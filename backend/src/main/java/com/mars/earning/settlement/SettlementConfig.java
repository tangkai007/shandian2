package com.mars.earning.settlement;

import java.math.BigDecimal;

/**
 * 结算口径（全部来自 {@code sys_config}，改口径不发版；S1a v0.4 §3.4.4）。
 *
 * @param period          结算周期，UTC+8 自然月，如 {@code 2026-11}
 * @param anchorShareRate 主播分成率（{@code OP-15} 默认 0.50）
 * @param platformTaxRate 平台流转税率（参与「先扣后分」；与主播个税分层，不混）
 */
public record SettlementConfig(String period, BigDecimal anchorShareRate, BigDecimal platformTaxRate) {

    public SettlementConfig {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period must not be blank");
        }
        if (anchorShareRate == null || anchorShareRate.signum() < 0
                || anchorShareRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("anchorShareRate must be within [0, 1]");
        }
        if (platformTaxRate == null || platformTaxRate.signum() < 0) {
            throw new IllegalArgumentException("platformTaxRate must be non-negative");
        }
    }
}
