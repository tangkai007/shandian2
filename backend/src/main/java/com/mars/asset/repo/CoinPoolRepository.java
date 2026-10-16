package com.mars.asset.repo;

import com.mars.asset.pool.CoinPoolState;

/**
 * 币池仓储（单例行 {@code coin_pool_state} + 渠道构成 {@code coin_pool_channel}）。
 *
 * <p>充值/退款必须在<b>同一事务</b>内更新池与钱包（S1a §3.4.2）。
 */
public interface CoinPoolRepository {

    CoinPoolState load();

    void save(CoinPoolState state);
}
