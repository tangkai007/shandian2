package com.mars.asset.gift;

/**
 * 赠送礼物的入参（契约 §4.6 API-ASSET-02）。
 *
 * <p>{@code isMinor} / {@code realnameVerified} / {@code todayConsumedCoin} 由调用方的鉴权上下文与
 * 当日统计提供 —— 领域服务不自行查询用户态，便于单测与复用。
 *
 * @param idempotencyKey 幂等键（必填）
 */
public record GiftSendCommand(
        String idempotencyKey,
        long sessionId,
        long senderId,
        long anchorId,
        long giftId,
        int count,
        boolean isMinor,
        boolean realnameVerified,
        long todayConsumedCoin) {

    public GiftSendCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
