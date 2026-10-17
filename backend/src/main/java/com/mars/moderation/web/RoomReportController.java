package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.security.AuthContext;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.ReportService;
import com.mars.moderation.RoomBrief;
import com.mars.moderation.repo.RoomLookup;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户举报直播间（契约 §4.5 · API-INT-09 · FR-9.4 · AC-7）。
 *
 * <p>举报进审核队列（{@code PENDING}），供后台审核工作台处理（API-ADM-10/11）。
 * 举报人取自服务端鉴权上下文（{@link AuthContext}），不信任前端传参；目标标签
 * 由 {@link RoomLookup} 联表补全（{@code <主播>（房号 <room_no>）}），查不到时回落房号。
 *
 * <p>字段名一律 <b>snake_case</b>（契约 §4.5 冻结：{@code reason_type/desc/images}）。
 */
@RestController
@RequestMapping("/api/v1")
@RequireRoles({"VIEWER", "ANCHOR"})
public class RoomReportController {

    private final ReportService reportService;
    private final RoomLookup rooms;

    public RoomReportController(ReportService reportService, RoomLookup rooms) {
        this.reportService = reportService;
        this.rooms = rooms;
    }

    /** API-INT-09：提交举报 → {@code { "report_id": "" }}。 */
    @PostMapping("/live/rooms/{roomId}/report")
    public ApiResponse<ReportAck> report(@PathVariable long roomId,
                                         @RequestBody ReportRequest body) {
        String targetLabel = rooms.findById(roomId)
                .map(RoomBrief::targetLabel)
                .orElse("房间 " + roomId);
        String reportId = reportService.file(AuthContext.requireUserId(), "ROOM",
                String.valueOf(roomId), targetLabel, roomId,
                body.reasonType(), body.desc(), body.images());
        return ApiResponse.ok(new ReportAck(reportId));
    }

    /** 举报请求体（契约 §4.5 API-INT-09）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReportRequest(String reasonType, String desc, List<String> images) {
    }

    /** 举报应答（契约 §4.5）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReportAck(String reportId) {
    }
}
