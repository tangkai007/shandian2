package com.mars.asset.ledger;

import java.util.Objects;

/**
 * 主播收益流水（表 {@code earning_ledger}）。
 *
 * @param bizType GIFT / SETTLEMENT / WITHDRAW / ADJUST
 * @param bizNo   业务唯一号（与钱包流水同源的单号）
 */
public record EarningLedgerEntry(
        long anchorId,
        String bizType,
        String bizNo,
        LedgerDirection direction,
        long amount,
        long balanceAfter) {

    public EarningLedgerEntry {
        Objects.requireNonNull(bizType, "bizType");
        Objects.requireNonNull(bizNo, "bizNo");
        Objects.requireNonNull(direction, "direction");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
