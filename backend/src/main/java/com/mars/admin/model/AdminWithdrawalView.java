package com.mars.admin.model;

import java.time.LocalDateTime;

/**
 * 财务提现行（契约 §4.9 API-ADM-24 · 前端 {@code WithdrawalRow}，§5.3.1 B 字段名映射）。
 *
 * @param preTaxAmount 申请金额（DB {@code amount_fen}）
 * @param taxWithheld  代扣个税（DB {@code tax_fen}，仅 {@code WITHDRAW} 模式生效）
 * @param netAmount    到账金额（DB {@code net_amount_fen} = preTax − taxWithheld）
 * @param statusNote   驳回原因（DB {@code reject_reason}）
 */
public record AdminWithdrawalView(
        String orderNo,
        String period,
        String anchorName,
        String realName,
        long preTaxAmount,
        long taxWithheld,
        long netAmount,
        String bankAccountMasked,
        boolean bankNameMatch,
        String status,
        String statusNote,
        LocalDateTime paidAt) {
}
