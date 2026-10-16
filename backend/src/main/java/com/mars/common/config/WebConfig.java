package com.mars.common.config;

import com.mars.common.security.JwtTokenService;
import com.mars.common.security.RbacInterceptor;
import com.mars.common.web.RequireIdempotencyKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Web 装配：注册鉴权 / RBAC / 幂等拦截器与时钟（契约 §2.3 / §2.6）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;
    private final RequireIdempotencyKey.Interceptor idempotencyInterceptor;

    public WebConfig(RbacInterceptor rbacInterceptor,
                     RequireIdempotencyKey.Interceptor idempotencyInterceptor) {
        this.rbacInterceptor = rbacInterceptor;
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtTokenService jwtTokenService(@Value("${mars.security.jwt-secret}") String jwtSecret) {
        return new JwtTokenService(jwtSecret);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(rbacInterceptor).addPathPatterns("/api/**");
    }
}
