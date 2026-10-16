package com.mars.asset.ledger;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;

/**
 * 虚拟币钱包（对应表 {@code wallet_account}：{@code user_id, balance, version}）。
 *
 * <p>不可变值对象 + 乐观锁版本号：任何变更返回新实例并令 {@code version + 1}，便于
 * 以「读取 → 变更 → 版本校验落库」表达并发控制（S1a §6.3）。虚拟币<b>单向不可提现</b>（BR-4）。
 *
 * @param frozen 退款/撤销导致余额为负时置位，用于冻结打赏能力（S1a §3.4.7）
 */
public record WalletAccount(long userId, long balance, long version, boolean frozen) {

    public static WalletAccount empty(long userId) {
        return new WalletAccount(userId, 0L, 0L, false);
    }

    /** 扣减；余额不足直接拒绝（对应契约 §4.6 的 {@code 40001 field=balance}）。 */
    public WalletAccount debit(long coin) {
        requirePositive(coin);
        if (balance < coin) {
            throw new BizException(ErrorCode.PARAM_INVALID, "余额不足", "balance", "insufficient");
        }
        return new WalletAccount(userId, balance - coin, version + 1, frozen);
    }

    /** 加币（充值 / IAP 到账）。 */
    public WalletAccount credit(long coin) {
        requirePositive(coin);
        long next = balance + coin;
        return new WalletAccount(userId, next, version + 1, next < 0L || frozen);
    }

    /**
     * 退款/撤销冲减：允许余额转负，并置 {@code frozen}（不静默吞掉，S1a §3.4.7）。
     */
    public WalletAccount reverse(long coin) {
        requirePositive(coin);
        long next = balance - coin;
        return new WalletAccount(userId, next, version + 1, next < 0L || frozen);
    }

    public boolean canAfford(long coin) {
        return balance >= coin;
    }

    private static void requirePositive(long coin) {
        if (coin <= 0L) {
            throw new IllegalArgumentException("coin must be positive");
        }
    }
}
