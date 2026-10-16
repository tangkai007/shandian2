package com.mars.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明端点所需角色（RBAC，契约 §2.3 · AC-10）。
 *
 * <p>语义：必须<b>已登录</b>，且具备 {@link #value()} 中任一角色；否则
 * {@code 40100}（未登录）/ {@code 40300}（角色不足）。房管等房间级权限由服务层按归属再校验。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRoles {

    /** 允许的角色（任一满足即放行）。 */
    String[] value();
}
