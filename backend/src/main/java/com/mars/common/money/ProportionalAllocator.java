package com.mars.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 按权重把总量拆分为整数份，余数按<b>最大余数法</b>分配，保证 {@code Σ结果 == total}。
 *
 * <p>用于结算的渠道构成拆分（S1a §3.4.6 ③）：先把 {@code consumed_coin} 按各渠道池内占比
 * 拆到渠道，保证「明细之和 == 汇总」恒成立，避免逐步取整产生缺口。
 */
public final class ProportionalAllocator {

    private ProportionalAllocator() {
    }

    /**
     * @param total   待分配总量（非负）
     * @param weights 各份权重（非负）；权重和 &le; 0 时全部返回 0
     * @return 与 {@code weights} 等长的整数分配结果，满足 {@code Σ == total}
     */
    public static long[] allocate(long total, long[] weights) {
        long[] result = new long[weights.length];
        if (total <= 0L || weights.length == 0) {
            return result;
        }
        long weightSum = 0L;
        for (long w : weights) {
            if (w < 0L) {
                throw new IllegalArgumentException("weights must be non-negative");
            }
            weightSum += w;
        }
        if (weightSum <= 0L) {
            return result;
        }

        BigDecimal bTotal = BigDecimal.valueOf(total);
        BigDecimal bSum = BigDecimal.valueOf(weightSum);
        BigDecimal[] fractions = new BigDecimal[weights.length];
        long allocated = 0L;
        for (int i = 0; i < weights.length; i++) {
            BigDecimal exact = bTotal.multiply(BigDecimal.valueOf(weights[i]))
                    .divide(bSum, 24, RoundingMode.FLOOR);
            long base = exact.setScale(0, RoundingMode.FLOOR).longValueExact();
            result[i] = base;
            allocated += base;
            fractions[i] = exact.subtract(BigDecimal.valueOf(base));
        }

        // 余数按最大余数法分配；余数相等时按下标升序，保证结果确定（可复算）。
        int leftover = (int) (total - allocated);
        Integer[] order = new Integer[weights.length];
        for (int i = 0; i < weights.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int cmp = fractions[b].compareTo(fractions[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        for (int k = 0; k < leftover; k++) {
            result[order[k]]++;
        }
        return result;
    }
}
