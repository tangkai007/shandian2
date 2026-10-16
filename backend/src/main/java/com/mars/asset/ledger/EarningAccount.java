package com.mars.asset.ledger;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;

/**
 * 主播收益账户（表 {@code earning_account}：{@code total_earned / available / withdrawing / version}）。
 *
 * <p>口径（S1a v0.4 §3.4-3 / §3.4.8）：
 * <ul>
 *   <li>打赏先计入 {@code totalEarned}，<b>月结生成账单后才转入 {@code available}</b>，因此打赏不即时可提现；</li>
 *   <li>{@code withdrawing} 为已发起提现、尚未打款的金额；</li>
 *   <li>平台 50% 部分不进入主播可提现余额。</li>
 * </ul>
 * DB 列名 → API-DTO 名映射见契约 §5.3.1（{@code totalEarned→total_earned_amount} 等）。
 */
public record EarningAccount(long anchorId, long totalEarned, long available, long withdrawing, long version) {

    public static EarningAccount empty(long anchorId) {
        return new EarningAccount(anchorId, 0L, 0L, 0L, 0L);
    }

    /** 礼物打赏累加（进 {@code total_earned}，不等同可提现）。 */
    public EarningAccount accrueTip(long coin) {
        requirePositive(coin);
        return new EarningAccount(anchorId, totalEarned + coin, available, withdrawing, version + 1);
    }

    /** 冲正/撤回（负向，不删原记录，S1a §3.4.7）。 */
    public EarningAccount reverseTip(long coin) {
        requirePositive(coin);
        return new EarningAccount(anchorId, totalEarned - coin, available, withdrawing, version + 1);
    }

    /** 月结转：账单 {@code anchorNet} 转入可提现余额。 */
    public EarningAccount settle(long anchorNetFen) {
        if (anchorNetFen < 0L) {
            throw new IllegalArgumentException("anchorNetFen must be non-negative");
        }
        return new EarningAccount(anchorId, totalEarned, available + anchorNetFen, withdrawing, version + 1);
    }

    /** 提现锁定：可提现 → 提现中。 */
    public EarningAccount lockWithdraw(long fen) {
        requirePositive(fen);
        if (available < fen) {
            throw new BizException(ErrorCode.CONFLICT, "可提现余额不足", "available", "insufficient");
        }
        return new EarningAccount(anchorId, totalEarned, available - fen, withdrawing + fen, version + 1);
    }

    /** 提现打款完成：提现中扣减。 */
    public EarningAccount completeWithdraw(long fen) {
        requirePositive(fen);
        if (withdrawing < fen) {
            throw new BizException(ErrorCode.CONFLICT, "提现中金额不足", "withdrawing", "insufficient");
        }
        return new EarningAccount(anchorId, totalEarned, available, withdrawing - fen, version + 1);
    }

    /** 提现驳回：提现中退回可提现。 */
    public EarningAccount unlockWithdraw(long fen) {
        requirePositive(fen);
        return new EarningAccount(anchorId, totalEarned, available + fen, withdrawing - fen, version + 1);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
