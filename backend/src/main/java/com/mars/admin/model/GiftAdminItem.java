package com.mars.admin.model;

/**
 * 后台礼物行（契约 §4.9 API-ADM-14/15/16 · 前端 {@code GiftRow}）。
 *
 * @param status {@code ON}（在售）/ {@code OFF}（下架）
 */
public record GiftAdminItem(long id, String name, long priceCoin, String effectType, String status) {

    public static final String STATUS_ON = "ON";
    public static final String STATUS_OFF = "OFF";

    public boolean onSale() {
        return STATUS_ON.equals(status);
    }

    public GiftAdminItem {
        if (priceCoin < 0L) {
            throw new IllegalArgumentException("priceCoin must be non-negative");
        }
    }
}
