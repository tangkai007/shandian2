package com.mars.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额运算：全部为**整数分**，禁止浮点（S1a v0.4 §3.4.5 / S2a v1.2 §2.5）。
 *
 * <p>取整策略按 S1a §3.4.5：{@code channel_fee} / {@code platform_tax_fee} / 折算额用
 * <b>HALF_UP</b>；{@code anchor_gross} 用 <b>FLOOR</b>；差额由 {@code platform_net} 吸收。
 */
public final class MoneyMath {

    private MoneyMath() {
    }

    /** 四舍五入到整数分（HALF_UP）。 */
    public static long halfUp(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 向下取整到整数分（FLOOR）。 */
    public static long floor(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** 安全除法：除数为 0 时返回 0（S1a §3.4.7「池为空时 avg_* 定义为 0」）。 */
    public static BigDecimal divide(long numerator, long denominator) {
        if (denominator == 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 12, RoundingMode.HALF_UP);
    }
}
