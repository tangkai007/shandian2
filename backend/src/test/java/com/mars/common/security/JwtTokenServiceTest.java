package com.mars.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 访问令牌签发/校验（契约 §2.3 · AC-10）。
 *
 * <p>装配层的 JUnit 5 切片：不加载 Spring 上下文（无需 MySQL/Redis），可独立运行。
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret";
    private final JwtTokenService service = new JwtTokenService(SECRET);
    private final Instant now = Instant.parse("2026-10-16T12:00:00Z");

    @Test
    @DisplayName("签发后可用同一密钥验签，并还原 userId 与角色")
    void issueThenVerify() {
        String token = service.issue(12345L, Set.of("VIEWER", "ANCHOR"), now);
        JwtClaims claims = service.verify(token, now.plusSeconds(60)).orElseThrow();

        assertEquals(12345L, claims.userId());
        assertTrue(claims.roles().contains("VIEWER"));
        assertTrue(claims.roles().contains("ANCHOR"));
        assertTrue(claims.hasAnyRole("ANCHOR"));
        assertFalse(claims.hasAnyRole("FINANCE"));
    }

    @Test
    @DisplayName("过期令牌验签失败（access 2h）")
    void expiredTokenRejected() {
        String token = service.issue(1L, Set.of("VIEWER"), now);
        assertTrue(service.verify(token, now.plus(JwtTokenService.ACCESS_TTL).plusSeconds(1)).isEmpty());
        assertTrue(service.verify(token, now.plus(JwtTokenService.ACCESS_TTL).minusSeconds(1)).isPresent());
    }

    @Test
    @DisplayName("篡改载荷或错密钥一律验签失败，不泄露原因")
    void tamperedTokenRejected() {
        String token = service.issue(1L, Set.of("VIEWER"), now);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".AAAA" + parts[2].substring(4);
        assertTrue(service.verify(tampered, now).isEmpty());
        assertTrue(service.verify(token + "x", now).isEmpty());
        assertTrue(service.verify("not-a-jwt", now).isEmpty());
        assertTrue(service.verify(null, now).isEmpty());
        assertTrue(service.verify("", now).isEmpty());
        assertTrue(new JwtTokenService("other-secret").verify(token, now).isEmpty());
    }
}
