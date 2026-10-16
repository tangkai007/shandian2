package com.mars.asset.repo;

import com.mars.asset.recharge.RechargeOrder;

import java.util.Optional;

/** 充值订单仓储（{@code order_no} 唯一；回调按单号幂等）。 */
public interface RechargeOrderRepository {

    Optional<RechargeOrder> findByOrderNo(String orderNo);

    void save(RechargeOrder order);
}
