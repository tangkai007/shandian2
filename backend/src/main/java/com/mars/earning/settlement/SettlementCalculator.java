package com.mars.earning.settlement;

import com.mars.asset.pool.CoinPoolState;
import com.mars.asset.pool.PoolChannel;
import com.mars.common.money.MoneyMath;
import com.mars.common.money.ProportionalAllocator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算引擎（S1a v0.4 §3.4.4 分层公式 + §3.4.5 误差 0 保证 + §3.4.6 示例）。
 *
 * <p>纯函数：同输入必得同输出（账单可复算）；渠道费率一律取自<b>订单快照</b>
 * （{@link PoolChannel#feeRate()}），因此后续配置变更不影响历史账单。
 *
 * <pre>
 * gross_amount      = Σ当月消费币 × avg_coin_gross_fen
 * channel_fee       = Σ当月消费币 × avg_coin_fee_fen
 * platform_tax_fee  = round((gross_amount − channel_fee) × platform_tax_rate)   // HALF_UP
 * distributable     = gross_amount − channel_fee − platform_tax_fee             // OP-15 先扣后分
 * anchor_gross      = floor(distributable × anchor_share_rate)                 // FLOOR
 * platform_net      = distributable − anchor_gross                             // 吸收取整残差
 * anchor_tax        = TaxCalculator.calc(anchor_gross, period)                 // OP-23
 * anchor_net        = anchor_gross − anchor_tax
 * </pre>
 */
public final class SettlementCalculator {

    /** 池为空时 avg_* 定义为 0，账单生成但金额为 0（不发生除零，S1a §3.4.7）。 */
    public SettlementBill settle(CoinPoolState pool,
                                 long consumedCoin,
                                 SettlementConfig config,
                                 TaxCalculator taxCalculator) {
        if (pool == null || config == null || taxCalculator == null) {
            throw new IllegalArgumentException("pool, config and taxCalculator are required");
        }
        if (consumedCoin < 0L) {
            throw new IllegalArgumentException("consumedCoin must be non-negative");
        }

        long coinTotal = pool.coinPoolTotal();
        BigDecimal avgCoinGross = MoneyMath.divide(pool.grossPoolFen(), coinTotal);
        BigDecimal avgCoinFee = MoneyMath.divide(pool.feePoolFen(), coinTotal);

        long grossAmount = MoneyMath.halfUp(BigDecimal.valueOf(consumedCoin).multiply(avgCoinGross));
        long channelFee = MoneyMath.halfUp(BigDecimal.valueOf(consumedCoin).multiply(avgCoinFee));
        long platformTaxFee = MoneyMath.halfUp(
                BigDecimal.valueOf(grossAmount - channelFee).multiply(config.platformTaxRate()));
        long distributable = grossAmount - channelFee - platformTaxFee;
        long anchorGross = MoneyMath.floor(
                BigDecimal.valueOf(distributable).multiply(config.anchorShareRate()));
        long platformNet = distributable - anchorGross;
        long anchorTax = taxCalculator.calc(anchorGross, config.period());
        long anchorNet = anchorGross - anchorTax;

        List<PoolChannel> poolChannels = pool.channels();
        long[] weights = new long[poolChannels.size()];
        for (int i = 0; i < poolChannels.size(); i++) {
            weights[i] = poolChannels.get(i).coins();
        }
        long[] consumedByChannel = ProportionalAllocator.allocate(consumedCoin, weights);

        List<SettlementBillChannel> rows = new ArrayList<>(poolChannels.size());
        long feeSum = 0L;
        for (int i = 0; i < poolChannels.size(); i++) {
            PoolChannel channel = poolChannels.get(i);
            long coinConsumed = consumedByChannel[i];
            long grossFen = MoneyMath.halfUp(BigDecimal.valueOf(coinConsumed).multiply(avgCoinGross));
            long feeFen = MoneyMath.halfUp(BigDecimal.valueOf(coinConsumed).multiply(channel.feeRate()));
            int shareBp = coinTotal == 0L ? 0 : BigDecimal.valueOf(channel.coins())
                    .multiply(BigDecimal.valueOf(10_000L))
                    .divide(BigDecimal.valueOf(coinTotal), 0, RoundingMode.HALF_UP)
                    .intValueExact();
            rows.add(new SettlementBillChannel(
                    channel.channel(), coinConsumed, shareBp, grossFen, feeFen, channel.feeRate()));
            feeSum += feeFen;
        }
        // §3.4.5：明细取整差由 rounding_diff_fen 吸收（计入平台侧），使 Σ明细 + diff == 汇总
        long roundingDiffFen = channelFee - feeSum;

        return new SettlementBill(
                config.period(),
                config.anchorShareRate(),
                grossAmount,
                channelFee,
                platformTaxFee,
                distributable,
                anchorGross,
                platformNet,
                anchorTax,
                anchorNet,
                0L,
                roundingDiffFen,
                SettlementBill.STATUS_GENERATED,
                rows);
    }
}
