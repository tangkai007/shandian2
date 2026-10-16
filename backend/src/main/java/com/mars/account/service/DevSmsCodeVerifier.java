package com.mars.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 开发/本地环境的短信验证码实现（<b>占位</b>）。
 *
 * <p>仅接受配置的固定验证码 {@code mars.auth.dev-code}，用于打通登录 → 令牌链路；
 * 真实短信通道与验证码存储由 {@code notify} 模块接入（下个增量）。
 * 该类仅装配在 {@code local} / {@code dev} / {@code test} / 默认 profile；生产 profile 下不加载。
 */
@Component
@Profile({"local", "dev", "test", "default"})
public class DevSmsCodeVerifier implements SmsCodeVerifier {

    private final String devCode;

    public DevSmsCodeVerifier(@Value("${mars.auth.dev-code:000000}") String devCode) {
        this.devCode = devCode;
    }

    @Override
    public boolean verify(String phone, String code) {
        return devCode.equals(code);
    }

    @Override
    public long send(String phone, String scene) {
        // 占位：不真正发短信，返回 60s 重发间隔（契约 §4.1 retry_after）
        return 60L;
    }
}
