package com.mars.common.security;

import java.time.Instant;
import java.util.Set;

/**
 * 已验签的访问令牌声明。
 *
 * @param userId    用户 ID（{@code sub}）
 * @param roles     角色集合（{@code VIEWER} / {@code ANCHOR} / {@code OPS} / {@code AUDITOR} / {@code FINANCE}）
 * @param issuedAt  签发时间
 * @param expiresAt 过期时间（access 2h，契约 §2.3）
 */
public record JwtClaims(long userId, Set<String> roles, Instant issuedAt, Instant expiresAt) {

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean hasAnyRole(String... expected) {
        for (String role : expected) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
