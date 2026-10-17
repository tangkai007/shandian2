package com.mars.admin.model;

import java.time.LocalDateTime;

/**
 * 运营位 / 轮播（契约 §4.9 API-ADM-19 · 前端 {@code BannerRow}）。
 *
 * @param targetType {@code ROOM / CATEGORY / URL}
 */
public record Banner(
        long id,
        String title,
        String targetType,
        String targetValue,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean enabled,
        int sort) {
}
