package com.mars.asset.repo;

import com.mars.asset.gift.GiftOrder;

import java.util.List;
import java.util.Optional;

/**
 * 礼物订单仓储。
 *
 * <p>{@code idempotency_key} 唯一 —— 同键重放<b>不得重复扣费</b>（契约 §2.6）。
 */
public interface GiftOrderRepository {

    Optional<GiftOrder> findByIdempotencyKey(String idempotencyKey);

    void save(GiftOrder order);

    List<GiftOrder> findBySender(long senderId);
}
