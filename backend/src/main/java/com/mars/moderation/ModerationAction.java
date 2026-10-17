package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 处置记录（契约 §4.9 API-ADM-08 · <b>不可物理删除</b>，AC-7）。
 *
 * <p>{@code appealDeadlineAt = actionTime + 申诉时限}（时限取自 {@code sys_config.appeal_days}，
 * <b>不硬编码 7 天</b>）；{@code dispositionEffective} 在申诉期间恒为 {@code true}（BR-8）。
 */
public record ModerationAction(
        String actionId,
        String taskId,
        Long roomId,
        Long sessionId,
        Long targetUserId,
        ModerationActionType actionType,
        String reasonCode,
        String remark,
        long operatorId,
        String operatorName,
        String notifyChannels,
        String evidenceId,
        LocalDateTime actionTime,
        LocalDateTime appealDeadlineAt,
        boolean dispositionEffective) {

    /** 申诉被采纳 → 处置解除（{@code dispositionEffective = false}）。 */
    public ModerationAction lift() {
        return new ModerationAction(actionId, taskId, roomId, sessionId, targetUserId, actionType,
                reasonCode, remark, operatorId, operatorName, notifyChannels, evidenceId,
                actionTime, appealDeadlineAt, false);
    }
}
