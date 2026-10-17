package com.mars.moderation;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.repo.ModerationActionRepository;
import com.mars.moderation.repo.ModerationEvidenceRepository;
import com.mars.moderation.repo.ModerationTaskRepository;
import com.mars.moderation.repo.RoomLookup;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 处置服务（契约 §4.9 API-ADM-05/08/09 · AC-7）。
 *
 * <p>口径（S1b D-ADM-002 / S2a §4.9 / mock.ts）：
 * <ul>
 *   <li>备注不足 <b>5 字</b> → {@code 40001}（{@code field=remark}）—— <b>对所有动作</b>，
 *       含 {@code MARK_OK}；</li>
 *   <li>原因码：处置类动作（{@code WARN/CUT/BAN/REPORT}）必填，缺失 → {@code 40001}
 *       （{@code field=reason_code}）；</li>
 *   <li>{@code MARK_OK}（标记无风险）只关单，<b>不写处置记录 / 证据</b>（不产生处置留痕）；
 *       但仍写一条后台审计（{@code action=MARK_OK}）；</li>
 *   <li>处置写入 {@code moderation_action} + 证据 {@code moderation_evidence}（<b>不可物理删除</b>）；</li>
 *   <li>{@code appealDeadlineAt = actionTime + sys_config.appeal_days}（<b>取后台配置，不硬编码 7 天</b>）。</li>
 * </ul>
 */
public final class ModerationActionService {

    /** 备注下限（D-ADM-002）。 */
    public static final int REMARK_MIN_LENGTH = 5;

    private final ModerationTaskRepository tasks;
    private final ModerationActionRepository actions;
    private final ModerationEvidenceRepository evidence;
    private final RoomLookup rooms;
    private final PlatformConfig platformConfig;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator ids;
    private final AuditRecorder audit;
    private final Clock clock;

    public ModerationActionService(ModerationTaskRepository tasks,
                                   ModerationActionRepository actions,
                                   ModerationEvidenceRepository evidence,
                                   RoomLookup rooms,
                                   PlatformConfig platformConfig,
                                   UnitOfWork unitOfWork,
                                   OrderNoGenerator ids,
                                   AuditRecorder audit,
                                   Clock clock) {
        this.tasks = tasks;
        this.actions = actions;
        this.evidence = evidence;
        this.rooms = rooms;
        this.platformConfig = platformConfig;
        this.unitOfWork = unitOfWork;
        this.ids = ids;
        this.audit = audit;
        this.clock = clock;
    }

    /** 提交处置（API-ADM-08）。 */
    public DispositionResult dispose(DispositionCommand cmd, String operatorRole, String ip) {
        validate(cmd);
        return unitOfWork.execute(() -> {
            ModerationTask task = findTaskOrNull(cmd.taskId());
            int appealDays = platformConfig.appealDays();
            LocalDateTime actionTime = now();
            String actionId = ids.next("MA");
            String targetLabel = task == null ? ("任务 " + cmd.taskId()) : task.targetLabel();
            Long roomId = task != null && task.roomId() != null ? task.roomId() : cmd.roomId();
            Long targetUserId = cmd.targetUserId() != null ? cmd.targetUserId()
                    : (task == null ? null : task.anchorId());

            boolean disposition = cmd.actionType().disposition();
            TaskStatus status = null;

            if (disposition) {
                String evidenceId = ids.next("ME");
                evidence.save(new ModerationEvidence(evidenceId, actionId, actionTime,
                        task == null ? "" : task.ruleId(), task == null ? 0 : task.confidence(),
                        cmd.operatorName(), cmd.evidenceUrls()));
                actions.save(new ModerationAction(actionId, cmd.taskId(), roomId, cmd.sessionId(),
                        targetUserId, cmd.actionType(), cmd.reasonCode(), cmd.remark().trim(),
                        cmd.operatorId(), cmd.operatorName(), String.join(",", cmd.notifyChannels()),
                        evidenceId, actionTime, actionTime.plusDays(appealDays), true));
                if (task != null) {
                    tasks.save(task.done());
                    status = TaskStatus.DONE;
                }
            } else if (task != null) {
                // MARK_OK：仅关单，不写处置记录 / 证据（不产生处置留痕）。
                tasks.save(task.done());
                status = TaskStatus.DONE;
            }

            // 审计动作名 = 动作类型名（WARN/CUT/BAN/REPORT/MARK_OK）；evidence_id 与 mock 一致取 actionId。
            audit.record(cmd.operatorId(), cmd.operatorName(), operatorRole,
                    cmd.actionType().name(), "ROOM",
                    roomId == null ? cmd.taskId() : String.valueOf(roomId), targetLabel,
                    disposition ? cmd.reasonCode() : null, cmd.remark().trim(), actionId, ip);

            return new DispositionResult(actionId, appealDays, disposition, status);
        });
    }

