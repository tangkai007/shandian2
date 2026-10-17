package com.mars.admin.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.admin.model.AdminRoomView;
import com.mars.admin.service.AdminRoomService;
import com.mars.common.ApiResponse;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台巡房房间列表（契约 §4.9 API-ADM-04 · 前端 {@code AdminRoomRow}）。
 *
 * <p>与 {@code com.mars.moderation.web.AdminRoomController}（{@code POST /{roomId}/force-stop}）
 * 共享基路径，方法映射互不重叠。角色 {@code OPS / AUDITOR}（与 S2a mock 一致）。
 *
 * <p>{@code locked_by} / {@code locked_by_name} 消费 {@link AdminRoomView}（房间级锁聚合为任务级认领）。
 */
@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequireRoles({"OPS", "AUDITOR"})
public class AdminRoomListController {

    private final AdminRoomService rooms;

    public AdminRoomListController(AdminRoomService rooms) {
        this.rooms = rooms;
    }

    /** API-ADM-04：房间列表（含锁与告警）。 */
    @GetMapping
    public ApiResponse<PageResult<AdminRoomRow>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        List<AdminRoomRow> rows = rooms.list().stream().map(AdminRoomListController::toRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    static AdminRoomRow toRow(AdminRoomView view) {
        long roomNo = view.roomNo() == null ? view.roomId() : view.roomNo();
        return new AdminRoomRow(
                String.valueOf(view.roomId()),
                roomNo,
                view.anchorName(),
                view.categoryName(),
                view.status(),
                view.viewerCnt(),
                view.riskScore(),
                view.lockedBy() == null ? null : String.valueOf(view.lockedBy()),
                view.lockedByName(),
                view.alerts().stream().map(AdminRoomListController::toAlert).toList());
    }

    static ModerationAlertRow toAlert(AdminRoomView.Alert alert) {
        return new ModerationAlertRow(alert.alertId(), alert.type(), alert.summary(), alert.confidence(),
                alert.ruleId(), alert.occurredAt(), alert.snapshotUrl());
    }

    /** 告警条目（前端 {@code ModerationAlert}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ModerationAlertRow(String alertId,
                                     String type,
                                     String summary,
                                     int confidence,
                                     String ruleId,
                                     LocalDateTime occurredAt,
                                     String snapshotUrl) {
    }

    /** 房间行（S2a §4.9 API-ADM-04）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdminRoomRow(String roomId,
                               long roomNo,
                               String anchorName,
                               String categoryName,
                               String status,
                               int viewerCnt,
                               int riskScore,
                               String lockedBy,
                               String lockedByName,
                               List<ModerationAlertRow> alerts) {
    }
}
