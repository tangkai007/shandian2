package com.mars.asset.gift;

/**
 * 赠送结果（成功与幂等重放共用）。
 *
 * @param orderNo          礼物单号（重放时与首次相同）
 * @param balanceAfter     送礼人扣费后余额
 * @param totalCoin        本次消费币数
 * @param anchorTotalEarned 主播累计收益（{@code total_earned}）快照
 * @param replay           {@code true} 表示幂等重放（未再次扣费）
 */
public record GiftSendResult(
        String orderNo,
        long balanceAfter,
        long totalCoin,
        long anchorTotalEarned,
        boolean replay) {

    public static GiftSendResult of(String orderNo, long balanceAfter, long totalCoin, long anchorTotalEarned) {
        return new GiftSendResult(orderNo, balanceAfter, totalCoin, anchorTotalEarned, false);
    }

    public static GiftSendResult replay(String orderNo, long balanceAfter, long totalCoin, long anchorTotalEarned) {
        return new GiftSendResult(orderNo, balanceAfter, totalCoin, anchorTotalEarned, true);
    }
}
