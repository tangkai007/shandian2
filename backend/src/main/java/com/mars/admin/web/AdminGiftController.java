package com.mars.admin.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.admin.model.GiftAdminItem;
import com.mars.admin.service.GiftAdminService;
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

import java.util.List;

/**
 * 后台礼物目录管理（契约 §4.9 API-ADM-14/15/16 · FR-5.2）。
 *
 * <p>角色 {@code OPS}。新建 / 更新落审计（{@code GIFT_CREATE} / {@code GIFT_UPDATE}）。
 */
@RestController
@RequestMapping("/api/v1/admin/gifts")
@RequireRoles({"OPS"})
public class AdminGiftController {

    private final GiftAdminService gifts;

    public AdminGiftController(GiftAdminService gifts) {
        this.gifts = gifts;
    }

    /** API-ADM-14：礼物列表。 */
    @GetMapping
    public ApiResponse<PageResult<GiftRow>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        List<GiftRow> rows = gifts.list().stream().map(AdminGiftController::toRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    /** API-ADM-15：新建礼物（默认在售）。 */
    @PostMapping
    public ApiResponse<GiftRow> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        GiftAdminItem item = gifts.create(body.name(), body.priceCoin(), body.effectType(),
                AdminActor.operator(request));
        return ApiResponse.ok(toRow(item));
    }

    /** API-ADM-16：局部更新（{@code null} 字段保持不变）。 */
    @PatchMapping("/{giftId}")
    public ApiResponse<GiftRow> update(@PathVariable long giftId,
                                       @RequestBody UpdateRequest body,
                                       HttpServletRequest request) {
        GiftAdminItem item = gifts.update(giftId, body.name(), body.priceCoin(), body.status(),
                AdminActor.operator(request));
        return ApiResponse.ok(toRow(item));
    }

    static GiftRow toRow(GiftAdminItem item) {
        return new GiftRow(item.id(), item.name(), item.priceCoin(), item.effectType(), item.status());
    }

    /** 新建请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateRequest(String name, long priceCoin, String effectType) {
    }

    /** 更新请求体（{@code null} = 不变）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(String name, Long priceCoin, String status) {
    }

    /** 礼物行（前端 {@code GiftRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GiftRow(long id, String name, long priceCoin, String effectType, String status) {
    }
}
