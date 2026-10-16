package com.mars.asset.pool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 滚动累计币池（S1a v0.4 §3.4.2「加权平均成本法」）。
 *
 * <p>充值成功时在<b>同一本地事务</b>内按渠道累加 {@code coins / grossFen / feeFen}；
 * 退款/撤销同事务冲减。结算按<b>池内加权平均</b>取数，因此：
 * <ul>
 *   <li>O(1) 取数、无逐币归因写放大（MVP 不需要逐币追溯）；</li>
 *   <li>池为全局累计，历史冲正不会使池变负，成本差异由当期结算自然吸收（§3.4.7）。</li>
 * </ul>
 *
 * @see PoolChannel
 */
public final class CoinPoolState {

    private final Map<String, PoolChannel> byChannel = new LinkedHashMap<>();
    private long version;

    /** 充值入池（同事务调用）。 */
    public void credit(String channel, long coins, long grossFen, long feeFen, BigDecimal feeRate) {
        merge(channel, coins, grossFen, feeFen, feeRate);
        version++;
    }

    /** 退款/撤销冲减（同事务调用，入参为正数，内部取负）。 */
    public void reverse(String channel, long coins, long grossFen, long feeFen, BigDecimal feeRate) {
        merge(channel, -coins, -grossFen, -feeFen, feeRate);
        version++;
    }

    private void merge(String channel, long coins, long grossFen, long feeFen, BigDecimal feeRate) {
        PoolChannel existing = byChannel.get(channel);
        long nextCoins = (existing == null ? 0L : existing.coins()) + coins;
        long nextGross = (existing == null ? 0L : existing.grossFen()) + grossFen;
        long nextFee = (existing == null ? 0L : existing.feeFen()) + feeFen;
        // 池为全局累计，冲减后不应为负（§3.4.7）
        byChannel.put(channel, new PoolChannel(
                channel, Math.max(0L, nextCoins), Math.max(0L, nextGross), Math.max(0L, nextFee), feeRate));
    }

    public long coinPoolTotal() {
        long sum = 0L;
        for (PoolChannel c : byChannel.values()) {
            sum += c.coins();
        }
        return sum;
    }

    public long grossPoolFen() {
        long sum = 0L;
        for (PoolChannel c : byChannel.values()) {
            sum += c.grossFen();
        }
        return sum;
    }

    public long feePoolFen() {
        long sum = 0L;
        for (PoolChannel c : byChannel.values()) {
            sum += c.feeFen();
        }
        return sum;
    }

    /** 各渠道贡献（保持插入顺序，便于账单明细稳定复算）。 */
    public List<PoolChannel> channels() {
        return new ArrayList<>(byChannel.values());
    }

    public long version() {
        return version;
    }
}
