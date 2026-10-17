package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.DispositionCommand;
import com.mars.moderation.DispositionResult;
import com.mars.moderation.ModerationAction;
import com.mars.moderation.ModerationActionService;
import com.mars.moderation.ModerationActionType;
import com.mars.moderation.ModerationEvidence;
import com.mars.moderation.ModerationQueueService;
import com.mars.moderation.ModerationReasonCodes;
import com.mars.moderation.ModerationTask;
import com.mars.moderation.PlatformConfig;
import com.mars.moderation.RiskLevel;
import com.mars.moderation.TaskStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台审核工作台（契约 §4.9 · AC-7 · D-ADM-001 / D-ADM-002）。
 *
 * <p>API-ADM-06 队列 / 07 认领 / 08 处置 / 09 证据 / 原因码字典。默认角色 {@code AUDITOR}；
 * 原因码字典审核与运营均可读（与 S2a mock 的角色守卫一致）。
 *
 * <p>字段名一律 <b>snake_case</b>（与后台前端 {@code contract.ts} 冻结一致）。
 */
@RestController
@RequestMapping("/api/v1/admin/moderation")
@RequireRoles({"AUDITOR"})
public class AdminModerationController {

    private final ModerationQueueService queueService;
    private final ModerationActionService actionService;
    private final PlatformConfig platformConfig;

    public AdminModerationController(ModerationQueueService queueService,
                                     ModerationActionService actionService,
                                     PlatformConfig platformConfig) {
        this.queueService = queueService;
        this.actionService = actionService;
        this.platformConfig = platformConfig;
    }

    /** API-ADM-06：审核队列（按风险等级高在前，含已认领 / 已处置）。 */
    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskRow>> tasks(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        List<TaskRow> rows = queueService.queue().stream().map(TaskRow::of).toList();
        return ApiResponse.ok(AdminPaging.of(rows, page, size));
    }

    /** API-ADM-07：认领任务（并发抢单失败 → {@code 40900}）。 */
    @PostMapping("/tasks/{taskId}/claim")
    public ApiResponse<TaskRow> claim(@PathVariable String taskId) {
        ModerationTask task = queueService.claim(taskId, AdminActor.id(), AdminActor.name());
        return ApiResponse.ok(TaskRow.of(task));
    }

    /** API-ADM-08：提交处置（备注不足 5 字 / 未选原因码 → {@code 40001}）。 */
    @PostMapping("/actions")
    public ApiResponse<DispositionAck> submitAction(@RequestBody DispositionRequest body,
                                                    HttpServletRequest request) {
        DispositionResult result = actionService.dispose(
                new DispositionCommand(
                        body.taskId(),
                        parseId(body.roomId()),
                        parseId(body.liveSessionId()),
                        null,
                        ModerationActionType.fromName(body.action()),
                        body.reasonCode(),
                        body.remark(),
                        body.evidenceUrls(),
                        body.notifyChannels(),
                        AdminActor.id(),
                        AdminActor.name()),
                AdminActor.role(),
                AdminActor.ip(request));
        return ApiResponse.ok(new DispositionAck(result.actionId(), result.appealWindowDays()));
    }

    /** API-ADM-09：处置证据（快照 / 规则 / 置信度 / 申诉时限），供 D-ADM-002 证据块回显。 */
    @GetMapping("/evidence/{actionId}")
    public ApiResponse<EvidenceRow> evidence(@PathVariable String actionId) {
        ModerationAction action = actionService.find(actionId);
        ModerationEvidence evidence = actionService.evidenceOf(actionId);
        return ApiResponse.ok(new EvidenceRow(
                action.actionId(),
                evidence.snapshotAt(),
                evidence.ruleId(),
                evidence.confidence(),
                evidence.operatorName(),
                platformConfig.appealDays(),
                evidence.evidenceUrls()));
    }

    /**
     * 原因码字典（后台字典，非前端硬编码）。审核员无权读取 {@code /admin/sys-config}，
     * 故申诉时限随字典一并下发（与 S2a mock 一致）。
     */
    @GetMapping("/reason-codes")
    @RequireRoles({"OPS", "AUDITOR"})
    public ApiResponse<ReasonCodes> reasonCodes() {
        return ApiResponse.ok(new ReasonCodes(ModerationReasonCodes.all(), platformConfig.appealDays()));
    }

    /** 宽松解析数字 ID：容忍空串与 {@code room_10241} 这类携带前缀的形态（mock 兼容）。 */
    static Long parseId(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }

    /** 审核队列行（S2a §4.9 API-ADM-06）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TaskRow(String taskId,
                          Long roomNo,
                          String anchorName,
                          RiskLevel riskLevel,
                          String reasonSummary,
                          LocalDateTime createdAt,
                          TaskStatus status) {

        static TaskRow of(ModerationTask task) {
            return new TaskRow(task.taskId(), task.roomNo(), task.anchorName(), task.riskLevel(),
                    task.reasonSummary(), task.createdAt(), task.status());
        }
    }

    /** 处置请求（API-ADM-08 + S1b D-ADM-002 处置区）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DispositionRequest(String taskId,
                                     String roomId,
                                     String liveSessionId,
                                     String action,
                                     String reasonCode,
                                     String remark,
                                     List<String> evidenceUrls,
                                     List<String> notifyChannels) {
    }

    /** 处置应答（{@code appeal_window_days} 取自后台配置，不硬编码）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DispositionAck(String actionId, int appealWindowDays) {
    }

    /** 证据块（API-ADM-09 + D-ADM-002）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EvidenceRow(String actionId,
                              LocalDateTime snapshotAt,
                              String ruleId,
                              int confidence,
                              String operatorName,
                              int appealWindowDays,
                              List<String> evidenceUrls) {
    }

    /** 原因码字典 + 申诉时限。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReasonCodes(List<ModerationReasonCodes.ReasonCode> list, int appealWindowDays) {
    }
}
