package com.mars.account.service;

/**
 * 第三方云实名核验端口（契约 §4.1 API-AUTH-10 · OP-5，供应商可切换）。
 *
 * <p>生产实现应调用阿里云 / 腾讯云实名接口；本期装配 {@code PlaceholderRealnameVerifier}
 * （无外呼，按「姓名非空 + 证件号长度」占位判定），已登记为「已知限制」。
 */
public interface RealnameVerifier {

    Result verify(String realName, String idCardNo);

    /**
     * @param passed    是否核验通过
     * @param channel   供应商标识（{@code ALIYUN} / {@code TENCENT} / {@code PLACEHOLDER}）
     * @param failReason 供应商返回码（如 {@code ID_MISMATCH}）；通过时为 {@code null}
     */
    record Result(boolean passed, String channel, String failReason) {
    }
}
