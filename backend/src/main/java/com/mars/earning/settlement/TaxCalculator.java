package com.mars.earning.settlement;

/**
 * 个税计算钩子（S1a v0.4 §3.4.4 / OP-23，待人类定值）。
 *
 * <p>本轮只落<b>配置位 + 计算钩子</b>：切换规则只改实现 + 配置，<b>不动账单结构</b>。
 * 账单中的 {@code anchor_tax} 字段与 {@code anchor_tax_mode} 两模式均已定义（S2a v1.2 §4.7）。
 */
@FunctionalInterface
public interface TaxCalculator {

    /**
     * @param anchorGrossFen 主播分成毛额（分）—— 分成之后、扣个税之前
     * @param period         结算周期（如 {@code 2026-11}，UTC+8 自然月）
     * @return 代扣个人所得税（分）
     */
    long calc(long anchorGrossFen, String period);
}
