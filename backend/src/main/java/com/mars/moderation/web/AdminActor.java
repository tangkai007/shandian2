package com.mars.moderation.web;

import com.mars.common.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 后台操作者身份与来源 IP 的统一提取（写入 {@code admin_operation_log} 留痕）。
 *
 * <p>身份<b>一律取自服务端鉴权上下文</b>（{@link AuthContext}），不信任前端传参（AC-10）。
 *
 * <p><b>已知偏差</b>：{@code JwtClaims} 不含昵称声明，故 {@link #name()} 暂以用户 ID 字符串落痕；
 * 待「后台账号 / SSO 昵称」增量接入后替换（登记于实现说明）。
 */
final class AdminActor {

    /** 角色呈现优先级：多角色账号取最高权限者（审计日志按小写角色落库）。 */
    private static final List<String> ROLE_PRIORITY =
            List.of("AUDITOR", "OPS", "FINANCE", "ANCHOR", "VIEWER");

    private AdminActor() {
    }

    static long id() {
        return AuthContext.requireUserId();
    }

    /** 操作者展示名（{@code JwtClaims} 无昵称 → 回落到用户 ID）。 */
    static String name() {
        return String.valueOf(AuthContext.requireUserId());
    }

    /** 审计日志以<b>小写角色</b>落库（契约 D-ADM-000：{@code auditor/ops/finance}）。 */
    static String role() {
        Set<String> roles = AuthContext.roles();
        for (String candidate : ROLE_PRIORITY) {
            if (roles.contains(candidate)) {
                return candidate.toLowerCase(Locale.ROOT);
            }
        }
        return roles.isEmpty() ? "" : roles.iterator().next().toLowerCase(Locale.ROOT);
    }

    /** 来源 IP：优先 {@code X-Forwarded-For} 首段（网关），其次 {@code X-Real-IP}，最后直连地址。 */
    static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}
