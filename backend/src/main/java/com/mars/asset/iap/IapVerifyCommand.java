package com.mars.asset.iap;

import java.math.BigDecimal;

/**
 * IAP 校验入参 —— 仅承载<b>已通过验签与商品/环境校验</b>的交易事实。
 *
 * <p>验签、环境判定（Sandbox/Prod）、调 App Store Server API 查询状态由适配层完成；
 * 本记录由适配层在验签成功后构造，服务端业务侧据此加币。
 */
public record IapVerifyCommand(
        String transactionId,
        long userId,
        String productId,
        long amountFen,
        long coinAmount,
        BigDecimal channelFeeRate,
        String env) {
}
