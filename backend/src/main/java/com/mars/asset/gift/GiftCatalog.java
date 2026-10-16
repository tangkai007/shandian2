package com.mars.asset.gift;

import java.util.Optional;

/** 礼物目录（表 {@code gift}；仅 {@code ON} 可选）。 */
public interface GiftCatalog {

    Optional<GiftItem> find(long giftId);
}
