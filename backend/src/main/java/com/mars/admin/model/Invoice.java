package com.mars.admin.model;

/**
 * 发票（契约 §4.9 API-ADM-28 · OP-11 / OP-22 · 前端 {@code InvoiceRow}）。
 *
 * @param channel {@code WECHAT / ALIPAY / IAP}
 * @param status  {@code PENDING}（待开）/ {@code ISSUED}（已开，回填 {@code invoiceUrl}）
 */
public record Invoice(
        String invoiceId,
        String orderNo,
        String buyer,
        long amountFen,
        String channel,
        String status,
        String invoiceUrl) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ISSUED = "ISSUED";

    public Invoice issued(String url) {
        return new Invoice(invoiceId, orderNo, buyer, amountFen, channel, STATUS_ISSUED, url);
    }
}
