package com.mars.moderation;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.repo.AppealRepository;
import com.mars.moderation.repo.ModerationActionRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 申诉服务（BR-8 / OP-17；契约 §4.9 API-ADM-12/13）。
 *
 * <p><b>申诉时限取后台配置</b>：{@code deadlineAt = actionTime + sys_config.appeal_days}
 * （{@code appeal.window_days} 的存储键），<b>不硬编码 7 天</b> —— 运营改配置即刻改变时限。
 * <b>一条处置仅可申诉一次</b>（{@code action_id} 唯一）；<b>申诉期间处置不解除</b>，
 * 仅当申诉被采纳（{@code ACCEPT}）才解除原处置（{@code dispositionEffective=false}）。
 */
public final class AppealService {

    private final AppealRepository appeals;
    private final ModerationActionRepository actions;
    private final PlatformConfig platformConfig;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator ids;
    private final AuditRecorder audit;
    private final Clock clock;

    public AppealService(AppealRepository appeals,
                         ModerationActionRepository actions,
                         PlatformConfig platformConfig,
                         UnitOfWork unitOfWork,
                         OrderNoGenerator ids,
                         AuditRecorder audit,
                         Clock clock) {
        this.appeals = appeals;
        this.actions = actions;
        this.platformConfig = platformConfig;
        this.unitOfWork = unitOfWork;
        this.ids = ids;
        this.audit = audit;
        this.clock = clock;
    }

    /** 提交申诉：时限取自后台配置；同一处置重复申诉 → {@code 40900}。 */
    public Appeal file(String actionId, long appellantId, String content) {
        if (actionId == null || actionId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "处置 ID 必填", "action_id", "required");
        }
        return unitOfWork.execute(() -> {
            ModerationAction action = actions.findById(actionId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.RESOURCE_GONE, "处置记录不存在", "action_id", "not_found"));
            if (appeals.findByActionId(actionId).isPresent()) {
                throw new BizException(ErrorCode.CONFLICT, "该处置已申诉", "action_id", "duplicate");
            }
            LocalDateTime deadline = action.actionTime().plusDays(platformConfig.appealDays());
            Appeal appeal = new Appeal(ids.next("APL"), actionId, appellantId, action.actionType(),
                    action.reasonCode(), content == null ? "" : content,
                    action.actionTime(), deadline, Appeal.AppealStatus.PENDING, true,
                    null, null, now(), null);
            appeals.save(appeal);
            return appeal;
        });
    }

    /** 申诉队列（API-ADM-12）：按创建时间升序；{@code status} 可空取全部。 */
    public List<Appeal> queue(Appeal.AppealStatus status) {
        return appeals.listByStatus(status);
    }

    /**
     * 处理申诉（API-ADM-13）：{@code ACCEPT} → 采纳并解除原处置；否则驳回并维持处置。
     * 写一条 {@code APPEAL_ACCEPT} / {@code APPEAL_REJECT} 审计（{@code evidence_id} 指向原处置）。
     */
    public Appeal handle(String appealId, String decision, String remark,
                         long operatorId, String operatorName, String operatorRole, String ip) {
        if (decision == null || decision.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "处理结论必填", "decision", "required");
        }
        return unitOfWork.execute(() -> {
            Appeal appeal = appeals.findById(appealId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.RESOURCE_GONE, "申诉不存在", "appeal_id", "not_found"));
            if (appeal.status() != Appeal.AppealStatus.PENDING) {
                throw new BizException(ErrorCode.CONFLICT, "申诉已处理", "appeal_id", "handled");
            }
            boolean accepted = "ACCEPT".equalsIgnoreCase(decision.trim());
            Appeal handled = appeal.handle(
                    accepted ? Appeal.AppealStatus.ACCEPTED : Appeal.AppealStatus.REJECTED,
                    operatorId, remark, now());
            appeals.save(handled);
            if (accepted) {
                actions.findById(appeal.actionId()).ifPresent(action -> actions.save(action.lift()));
            }

            audit.record(operatorId, operatorName, operatorRole,
                    accepted ? AuditRecorder.ACTION_APPEAL_ACCEPT : AuditRecorder.ACTION_APPEAL_REJECT,
                    "APPEAL", appeal.appealId(), "申诉 " + appeal.appealId(),
                    appeal.reasonCode(), remark, appeal.actionId(), ip);
            return handled;
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
