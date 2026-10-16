package com.mars.asset.iap;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * IAP 交易（表 {@code iap_transaction}，仅 iOS · OP-11）。
 *
 * @param channelFeeRate Apple 抽成费率<b>下单快照</b>（小企业计划变更不影响历史账单）
 * @param status         {@code VERIFIED} / {@code REFUNDED} / {@code REVOKED}
 */
public record IapTransaction(
        String transactionId,
        long userId,
        String productId,
        long amountFen,
        long coinAmount,
        BigDecimal channelFeeRate,
        String env,
        String status) {

    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_REVOKED = "REVOKED";

    public IapTransaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(channelFeeRate, "channelFeeRate");
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(status, "status");
    }

    public IapTransaction withStatus(String nextStatus) {
        return new IapTransaction(transactionId, userId, productId, amountFen, coinAmount,
                channelFeeRate, env, nextStatus);
    }
}
