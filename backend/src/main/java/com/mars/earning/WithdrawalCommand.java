package com.mars.earning;

/**
 * 提现申请入参（契约 §4.7 API-EARN-06）。
 *
 * @param bankNameMatchesRealname 银行卡姓名与实名是否一致（BR-6）
 * @param taxMode                 个税扣缴时点（{@code SETTLE} 默认 / {@code WITHDRAW}）
 */
public record WithdrawalCommand(
        long anchorId,
        AnchorType anchorType,
        long amountFen,
        boolean bankNameMatchesRealname,
        TaxMode taxMode,
        long taxWithheldFen) {
}
