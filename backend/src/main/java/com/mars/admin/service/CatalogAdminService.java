package com.mars.admin.service;

import com.mars.admin.model.Banner;
import com.mars.admin.model.Category;
import com.mars.admin.repo.CatalogRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AdminOperator;
import com.mars.moderation.AuditRecorder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 目录 / 运营位管理（契约 §4.9 API-ADM-17/18/19 · FR-11.2 / OP-7）。
 *
 * <p>类目更新与运营位新建 / 更新落审计。运营位 {@code target_type} ∈ {@code ROOM/CATEGORY/URL}。
 */
public final class CatalogAdminService {

    private static final Set<String> TARGET_TYPES = Set.of("ROOM", "CATEGORY", "URL");

    private final CatalogRepository catalog;
    private final AuditRecorder audit;

    public CatalogAdminService(CatalogRepository catalog, AuditRecorder audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    /** API-ADM-17。 */
    public List<Category> listCategories() {
        return catalog.listCategories();
    }

    /** API-ADM-18：局部更新（{@code null} 字段保持不变）。 */
    public Category updateCategory(long id, Boolean enabled, Integer sort, String name, AdminOperator operator) {
        Category current = catalog.findCategory(id).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "类目不存在", "id", "not_found"));
        Category updated = current.withEditable(enabled, sort, name);
        catalog.updateCategory(updated);
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_CATEGORY_UPDATE,
                "CATEGORY", String.valueOf(id), updated.name(), null, null, null, operator.ip());
        return updated;
    }

    /** API-ADM-19：运营位列表。 */
    public List<Banner> listBanners() {
        return catalog.listBanners();
    }

    /** API-ADM-19：新建运营位。 */
    public Banner createBanner(Banner draft, AdminOperator operator) {
        Banner valid = validate(draft);
        long id = catalog.insertBanner(valid);
        Banner created = new Banner(id, valid.title(), valid.targetType(), valid.targetValue(),
                valid.startAt(), valid.endAt(), valid.enabled(), valid.sort());
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_BANNER_CREATE,
                "BANNER", String.valueOf(id), created.title(), null, null, null, operator.ip());
        return created;
    }

    /** API-ADM-19：更新运营位（整行覆盖，前端提交完整对象）。 */
    public Banner updateBanner(long id, Banner patch, AdminOperator operator) {
        catalog.findBanner(id).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "运营位不存在", "id", "not_found"));
        Banner valid = validate(new Banner(id, patch.title(), patch.targetType(), patch.targetValue(),
                patch.startAt(), patch.endAt(), patch.enabled(), patch.sort()));
        catalog.updateBanner(valid);
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_BANNER_UPDATE,
                "BANNER", String.valueOf(id), valid.title(), null, null, null, operator.ip());
        return valid;
    }

    private static Banner validate(Banner banner) {
        if (banner.title() == null || banner.title().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题不能为空", "title", "blank");
        }
        if (!TARGET_TYPES.contains(banner.targetType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "跳转类型非法", "target_type", "invalid");
        }
        LocalDateTime start = banner.startAt();
        LocalDateTime end = banner.endAt();
        if (start != null && end != null && end.isBefore(start)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间早于开始时间", "end_at", "invalid");
        }
        return banner;
    }
}
