package com.mars.admin.service;

import com.mars.admin.model.RefreshToken;
import com.mars.admin.repo.RefreshTokenRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.JwtTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * 刷新令牌服务（契约 §4.1 API-AUTH-05/06）。
 *
 * <p><b>轮换</b>：refresh 一次性使用 —— 每次刷新返回新 access + 新 refresh，旧 refresh 立即吊销。
 * 只存 {@code SHA-256(refresh_token)}；失效 / 已吊销 / 过期一律 {@code 40100}。
 */
public final class RefreshTokenService {

    /** refresh 有效期（30 天）。 */
    public static final Duration REFRESH_TTL = Duration.ofDays(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final JwtTokenService jwt;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, JwtTokenService jwt, Clock clock) {
        this.tokens = tokens;
        this.jwt = jwt;
        this.clock = clock;
    }

    /** 签发 access + refresh（登录成功后调用）。 */
    public TokenPair issue(long userId, Set<String> roles) {
        return issue(userId, roles, null);
    }

    /** 签发（{@code previousHash} 非空时一并吊销旧 refresh，实现无断点轮换）。 */
    public TokenPair issue(long userId, Set<String> roles, String previousHash) {
        Instant now = clock.instant();
        if (previousHash != null) {
            tokens.revoke(previousHash);
        }
        String refresh = newRefreshToken();
        tokens.save(new RefreshToken(hash(refresh), userId, now.plus(REFRESH_TTL), false));
        String access = jwt.issue(userId, roles, now);
        return new TokenPair(access, refresh, JwtTokenService.ACCESS_TTL.toSeconds());
    }

    /**
     * 轮换：校验入参 refresh → 吊销旧票 → 签发新对。
     *
     * @param roleResolver 依 {@code userId} 重新解析角色（角色变更即时生效）
     */
    public TokenPair rotate(String refreshToken, RoleResolver roleResolver) {
        String hash = hash(refreshToken);
        RefreshToken stored = tokens.findByHash(hash).orElseThrow(() -> invalid());
        if (stored.revoked() || !stored.expiresAt().isAfter(clock.instant())) {
            throw invalid();
        }
        Set<String> roles = roleResolver.rolesOf(stored.userId());
        return issue(stored.userId(), roles, hash);
    }

    /** 注销：当前 refresh 吊销（幂等）。 */
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        tokens.revoke(hash(refreshToken));
    }

    /** 解析 refresh 所属用户（不轮换，仅用于审计 / 回显）。 */
    public Optional<Long> userIdOf(String refreshToken) {
        return tokens.findByHash(hash(refreshToken))
                .filter(token -> !token.revoked() && token.expiresAt().isAfter(clock.instant()))
                .map(RefreshToken::userId);
    }

    private static BizException invalid() {
        return new BizException(ErrorCode.UNAUTHENTICATED, "refresh 令牌失效", "refresh_token", "invalid");
    }

    private static String newRefreshToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** {@code SHA-256} 十六进制小写（表列 {@code CHAR(64)}）。 */
    public static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** 角色解析（刷新时按最新角色重签 access）。 */
    @FunctionalInterface
    public interface RoleResolver {
        Set<String> rolesOf(long userId);
    }

    /** 令牌对。 */
    public record TokenPair(String accessToken, String refreshToken, long expireIn) {
    }
}
