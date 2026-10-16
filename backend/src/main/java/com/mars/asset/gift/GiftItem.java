package com.mars.asset.gift;

/**
 * 礼物目录项（表 {@code gift}）。
 *
 * @param id        礼物 ID
 * @param name      名称
 * @param priceCoin 单价（币）
 * @param onSale    是否在售（{@code ON}/{@code OFF}，下架不可赠）
 */
public record GiftItem(long id, String name, long priceCoin, boolean onSale) {

    public GiftItem {
        if (priceCoin < 0L) {
            throw new IllegalArgumentException("priceCoin must be non-negative");
        }
    }
}
