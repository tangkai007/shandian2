package com.mars.asset.pool;

import java.math.BigDecimal;

/**
 * 币池中的单个渠道贡献（S1a v0.4 §3.4.1–3.4.3）。
 *
 * @param channel  渠道：{@code WECHAT} / {@code ALIPAY} / {@code IAP}
 * @param coins    入池虚拟币数（含套餐赠送币）
 * @param grossFen 该批币的<b>实付</b>金额（分）—— 赠送币计入币数但不计入 gross
 * @param feeFen   该批币对应的渠道成本（分），入池时按 {@code feeRate} 快照计算
 * @param feeRate  <b>下单时快照</b>的渠道费率（如 0.006 / 0.30）；历史账单永不受后续配置变更影响
 */
public record PoolChannel(String channel, long coins, long grossFen, long feeFen, BigDecimal feeRate) {

    public PoolChannel {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        if (coins < 0L || grossFen < 0L || feeFen < 0L) {
            throw new IllegalArgumentException("pool channel amounts must be non-negative");
        }
        if (feeRate == null || feeRate.signum() < 0) {
            throw new IllegalArgumentException("feeRate must be non-negative");
        }
    }
}
