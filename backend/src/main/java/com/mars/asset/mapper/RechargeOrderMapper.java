package com.mars.asset.mapper;

import com.mars.asset.recharge.RechargeOrder;
import com.mars.asset.repo.RechargeOrderRepository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 充值订单 Mapper（表 {@code recharge_order}）。
 *
 * <p>状态流转用条件更新表达，避免并发重复置位：{@code ... SET status='PAID' WHERE order_no=? AND status='CREATED'}。
 */
@Mapper
public interface RechargeOrderMapper extends RechargeOrderRepository {

    @Override
    @Select("SELECT order_no AS orderNo, user_id AS userId, amount_fen AS amountFen, "
            + "coin_amount AS coinAmount, pay_channel AS payChannel, channel_fee_rate AS channelFeeRate, "
            + "status FROM recharge_order WHERE order_no = #{orderNo}")
    Optional<RechargeOrder> findByOrderNo(String orderNo);

    @Update("INSERT INTO recharge_order (order_no, user_id, amount_fen, coin_amount, pay_channel, "
            + "channel_fee_rate, status) VALUES (#{orderNo}, #{userId}, #{amountFen}, #{coinAmount}, "
            + "#{payChannel}, #{channelFeeRate}, #{status}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status)")
    int upsert(RechargeOrder order);

    @Override
    default void save(RechargeOrder order) {
        upsert(order);
    }

    /** 条件置位：仅 CREATED → PAID 才生效，返回影响行数（0 表示已被其它请求处理）。 */
    @Update("UPDATE recharge_order SET status = 'PAID', paid_at = NOW(3) "
            + "WHERE order_no = #{orderNo} AND status = 'CREATED'")
    int markPaid(@Param("orderNo") String orderNo);
}
