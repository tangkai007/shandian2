package com.mars.admin.mapper;

import com.mars.admin.model.GiftAdminItem;
import com.mars.admin.repo.GiftAdminRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 后台礼物目录 Mapper（表 {@code gift}），契约 §4.9 API-ADM-14/15/16。
 *
 * <p>记录（record）不可变，故用 {@code insertRow} + {@code LAST_INSERT_ID()} 取回自增主键。
 */
@Mapper
public interface GiftAdminMapper extends GiftAdminRepository {

    String COLUMNS = "id AS id, name AS name, price_coin AS priceCoin, "
            + "effect_type AS effectType, status AS status";

    @Override
    @Select("SELECT " + COLUMNS + " FROM `gift` ORDER BY sort ASC, id ASC")
    List<GiftAdminItem> listAll();

    @Override
    @Select("SELECT " + COLUMNS + " FROM `gift` WHERE id = #{giftId}")
    Optional<GiftAdminItem> findById(@Param("giftId") long giftId);

    @Insert("INSERT INTO `gift` (name, price_coin, effect_type, status) "
            + "VALUES (#{item.name}, #{item.priceCoin}, #{item.effectType}, #{item.status})")
    void insertRow(@Param("item") GiftAdminItem item);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Override
    default long insert(GiftAdminItem item) {
        insertRow(item);
        return lastInsertId();
    }

    @Override
    @Update("UPDATE `gift` SET name = #{name}, price_coin = #{priceCoin}, "
            + "effect_type = #{effectType}, status = #{status} WHERE id = #{id}")
    void update(GiftAdminItem item);
}
