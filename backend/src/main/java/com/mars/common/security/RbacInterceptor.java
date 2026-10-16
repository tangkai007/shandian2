package com.mars.common.security;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RBAC 拦截器：读取 {@link RequireRoles} 并校验登录态与角色（契约 §2.3 · AC-10）。
 *
 * <p>权限不足统一返回 {@code 40300}；未登录返回 {@code 40100}，两者不混用。
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRoles methodLevel = method.getMethodAnnotation(RequireRoles.class);
        RequireRoles required = methodLevel != null
                ? methodLevel
                : method.getBeanType().getAnnotation(RequireRoles.class);
        if (required == null) {
            return true;
        }
        if (!AuthContext.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "未登录或令牌失效");
        }
        if (required.value().length > 0 && !AuthContext.current()
                .map(claims -> claims.hasAnyRole(required.value())).orElse(false)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问该资源");
        }
        return true;
    }
}