    /**
     * 强制关播（API-ADM-05，高危不可逆）：写一条 {@code CUT} 处置留痕 + 后台审计。
     *
     * <p>必填原因码（缺失 → {@code 40001 field=reason_code}）；备注可选（与 S2a mock 一致）。
     * 返回新建的处置 ID 与房间新状态（{@code INTERRUPTED}）。
     */
    public ForceStopResult forceStop(long roomId, Long sessionId, String reasonCode, String remark,
                                     long operatorId, String operatorName, String operatorRole,
                                     String ip) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原因码必填", "reason_code", "required");
        }
        return unitOfWork.execute(() -> {
            RoomBrief room = rooms.findById(roomId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.RESOURCE_GONE, "房间不存在或已结束", "room_id", "not_found"));
            LocalDateTime actionTime = now();
            int appealDays = platformConfig.appealDays();
            String actionId = ids.next("MA");
            String evidenceId = ids.next("ME");

            evidence.save(new ModerationEvidence(evidenceId, actionId, actionTime, "", 0,
                    operatorName, java.util.List.of()));
            actions.save(new ModerationAction(actionId, null, roomId, sessionId, room.anchorId(),
                    ModerationActionType.CUT, reasonCode, remark == null ? "" : remark,
                    operatorId, operatorName, "INBOX", evidenceId, actionTime,
                    actionTime.plusDays(appealDays), true));

            audit.record(operatorId, operatorName, operatorRole, AuditRecorder.ACTION_FORCE_STOP,
                    "ROOM", String.valueOf(roomId), room.targetLabel(), reasonCode, remark, actionId, ip);
            return new ForceStopResult(actionId, "INTERRUPTED");
        });
    }

    /** 强制关播结果（API-ADM-05 响应，S2a mock：{@code {action_id, status}}）。 */
    public record ForceStopResult(String actionId, String status) {
    }

    /** 处置详情（API-ADM-09 证据查询用）。 */
    public ModerationAction find(String actionId) {
        return actions.findById(actionId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_GONE, "处置记录不存在", "action_id", "not_found"));
    }

    /** 证据 + 处置（API-ADM-09）。 */
    public ModerationEvidence evidenceOf(String actionId) {
        return evidence.findByActionId(actionId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_GONE, "证据不存在", "action_id", "not_found"));
    }

    private void validate(DispositionCommand cmd) {
        if (cmd == null || cmd.actionType() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "处置动作必填", "action", "required");
        }
        if (cmd.actionType().requiresReasonCode()
                && (cmd.reasonCode() == null || cmd.reasonCode().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原因码必填", "reason_code", "required");
        }
        if (cmd.remark() == null || cmd.remark().trim().length() < REMARK_MIN_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "备注不少于 " + REMARK_MIN_LENGTH + " 字", "remark", "remark_too_short");
        }
    }

    private ModerationTask findTaskOrNull(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        ModerationTask task = tasks.findById(taskId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_GONE, "审核任务不存在", "task_id", "not_found"));
        if (task.status() == TaskStatus.DONE) {
            throw new BizException(ErrorCode.CONFLICT, "任务已处置", "task_id", "done");
        }
        return task;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
