package com.mars.admin.repo;

import com.mars.admin.model.Banner;
import com.mars.admin.model.Category;

import java.util.List;
import java.util.Optional;

/**
 * 目录 / 运营位仓储（表 {@code live_category}、{@code banner}），契约 §4.9 API-ADM-17/18/19。
 */
public interface CatalogRepository {

    List<Category> listCategories();

    Optional<Category> findCategory(long id);

    void updateCategory(Category category);

    List<Banner> listBanners();

    Optional<Banner> findBanner(long id);

    long insertBanner(Banner banner);

    void updateBanner(Banner banner);
}
