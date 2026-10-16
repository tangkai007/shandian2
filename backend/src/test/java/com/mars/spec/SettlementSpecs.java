package com.mars.spec;

import com.mars.asset.pool.CoinPoolState;
import com.mars.earning.settlement.SettlementBill;
import com.mars.earning.settlement.SettlementBillChannel;
import com.mars.earning.settlement.SettlementCalculator;
import com.mars.earning.settlement.SettlementConfig;
import com.mars.earning.tax.FlatTaxCalculator;
import com.mars.testkit.Registry;

import java.math.BigDecimal;

/**
 * 结算 spec —— 直接以 S1a v0.4 §3.4.6 的完整示例为断言基线（AC-8：对账误差 0）。
 *
 * <p>凭证：PRD v0.3 / S1a v0.4 §3.4 / S2a v1.2 §4.6.1。字段名逐字对齐，不做重命名。
 */
public final class SettlementSpecs {

    private static final String PERIOD = "2026-11";
    private static final long CONSUMED_COIN = 400_000L;

    private SettlementSpecs() {
    }

    /** §3.4.6 ① 的币池：微信 80 万 / 支付宝 50 万 / IAP 30 万，费率按下单快照。 */
    static CoinPoolState standardPool() {
        CoinPoolState pool = new CoinPoolState();
        pool.credit("WECHAT", 800_000L, 800_000L, 4_800L, new BigDecimal("0.006"));
        pool.credit("ALIPAY", 500_000L, 500_000L, 3_000L, new BigDecimal("0.006"));
        pool.credit("IAP", 300_000L, 300_000L, 90_000L, new BigDecimal("0.30"));
        return pool;
    }

    static SettlementConfig standardConfig() {
        return new SettlementConfig(PERIOD, new BigDecimal("0.50"), new BigDecimal("0.06"));
    }

    private static SettlementBill settleStandard() {
        return new SettlementCalculator().settle(
                standardPool(), CONSUMED_COIN, standardConfig(), FlatTaxCalculator.placeholder());
    }

