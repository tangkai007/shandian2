package com.mars.asset.mapper;

import com.mars.asset.gift.GiftCatalog;
import com.mars.asset.gift.GiftItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/** 礼物目录 Mapper（表 {@code gift}）。 */
@Mapper
public interface GiftMapper extends GiftCatalog {

    @Override
    @Select("SELECT id, name, price_coin AS priceCoin, (status = 'ON') AS onSale "
            + "FROM gift WHERE id = #{giftId}")
    Optional<GiftItem> find(long giftId);

    /** 在售目录（契约 §4.6 API-ASSET-01）。 */
    @Select("SELECT id, name, price_coin AS priceCoin, (status = 'ON') AS onSale "
            + "FROM gift WHERE status = 'ON' ORDER BY sort, id")
    List<GiftItem> findOnSale();
}
