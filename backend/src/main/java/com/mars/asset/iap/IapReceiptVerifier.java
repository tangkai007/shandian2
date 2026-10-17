package com.mars.asset.iap;

/**
 * App Store 回执校验适配端口（契约 §4.6 API-ASSET-08 · OP-11）。
 *
 * <p>真实实现需：JWS 验签 → 商品 ID 与档位比对 → 调 <b>App Store Server API</b> 查询交易状态
 * （{@code Environment: Sandbox/Production}）。本期装配 {@link PlaceholderIapReceiptVerifier}
 * （占位，见「已知限制」）。校验失败一律 {@code 40345}。
 */
public interface IapReceiptVerifier {

    /**
     * 校验回执。
     *
     * @return 确认的环境 {@code SANDBOX} / {@code PROD}
     * @throws com.mars.common.BizException {@code 40345} 校验失败
     */
    String verify(String productId, String receipt);
}