    public static void register(Registry registry) {

        registry.add("AC-8 · S1a §3.4.6 复算示例：逐字段闭合", a -> {
            SettlementBill bill = settleStandard();

            a.eq(bill.period(), PERIOD, "period");
            a.eq(bill.shareRate(), new BigDecimal("0.50"), "share_rate");
            a.eqLong(bill.grossAmount(), 400_000L, "gross_amount（400,000 分）");
            a.eqLong(bill.channelFee(), 24_450L, "channel_fee（24,450 分）");
            a.eqLong(bill.platformTaxFee(), 22_533L, "platform_tax_fee（22,533 分）");
            a.eqLong(bill.distributable(), 353_017L, "distributable（353,017 分）");
            a.eqLong(bill.anchorGross(), 176_508L, "anchor_gross（176,508 分，FLOOR）");
            a.eqLong(bill.platformNet(), 176_509L, "platform_net（176,509 分，吸收取整残差）");
            a.eqLong(bill.anchorTax(), 10_590L, "anchor_tax（10,590 分，OP-23 占位）");
            a.eqLong(bill.anchorNet(), 165_918L, "anchor_net（165,918 分）");
            a.eqLong(bill.adjustmentFen(), 0L, "adjustment_fen（本期无冲正）");
            a.eqLong(bill.roundingDiffFen(), 0L, "rounding_diff_fen（示例为 0）");
            a.eq(bill.status(), SettlementBill.STATUS_GENERATED, "status");
        });

        registry.add("AC-8 · 公式结构：先扣后分 + 税费分层 + 个别名闭合", a -> {
            SettlementBill b = settleStandard();
            a.eqLong(b.distributable(), b.grossAmount() - b.channelFee() - b.platformTaxFee(),
                    "distributable = gross − channel_fee − platform_tax_fee（OP-15 先扣后分）");
            a.eqLong(b.platformNet() + b.anchorGross(), b.distributable(),
                    "platform_net + anchor_gross = distributable");
            a.eqLong(b.anchorNet() + b.anchorTax(), b.anchorGross(),
                    "anchor_net + anchor_tax = anchor_gross（个税在分成之后扣，不参与 50/50）");
            // 主播个税不得进入平台分配：platform_net 与 anchor_tax 无算术耦合
            a.eqLong(b.platformNet(), 176_509L, "platform_net 未吸收 anchor_tax");
        });

        registry.add("AC-8 · 渠道明细：Σ明细 + rounding_diff = 汇总，且占比合计 100%", a -> {
            SettlementBill b = settleStandard();
            a.eqLong((long) b.channels().size(), 3L, "明细行数 = 池内渠道数");

            SettlementBillChannel wechat = b.channels().get(0);
            a.eq(wechat.channel(), "WECHAT", "行序稳定：微信");
            a.eqLong(wechat.coinConsumed(), 200_000L, "微信消费币");
            a.eqLong((long) wechat.coinShareBp(), 5_000L, "微信占比 5000bp");
            a.eqLong(wechat.grossFen(), 200_000L, "微信 gross");
            a.eqLong(wechat.feeFen(), 1_200L, "微信 fee（200,000 × 0.6%）");
            a.eq(wechat.feeRateSnapshot(), new BigDecimal("0.006"), "微信费率快照");

            SettlementBillChannel alipay = b.channels().get(1);
            a.eqLong(alipay.coinConsumed(), 125_000L, "支付宝消费币");
            a.eqLong((long) alipay.coinShareBp(), 3_125L, "支付宝占比 3125bp");
            a.eqLong(alipay.feeFen(), 750L, "支付宝 fee（125,000 × 0.6%）");

            SettlementBillChannel iap = b.channels().get(2);
            a.eqLong(iap.coinConsumed(), 75_000L, "IAP 消费币");
            a.eqLong((long) iap.coinShareBp(), 1_875L, "IAP 占比 1875bp");
            a.eqLong(iap.feeFen(), 22_500L, "IAP fee（75,000 × 30%）");
            a.eq(iap.feeRateSnapshot(), new BigDecimal("0.30"), "IAP 费率快照");

            long coinSum = 0L;
            long grossSum = 0L;
            long feeSum = 0L;
            int bpSum = 0;
            for (SettlementBillChannel row : b.channels()) {
                coinSum += row.coinConsumed();
                grossSum += row.grossFen();
                feeSum += row.feeFen();
                bpSum += row.coinShareBp();
            }
            a.eqLong(coinSum, CONSUMED_COIN, "Σ消费币 = 汇总消费币");
            a.eqLong(grossSum, b.grossAmount(), "Σ明细 gross = 汇总 gross_amount");
            a.eqLong(bpSum, 10_000L, "Σ占比 = 10000bp（100%）");
            a.eqLong(feeSum + b.roundingDiffFen(), b.channelFee(),
                    "Σ明细 fee + rounding_diff_fen = 汇总 channel_fee（闭合恒等式）");
        });

        registry.add("AC-8 · rounding_diff 吸收渠道取整差（含负差情形）", a -> {
            // 3 渠道各 10 币 / 实付 10 分 / 费 5 分 → avg_fee = 0.5 分/币
            CoinPoolState pool = new CoinPoolState();
            pool.credit("WECHAT", 10L, 10L, 5L, new BigDecimal("0.5"));
            pool.credit("ALIPAY", 10L, 10L, 5L, new BigDecimal("0.5"));
            pool.credit("IAP", 10L, 10L, 5L, new BigDecimal("0.5"));
            SettlementBill b = new SettlementCalculator().settle(
                    pool, 3L, standardConfig(), FlatTaxCalculator.placeholder());

            a.eqLong(b.grossAmount(), 3L, "汇总 gross（3 币 × 1.0 分）");
            a.eqLong(b.channelFee(), 2L, "汇总 channel_fee = HALF_UP(3 × 0.5) = 2");
            long feeSum = 0L;
            for (SettlementBillChannel row : b.channels()) {
                feeSum += row.feeFen();
            }
            a.eqLong(feeSum, 3L, "明细 Σfee = 3（逐渠道 HALF_UP(1 × 0.5) = 1）");
            a.eqLong(b.roundingDiffFen(), -1L, "取整差 = 2 − 3 = −1（计入平台侧）");
            a.eqLong(feeSum + b.roundingDiffFen(), b.channelFee(), "Σ明细 + diff = 汇总（恒等）");
        });

        registry.add("AC-8 · 同输入重复结算逐字节一致（账单可复算）", a -> {
            SettlementBill first = settleStandard();
            SettlementBill second = settleStandard();
            SettlementBill third = settleStandard();

            a.eqLong(second.anchorNet(), first.anchorNet(), "第 2 次 anchor_net 一致");
            a.eqLong(third.anchorNet(), first.anchorNet(), "第 3 次 anchor_net 一致");
            a.eqLong(second.roundingDiffFen(), first.roundingDiffFen(), "取整差一致");
            a.eq(first.channels().toString(), second.channels().toString(), "明细逐项一致");
            a.eq(second.toString(), third.toString(), "整张账单一致");
        });

        registry.add("AC-8 · 费率/税率快照：后续配置变更不影响历史账单", a -> {
            SettlementBill november = settleStandard();

            // 后续配置变更：平台税率改 10%、分成率改 60% —— 只影响新账期
            SettlementConfig december = new SettlementConfig("2026-12", new BigDecimal("0.60"), new BigDecimal("0.10"));
            SettlementBill decemberBill = new SettlementCalculator().settle(
                    standardPool(), CONSUMED_COIN, december, FlatTaxCalculator.placeholder());

            a.eqLong(november.platformTaxFee(), 22_533L, "11 月账单 platform_tax_fee 不随后续配置变");
            a.eqLong(november.anchorGross(), 176_508L, "11 月账单 anchor_gross 不变");
            a.eqLong(november.anchorNet(), 165_918L, "11 月账单 anchor_net 不变");
            a.eq(decemberBill.shareRate(), new BigDecimal("0.60"), "12 月账单记录新 share_rate");
            a.isTrue(decemberBill.platformTaxFee() != november.platformTaxFee(), "新账期按新税率重算");
            a.isTrue(decemberBill.anchorGross() != november.anchorGross(), "新账期按新分成率重算");

            // 历史账单的渠道费率取自订单快照：即使把「当前费率」设为 30%，11 月明细仍是 0.6%
            a.eq(november.channels().get(0).feeRateSnapshot(), new BigDecimal("0.006"),
                    "11 月微信明细费率仍是下单快照 0.6%");
        });

        registry.add("AC-8 · 边界：空池 / 零消费不除零，账单仍生成", a -> {
            SettlementCalculator calc = new SettlementCalculator();

            SettlementBill emptyPool = calc.settle(new CoinPoolState(), CONSUMED_COIN,
                    standardConfig(), FlatTaxCalculator.placeholder());
            a.eqLong(emptyPool.grossAmount(), 0L, "空池 gross = 0");
            a.eqLong(emptyPool.channelFee(), 0L, "空池 channel_fee = 0");
            a.eqLong(emptyPool.platformTaxFee(), 0L, "空池 platform_tax_fee = 0");
            a.eqLong(emptyPool.anchorGross(), 0L, "空池 anchor_gross = 0");
            a.eqLong(emptyPool.anchorTax(), 0L, "空池 anchor_tax = 0");
            a.eqLong(emptyPool.anchorNet(), 0L, "空池 anchor_net = 0");
            a.eq(emptyPool.status(), SettlementBill.STATUS_GENERATED, "空池仍生成账单");

            SettlementBill zeroConsumed = calc.settle(standardPool(), 0L,
                    standardConfig(), FlatTaxCalculator.placeholder());
            a.eqLong(zeroConsumed.grossAmount(), 0L, "零消费 gross = 0");
            a.eqLong(zeroConsumed.channelFee(), 0L, "零消费 channel_fee = 0");
            a.eqLong(zeroConsumed.anchorNet(), 0L, "零消费 anchor_net = 0");
        });

        registry.add("AC-8 · 对账等式（每张账单层面的 5 条恒等）", a -> {
            SettlementBill b = settleStandard();

            // (1) 渠道明细 gross 合计 == 汇总 gross_amount
            long detailGross = 0L;
            long detailFee = 0L;
            for (SettlementBillChannel row : b.channels()) {
                detailGross += row.grossFen();
                detailFee += row.feeFen();
            }
            a.eqLong(detailGross, b.grossAmount(), "(1) Σ明细 gross == 汇总 gross_amount");
            // (2) 渠道明细 fee + 取整差 == 汇总 channel_fee
            a.eqLong(detailFee + b.roundingDiffFen(), b.channelFee(), "(2) Σ明细 fee + diff == channel_fee");
            // (3) anchor_gross 为 floor(distributable × share_rate)，差额由 platform_net 吸收
            a.eqLong(b.anchorGross() + b.platformNet(), b.distributable(), "(3) anchor_gross + platform_net == distributable");
            // (4) anchor_net + anchor_tax == anchor_gross
            a.eqLong(b.anchorNet() + b.anchorTax(), b.anchorGross(), "(4) anchor_net + anchor_tax == anchor_gross");
            // (5) 记账折算：gross_amount == 消费币 × 池内加权平均实付成本（此处 avg = 1.000000）
            a.eqLong(b.grossAmount(), CONSUMED_COIN * 1L, "(5) gross_amount == 消费币 × avg_coin_gross_fen");
        });
    }
}
