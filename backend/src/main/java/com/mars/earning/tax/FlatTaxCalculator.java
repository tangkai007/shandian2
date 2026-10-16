package com.mars.earning.tax;

import com.mars.common.money.MoneyMath;
import com.mars.earning.settlement.TaxCalculator;

import java.math.BigDecimal;

/**
 * 个税占位实现（S1a v0.4 §3.4.4）：<b>{@link #PENDING_OP_23} 占位费率，上线前必须替换为财务确认口径</b>。
 *
 * <p>仅用于打通「配置位 + 计算钩子」与账单结构；不构成任何税务结论。
 */
public final class FlatTaxCalculator implements TaxCalculator {

    /** 占位标记：财务（OP-23）定值前的默认规则。 */
    public static final String PENDING_OP_23 = "PENDING-OP-23";

    /** 占位费率（与 S1a §3.4.6 示例一致，便于复算对账）。 */
    public static final BigDecimal PLACEHOLDER_RATE = new BigDecimal("0.06");

    private final BigDecimal rate;

    public FlatTaxCalculator(BigDecimal rate) {
        if (rate == null || rate.signum() < 0) {
            throw new IllegalArgumentException("rate must be non-negative");
        }
        this.rate = rate;
    }

    public static FlatTaxCalculator placeholder() {
        return new FlatTaxCalculator(PLACEHOLDER_RATE);
    }

    public BigDecimal rate() {
        return rate;
    }

    @Override
    public long calc(long anchorGrossFen, String period) {
        if (anchorGrossFen <= 0L) {
            return 0L;
        }
        return MoneyMath.halfUp(BigDecimal.valueOf(anchorGrossFen).multiply(rate));
    }
}
