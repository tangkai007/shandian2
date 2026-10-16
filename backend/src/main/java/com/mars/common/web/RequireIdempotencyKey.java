package com.mars.common.web;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明端点需要 {@code Idempotency-Key} 请求头（契约 §2.6：<b>资金类接口必填</b>）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireIdempotencyKey {

    /** 强制校验 {@code Idempotency-Key} 是否存在（缺失 → {@code 40001 field=Idempotency-Key}）。 */
    @Component
    class Interceptor implements HandlerInterceptor {

        public static final String HEADER = "Idempotency-Key";

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (handler instanceof HandlerMethod method
                    && method.getMethodAnnotation(RequireIdempotencyKey.class) != null) {
                String key = request.getHeader(HEADER);
                if (key == null || key.isBlank()) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "资金类接口缺少幂等键", "Idempotency-Key", "required");
                }
            }
            return true;
        }
    }
}
