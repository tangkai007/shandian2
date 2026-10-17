package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 处置申诉（BR-8 / OP-17；契约 §4.9 API-ADM-12/13）。
 *
 * <p><b>申诉时限</b>：{@code deadlineAt = actionTime + sys_config.appeal_days}（后台配置，
 * <b>不硬编码 7 天</b>）。<b>申诉期间处置不解除</b>：PENDING 时 {@code dispositionEffective=true}；
 * 仅当 {@code ACCEPTED} 才置 {@code false}（解除处置）。
 */
public record Appeal(
        String appealId,
        String actionId,
        long appellantId,
        ModerationActionType actionType,
        String reasonCode,
        String content,
        LocalDateTime actionTime,
        LocalDateTime deadlineAt,
        AppealStatus status,
        boolean dispositionEffective,
        Long handledBy,
        String handleRemark,
        LocalDateTime createdAt,
        LocalDateTime handledAt) {

    /** 申诉状态（前端 `PENDING/ACCEPTED/REJECTED`）。 */
    public enum AppealStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    /** 处理申诉：采纳 → 解除处置（{@code dispositionEffective=false}）；驳回 → 维持处置。 */
    public Appeal handle(AppealStatus decision, long operatorId, String remark, LocalDateTime at) {
        boolean stillEffective = decision != AppealStatus.ACCEPTED;
        return new Appeal(appealId, actionId, appellantId, actionType, reasonCode, content,
                actionTime, deadlineAt, decision, stillEffective, operatorId, remark, createdAt, at);
    }
}
