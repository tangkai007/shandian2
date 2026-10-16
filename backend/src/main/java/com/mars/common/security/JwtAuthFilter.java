package com.mars.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mars.common.ApiResponse;
import com.mars.common.ErrorCode;
import com.mars.common.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * JWT 鉴权过滤器（契约 §2.3 · AC-10）。
 *
 * <p>规则：带合法 Bearer 令牌 → 写入 {@link AuthContext}；令牌存在但非法/过期 →
 * <b>直接 401（{@code 40100}）</b>；无令牌 → 放行，由端点注解（{@link RequireRoles}）
 * 决定是否需要登录（匿名可看直播，但发言/打赏等前置门槛另行校验）。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BEARER = "Bearer ";

    private final JwtTokenService tokenService;
    private final Clock clock;

    public JwtAuthFilter(JwtTokenService tokenService, Clock clock) {
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            var claims = tokenService.verify(token, clock.instant());
            if (claims.isEmpty()) {
                reject(response);
                return;
            }
            AuthContext.set(claims.get());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.UNAUTHENTICATED.httpStatus());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.UNAUTHENTICATED, null, TraceContext.get());
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
