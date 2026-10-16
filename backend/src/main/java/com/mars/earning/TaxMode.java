package com.mars.earning;

/**
 * 个税扣缴时点（{@code anchor_tax_mode}，S1a v0.4 §3.4.8 / S1b v3.2 §11.2 B 域）。
 *
 * <ul>
 *   <li>{@link #SETTLE}（默认）：个税已在<b>结算</b>时代扣（{@code anchor_tax}）；
 *       提现页只展示「提现金额 + 实际到账」两行，<b>不出现「代扣个税」行</b>；</li>
 *   <li>{@link #WITHDRAW}：提现时代扣，{@code tax_withheld} 生效，三段式展示。</li>
 * </ul>
 */
public enum TaxMode {
    SETTLE,
    WITHDRAW
}
