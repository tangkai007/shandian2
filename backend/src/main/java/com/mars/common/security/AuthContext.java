package com.mars.common.security;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;

import java.util.Optional;
import java.util.Set;

/**
 * 当前请求的鉴权上下文（由 {@link JwtAuthFilter} 写入）。
 *
 * <p>服务端一律以本上下文判定归属与权限，<b>不信任前端传参</b>（S1a §九 安全 / AC-10）。
 */
public final class AuthContext {

    private static final ThreadLocal<JwtClaims> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(JwtClaims claims) {
        CURRENT.set(claims);
    }

    public static Optional<JwtClaims> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static boolean isAuthenticated() {
        return CURRENT.get() != null;
    }

    /** 未登录即抛 {@code 40100}（<b>不得</b>复用 {@code 40325}，见契约 §2.7 边界备注）。 */
    public static long requireUserId() {
        JwtClaims claims = CURRENT.get();
        if (claims == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "未登录或令牌失效");
        }
        return claims.userId();
    }

    public static Set<String> roles() {
        JwtClaims claims = CURRENT.get();
        return claims == null ? Set.of() : claims.roles();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
