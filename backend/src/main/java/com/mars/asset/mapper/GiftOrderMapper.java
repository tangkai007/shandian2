package com.mars.asset.mapper;

import com.mars.asset.gift.GiftOrder;
import com.mars.asset.repo.GiftOrderRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 礼物订单 Mapper（表 {@code gift_order}）。
 *
 * <p>{@code idempotency_key} 唯一索引：重复插入由数据库拒绝 → 幂等重放不会重复扣费。
 */
@Mapper
public interface GiftOrderMapper extends GiftOrderRepository {

    @Override
    @Select("SELECT order_no AS orderNo, idempotency_key AS idempotencyKey, session_id AS sessionId, "
            + "sender_id AS senderId, anchor_id AS anchorId, gift_id AS giftId, count, "
            + "total_coin AS totalCoin, status FROM gift_order WHERE idempotency_key = #{idempotencyKey}")
    Optional<GiftOrder> findByIdempotencyKey(String idempotencyKey);

    @Insert("INSERT INTO gift_order (order_no, idempotency_key, session_id, sender_id, anchor_id, gift_id, "
            + "count, total_coin, status) VALUES (#{orderNo}, #{idempotencyKey}, #{sessionId}, #{senderId}, "
            + "#{anchorId}, #{giftId}, #{count}, #{totalCoin}, #{status})")
    int insert(GiftOrder order);

    @Override
    default void save(GiftOrder order) {
        insert(order);
    }

    @Override
    @Select("SELECT order_no AS orderNo, idempotency_key AS idempotencyKey, session_id AS sessionId, "
            + "sender_id AS senderId, anchor_id AS anchorId, gift_id AS giftId, count, "
            + "total_coin AS totalCoin, status FROM gift_order WHERE sender_id = #{senderId} ORDER BY id")
    List<GiftOrder> findBySender(long senderId);

    /** 供后台/对账按场次或主播检索（非本期前端路径）。 */
    @Select("SELECT COUNT(*) FROM gift_order WHERE session_id = #{sessionId}")
    long countBySession(@Param("sessionId") long sessionId);
}
