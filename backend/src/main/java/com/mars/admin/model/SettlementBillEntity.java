package com.mars.admin.model;

import com.mars.earning.settlement.SettlementBill;

/**
 * 结算账单实体（DB 行 + 领域账单）。
 *
 * <p>DB 主键 {@code id} 与 {@code anchor_id} 在领域 {@link SettlementBill} 中无对应字段，
 * 故以本记录承载「持久化外壳」，账单金额字段仍逐字复用 S1a §3.4 的冻结名。
 */
public record SettlementBillEntity(long billId, long anchorId, SettlementBill bill) {
}
