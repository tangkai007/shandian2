package com.mars.asset.gift;

import java.util.Objects;

/**
 * 礼物赠送单（表 {@code gift_order}）。
 *
 * @param idempotencyKey 幂等键（唯一约束 → 同键重放绝不重复扣费）
 * @param totalCoin      本次消费币数 = 礼物单价 × 数量
 * @param status         {@code NORMAL} / {@code REVERSED}（冲正不删原记录）
 */
public record GiftOrder(
        String orderNo,
        String idempotencyKey,
        long sessionId,
        long senderId,
        long anchorId,
        long giftId,
        int count,
        long totalCoin,
        String status) {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_REVERSED = "REVERSED";

    public GiftOrder {
        Objects.requireNonNull(orderNo, "orderNo");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(status, "status");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (totalCoin <= 0L) {
            throw new IllegalArgumentException("totalCoin must be positive");
        }
    }
}
