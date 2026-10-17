package com.mars.moderation;

/**
 * 平台口径配置项（契约 §4.9 API-ADM-20 · S1b §11.3「必须配置化下发的口径」）。
 *
 * <p>{@code valueType} 取值：{@code fen} / {@code int} / {@code percent} / {@code bool} /
 * {@code days} / {@code list}；{@code value} 一律以字符串存储，读侧按类型解析。
 */
public record SysConfigEntry(
        String key,
        String label,
        String valueType,
        String value,
        String note) {
}
