package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 机审回调事件（入参，契约 §4.10 API-CB-03/04）。
 *
 * <p>{@code eventId} 是三方事件 ID —— <b>幂等唯一键</b>；同一 {@code eventId} 重复投递只入队一次。
 */
public record ModerationAlertEvent(
        String eventId,
        String source,
        Long roomId,
        Long roomNo,
        Long anchorId,
        String anchorName,
        Long sessionId,
        AlertType alertType,
        String reasonSummary,
        String snapshotUrl,
        String ruleId,
        int confidence,
        LocalDateTime occurredAt) {

    /** 由置信度得到风险等级（分流口径唯一来源）。 */
    public RiskLevel riskLevel() {
        return RiskLevel.fromConfidence(confidence);
    }
}
