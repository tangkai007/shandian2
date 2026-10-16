package com.mars.asset.recharge;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 充值订单（表 {@code recharge_order}）。
 *
 * @param channelFeeRate <b>下单时快照</b>的渠道费率（S1a §3.4.4：「历史账单永不受后续配置变更影响」）
 * @param status         {@code CREATED} / {@code PAID} / {@code FAILED} / {@code CLOSED}
 */
public record RechargeOrder(
        String orderNo,
        long userId,
        long amountFen,
        long coinAmount,
        String payChannel,
        BigDecimal channelFeeRate,
        String status) {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_PAID = "PAID";

    public RechargeOrder {
        Objects.requireNonNull(orderNo, "orderNo");
        Objects.requireNonNull(payChannel, "payChannel");
        Objects.requireNonNull(channelFeeRate, "channelFeeRate");
        Objects.requireNonNull(status, "status");
        if (amountFen <= 0L) {
            throw new IllegalArgumentException("amountFen must be positive");
        }
        if (coinAmount <= 0L) {
            throw new IllegalArgumentException("coinAmount must be positive");
        }
    }

    public RechargeOrder paid() {
        return new RechargeOrder(orderNo, userId, amountFen, coinAmount, payChannel, channelFeeRate, STATUS_PAID);
    }
}
