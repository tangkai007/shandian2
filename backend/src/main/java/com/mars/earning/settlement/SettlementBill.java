package com.mars.earning.settlement;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月度结算账单（表 {@code settlement_bill}）。
 *
 * <p>字段名<b>逐字</b>等于 S1a v0.4 §3.4 与 S2a v1.2 §4.6.1 的冻结名 —— 前端/后台/DB 三处同名，
 * 即 AC-8「主播收益明细与后台财务对账一致、误差 0」的核对基础。
 *
 * @param period          账期（UTC+8 自然月）
 * @param shareRate       结算执行时的分成率（落地 {@code share_rate}）
 * @param grossAmount     记账折算收入（{@code Σ当月消费币 × avg_coin_gross_fen}）
 * @param channelFee      渠道费（{@code Σ当月消费币 × avg_coin_fee_fen}）
 * @param platformTaxFee  平台流转税（先扣后分）
 * @param distributable   可分配金额 = {@code grossAmount − channelFee − platformTaxFee}
 * @param anchorGross     主播分成毛额 = {@code floor(distributable × shareRate)}
 * @param platformNet     平台留存 = {@code distributable − anchorGross}（吸收取整残差）
 * @param anchorTax       主播个税（代扣代缴，OP-23）
 * @param anchorNet       主播实得 = {@code anchorGross − anchorTax} → {@code earning_account.available}
 * @param adjustmentFen   历史冲正（结算后才发现）计入下一期，不改已出账账单
 * @param roundingDiffFen 渠道明细取整差（计入平台侧），保证明细与汇总闭合
 * @param status          账单状态（{@code GENERATED} / {@code PAID} ...）
 * @param channels        渠道构成明细
 */
public record SettlementBill(
        String period,
        BigDecimal shareRate,
        long grossAmount,
        long channelFee,
        long platformTaxFee,
        long distributable,
        long anchorGross,
        long platformNet,
        long anchorTax,
        long anchorNet,
        long adjustmentFen,
        long roundingDiffFen,
        String status,
        List<SettlementBillChannel> channels) {

    /** 账单已生成。 */
    public static final String STATUS_GENERATED = "GENERATED";

    public SettlementBill {
        channels = List.copyOf(channels);
    }
}
