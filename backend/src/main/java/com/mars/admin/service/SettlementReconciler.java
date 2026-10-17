package com.mars.admin.service;

import com.mars.admin.model.SettlementBillEntity;
import com.mars.earning.settlement.SettlementBill;
import com.mars.earning.settlement.SettlementBillChannel;

import java.util.List;

/**
 * 结算对账复算（契约 §4.9 API-ADM-27 · AC-8「五条对账等式」）。
 *
 * <p>纯函数：对给定账单集合逐条复算五条等式，取绝对差之和作为 {@code diff_fen}。
 * {@code diff_fen ≠ 0 → 冻结出款}（S1b D-ADM-003）。
 *
 * <pre>
 * ① gross_amount    == Σ 渠道明细 gross_fen
 * ② channel_fee     == Σ 渠道明细 fee_fen + rounding_diff_fen
 * ③ distributable   == gross_amount − channel_fee − platform_tax_fee
 * ④ distributable   == anchor_gross + platform_net
 * ⑤ anchor_gross    == anchor_net + anchor_tax
 * </pre>
 */
public final class SettlementReconciler {

    public long diffOf(List<SettlementBillEntity> bills) {
        long diff = 0L;
        for (SettlementBillEntity entity : bills) {
            diff += diffOf(entity.bill());
        }
        return diff;
    }

    public long diffOf(SettlementBill bill) {
        long detailGross = 0L;
        long detailFee = 0L;
        for (SettlementBillChannel channel : bill.channels()) {
            detailGross += channel.grossFen();
            detailFee += channel.feeFen();
        }
        long e1 = Math.abs(bill.grossAmount() - detailGross);
        long e2 = Math.abs(bill.channelFee() - (detailFee + bill.roundingDiffFen()));
        long e3 = Math.abs(bill.distributable()
                - (bill.grossAmount() - bill.channelFee() - bill.platformTaxFee()));
        long e4 = Math.abs(bill.distributable() - (bill.anchorGross() + bill.platformNet()));
        long e5 = Math.abs(bill.anchorGross() - (bill.anchorNet() + bill.anchorTax()));
        return e1 + e2 + e3 + e4 + e5;
    }
}
