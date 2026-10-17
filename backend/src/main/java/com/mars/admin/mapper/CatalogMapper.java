package com.mars.admin.mapper;

import com.mars.admin.model.Banner;
import com.mars.admin.model.Category;
import com.mars.admin.repo.CatalogRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 目录 / 运营位 Mapper（{@code live_category}、{@code banner}），契约 §4.9 API-ADM-17/18/19。
 */
@Mapper
public interface CatalogMapper extends CatalogRepository {

    String CATEGORY_COLUMNS = "id AS id, name AS name, require_qualification AS requireQualification, "
            + "restricted AS restricted, sort AS sort, (status = 'ENABLED') AS enabled";

    String BANNER_COLUMNS = "id AS id, title AS title, target_type AS targetType, "
            + "target_value AS targetValue, start_at AS startAt, end_at AS endAt, "
            + "enabled AS enabled, sort AS sort";

    @Override
    @Select("SELECT " + CATEGORY_COLUMNS + " FROM `live_category` ORDER BY sort ASC, id ASC")
    List<Category> listCategories();

    @Override
    @Select("SELECT " + CATEGORY_COLUMNS + " FROM `live_category` WHERE id = #{id}")
    Optional<Category> findCategory(@Param("id") long id);

    @Override
    @Update("UPDATE `live_category` SET name = #{name}, sort = #{sort}, "
            + "status = #{statusValue} WHERE id = #{id}")
    void updateCategory(Category category);

    @Override
    @Select("SELECT " + BANNER_COLUMNS + " FROM `banner` ORDER BY sort ASC, id ASC")
    List<Banner> listBanners();

    @Override
    @Select("SELECT " + BANNER_COLUMNS + " FROM `banner` WHERE id = #{id}")
    Optional<Banner> findBanner(@Param("id") long id);

    @Insert("INSERT INTO `banner` (title, target_type, target_value, start_at, end_at, enabled, sort) "
            + "VALUES (#{banner.title}, #{banner.targetType}, #{banner.targetValue}, "
            + "#{banner.startAt}, #{banner.endAt}, #{banner.enabled}, #{banner.sort})")
    void insertBannerRow(@Param("banner") Banner banner);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Override
    default long insertBanner(Banner banner) {
        insertBannerRow(banner);
        return lastInsertId();
    }

    @Override
    @Update("UPDATE `banner` SET title = #{title}, target_type = #{targetType}, "
            + "target_value = #{targetValue}, start_at = #{startAt}, end_at = #{endAt}, "
            + "enabled = #{enabled}, sort = #{sort} WHERE id = #{id}")
    void updateBanner(Banner banner);
}
