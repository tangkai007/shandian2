package com.mars.account.service;

/**
 * 短信验证码校验（契约 §4.1 API-AUTH-01/02）。
 *
 * <p>接入前须由 {@code notify} 模块提供真实实现（真实短信通道 + Redis 验证码 TTL 300s）。
 * 生产 Profile 下<b>必须</b>存在实现 bean，否则应用启动失败（fail-fast，避免占位实现被误用到线上）。
 */
public interface SmsCodeVerifier {

    /**
     * @return 验证码是否正确
     */
    boolean verify(String phone, String code);

    /** 发送验证码（返回可再次发送的间隔秒数）。 */
    long send(String phone, String scene);
}
