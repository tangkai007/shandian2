package com.mars.admin.model;

import java.time.Instant;

/**
 * 刷新令牌记录（契约 §4.1 API-AUTH-05/06）。
 *
 * <p>{@code tokenHash = SHA-256(refresh_token)} —— 只存哈希，明文不落库；轮换时旧令牌置 {@code revoked}。
 */
public record RefreshToken(String tokenHash, long userId, Instant expiresAt, boolean revoked) {
}
