package com.mars.earning;

import java.util.Objects;

/**
 * 提现单（表 {@code withdrawal}）。
 *
 * <p>字段与契约 §5.3.1 B 域对应：{@code amountFen} → API {@code pre_tax_amount}；
 * {@code taxFen} → API {@code tax_withheld}（仅 {@code WITHDRAW} 模式生效）；
 * {@code netAmountFen} → API {@code net_amount}。
 */
public record WithdrawalOrder(
        String orderNo,
        long anchorId,
        long amountFen,
        long taxFen,
        long netAmountFen,
        TaxMode taxMode,
        String status,
        String rejectReason) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_REJECTED = "REJECTED";

    public WithdrawalOrder {
        Objects.requireNonNull(orderNo, "orderNo");
        Objects.requireNonNull(taxMode, "taxMode");
        Objects.requireNonNull(status, "status");
    }

    public WithdrawalOrder withStatus(String next, String reason) {
        return new WithdrawalOrder(orderNo, anchorId, amountFen, taxFen, netAmountFen, taxMode, next, reason);
    }
}
