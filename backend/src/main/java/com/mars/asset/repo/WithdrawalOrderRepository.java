package com.mars.asset.repo;

import com.mars.earning.WithdrawalOrder;

import java.util.List;
import java.util.Optional;

/** 提现单仓储（表 {@code withdrawal}，{@code order_no} 唯一）。 */
public interface WithdrawalOrderRepository {

    Optional<WithdrawalOrder> findByOrderNo(String orderNo);

    void save(WithdrawalOrder order);

    List<WithdrawalOrder> findByAnchor(long anchorId);
}
