package com.mars.admin.repo;

import com.mars.asset.pool.CoinPoolState;

import java.util.List;

/**
 * 月结取数端口（契约 §4.9 API-ADM-23 · FR-10.2 / OP-15）。
 *
 * <p>月结任务按账期枚举「各主播的结算输入」。生产实现应从 {@code coin_pool_state} 与
 * 当月消费流水取数；本增量装配 {@code EmptySettlementSource}（无运行时来源 → 空），
 * 已登记为「已知限制」（月结批任务待接入）。
 */
public interface SettlementSource {

    /** 该账期各主播的结算输入；无来源时返回空表。 */
    List<AnchorSettlementInput> inputsFor(String period);

    /**
     * 单主播结算输入。
     *
     * @param anchorId     主播用户 ID
     * @param pool         当期币池快照
     * @param consumedCoin 当期该主播直播间被消费的币数
     */
    record AnchorSettlementInput(long anchorId, CoinPoolState pool, long consumedCoin) {
    }
}
