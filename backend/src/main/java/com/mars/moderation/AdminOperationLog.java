package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 后台操作审计（契约 §4.9 API-ADM-29，保留 ≥1 年）。
 *
 * <p>契约措辞为「AOP 切面」写 {@code admin_operation_log}；本实现以<b>显式 {@code AuditRecorder}</b>
 * 在各后台写端点调用（同库事务内），避免引入 AOP 依赖 —— 偏差已登记于实现说明。
 */
public record AdminOperationLog(
        String logId,
        long operatorId,
        String operatorName,
        String operatorRole,
        String action,
        String targetType,
        String targetId,
        String targetLabel,
        String reasonCode,
        String remark,
        String evidenceId,
        String beforeJson,
        String afterJson,
        String ip,
        LocalDateTime createdAt) {
}
