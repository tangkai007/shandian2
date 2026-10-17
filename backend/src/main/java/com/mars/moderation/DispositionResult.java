package com.mars.moderation;

/**
 * 处置结果（契约 §4.9 API-ADM-08 响应）。
 *
 * <p>{@code appealWindowDays} 随处置结果下发 —— 让审核员在不读取 {@code /admin/sys-config}
 * 的前提下看到本单的申诉时限（时限取自后台配置，非前端硬编码）。
 */
public record DispositionResult(
        String actionId,
        int appealWindowDays,
        boolean dispositionApplied,
        TaskStatus taskStatus) {
}
