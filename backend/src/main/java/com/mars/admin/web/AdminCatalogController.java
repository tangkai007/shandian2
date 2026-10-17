package com.mars.admin.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.admin.model.Banner;
import com.mars.admin.model.Category;
import com.mars.admin.service.CatalogAdminService;
import com.mars.common.ApiResponse;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.web.AdminActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台类目 / 运营位管理（契约 §4.9 API-ADM-17/18/19 · FR-11.2 / OP-7）。
 *
 * <p>角色 {@code OPS}。类目更新与运营位新建 / 更新落审计。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequireRoles({"OPS"})
public class AdminCatalogController {

    private final CatalogAdminService catalog;

    public AdminCatalogController(CatalogAdminService catalog) {
        this.catalog = catalog;
    }

    /** API-ADM-17：类目列表。 */
    @GetMapping("/categories")
    public ApiResponse<PageResult<CategoryRow>> categories(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        List<CategoryRow> rows = catalog.listCategories().stream()
                .map(AdminCatalogController::toCategoryRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    /** API-ADM-18：类目局部更新（{@code null} 字段保持不变）。 */
    @PatchMapping("/categories/{id}")
    public ApiResponse<CategoryRow> updateCategory(@PathVariable long id,
                                                   @RequestBody CategoryUpdateRequest body,
                                                   HttpServletRequest request) {
        Category updated = catalog.updateCategory(id, body.enabled(), body.sort(), body.name(),
                AdminActor.operator(request));
        return ApiResponse.ok(toCategoryRow(updated));
    }

    /** API-ADM-19：运营位列表。 */
    @GetMapping("/banners")
    public ApiResponse<PageResult<BannerRow>> banners(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        List<BannerRow> rows = catalog.listBanners().stream()
                .map(AdminCatalogController::toBannerRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    /** API-ADM-19：新建运营位。 */
    @PostMapping("/banners")
    public ApiResponse<BannerRow> createBanner(@RequestBody BannerRequest body, HttpServletRequest request) {
        Banner created = catalog.createBanner(toBanner(0L, body), AdminActor.operator(request));
        return ApiResponse.ok(toBannerRow(created));
    }

    /** API-ADM-19：更新运营位（整行覆盖，前端提交完整对象）。 */
    @PatchMapping("/banners/{id}")
    public ApiResponse<BannerRow> updateBanner(@PathVariable long id,
                                               @RequestBody BannerRequest body,
                                               HttpServletRequest request) {
        Banner updated = catalog.updateBanner(id, toBanner(id, body), AdminActor.operator(request));
        return ApiResponse.ok(toBannerRow(updated));
    }

    static Banner toBanner(long id, BannerRequest body) {
        return new Banner(id, body.title(), body.targetType(), body.targetValue(),
                body.startAt(), body.endAt(), body.enabled(), body.sort());
    }

    static CategoryRow toCategoryRow(Category category) {
        return new CategoryRow(category.id(), category.name(), category.requireQualification(),
                category.restricted(), category.sort(), category.enabled());
    }

    static BannerRow toBannerRow(Banner banner) {
        return new BannerRow(banner.id(), banner.title(), banner.targetType(), banner.targetValue(),
                banner.startAt(), banner.endAt(), banner.enabled(), banner.sort());
    }

    /** 类目更新请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CategoryUpdateRequest(String name, Boolean enabled, Integer sort) {
    }

    /** 运营位请求体（创建与更新共用；{@code id} 由路径决定）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BannerRequest(String title,
                                String targetType,
                                String targetValue,
                                LocalDateTime startAt,
                                LocalDateTime endAt,
                                boolean enabled,
                                int sort) {
    }

    /** 类目行（前端 {@code CategoryRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CategoryRow(long id,
                              String name,
                              boolean requireQualification,
                              boolean restricted,
                              int sort,
                              boolean enabled) {
    }

    /** 运营位行（前端 {@code BannerRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BannerRow(long id,
                            String title,
                            String targetType,
                            String targetValue,
                            LocalDateTime startAt,
                            LocalDateTime endAt,
                            boolean enabled,
                            int sort) {
    }
}
