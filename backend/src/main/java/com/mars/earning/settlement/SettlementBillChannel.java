package com.mars.earning.settlement;

import java.math.BigDecimal;

/**
 * 结算账单的渠道构成明细（表 {@code settlement_bill_channel}，S1a v0.4 §3.4.6 ③ 之一）。
 *
 * <p>闭合约束：{@code Σ feeFen + rounding_diff_fen == 汇总 channel_fee}（§3.4.5），
 * 使「任取一主播账单都可从明细逐项复算到 {@code anchor_net}」（AC-8）。
 *
 * @param channel         渠道
 * @param coinConsumed    该渠道分摊到的当月消费币（{@code Σ == 汇总 consumed_coin}）
 * @param coinShareBp     池内占比（万分比，{@code Σ == 10000}）
 * @param grossFen        该渠道记账折算收入（分）
 * @param feeFen          该渠道渠道成本（分）
 * @param feeRateSnapshot 下单时快照费率
 */
public record SettlementBillChannel(
        String channel,
        long coinConsumed,
        int coinShareBp,
        long grossFen,
        long feeFen,
        BigDecimal feeRateSnapshot) {
}
