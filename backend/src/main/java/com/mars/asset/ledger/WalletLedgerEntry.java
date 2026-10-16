package com.mars.asset.ledger;

import java.util.Objects;

/**
 * 虚拟币流水（表 {@code wallet_ledger}）。
 *
 * <p>{@code bizNo} 全局唯一 —— 幂等的落点（S1a §6.3 / 契约 §2.6）。
 *
 * @param bizType      RECHARGE / GIFT / REFUND / ADJUST
 * @param bizNo        业务唯一号（充值单号 / 赠送单号 ...）
 * @param direction    IN / OUT
 * @param amount       变动币数（正数）
 * @param balanceAfter 变动后余额（用于「余额 ↔ 流水」一致性核对）
 * @param channel      渠道（充值/IAP 时非空）
 */
public record WalletLedgerEntry(
        long userId,
        String bizType,
        String bizNo,
        LedgerDirection direction,
        long amount,
        long balanceAfter,
        String channel) {

    public WalletLedgerEntry {
        Objects.requireNonNull(bizType, "bizType");
        Objects.requireNonNull(bizNo, "bizNo");
        Objects.requireNonNull(direction, "direction");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static WalletLedgerEntry of(long userId, String bizType, String bizNo,
                                       LedgerDirection direction, long amount, long balanceAfter) {
        return new WalletLedgerEntry(userId, bizType, bizNo, direction, amount, balanceAfter, null);
    }
}
