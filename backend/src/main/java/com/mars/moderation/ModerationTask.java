package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 审核任务（机审回调落库后的队列条目，契约 §4.10 API-CB-03/04 · AC-7）。
 *
 * <p>{@code taskId} 为对外可见主键（前端 `task_id`）；{@code eventId} 为回调事件幂等键。
 */
public record ModerationTask(
        String taskId,
        String eventId,
        String source,
        Long roomId,
        Long roomNo,
        Long anchorId,
        String anchorName,
        Long sessionId,
        RiskLevel riskLevel,
        AlertType alertType,
        String reasonSummary,
        String snapshotUrl,
        String ruleId,
        int confidence,
        TaskStatus status,
        Long claimedBy,
        String claimedByName,
        LocalDateTime createdAt) {

    /** 处置留痕的对外标签：{@code <主播>（房号 <room_no>）}（S2a §4.9 writeAudit 口径）。 */
    public String targetLabel() {
        String name = (anchorName == null || anchorName.isBlank()) ? "主播" : anchorName;
        String no = roomNo == null ? "-" : String.valueOf(roomNo);
        return name + "（房号 " + no + "）";
    }

    /** 认领（PENDING → CLAIMED）。 */
    public ModerationTask claimedBy(long operatorId, String operatorName) {
        return new ModerationTask(taskId, eventId, source, roomId, roomNo, anchorId, anchorName,
                sessionId, riskLevel, alertType, reasonSummary, snapshotUrl, ruleId, confidence,
                TaskStatus.CLAIMED, operatorId, operatorName, createdAt);
    }

    /** 关单（→ DONE）。 */
    public ModerationTask done() {
        return new ModerationTask(taskId, eventId, source, roomId, roomNo, anchorId, anchorName,
                sessionId, riskLevel, alertType, reasonSummary, snapshotUrl, ruleId, confidence,
                TaskStatus.DONE, claimedBy, claimedByName, createdAt);
    }
}
