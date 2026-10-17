package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.ModerationActionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台房间管理（契约 §4.9 · API-ADM-05 强制关播）。
 *
 * <p>高危不可逆：必填原因码；二次确认由前端保证。角色 {@code OPS / AUDITOR}（与 S2a mock 一致）。
 */
@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequireRoles({"OPS", "AUDITOR"})
public class AdminRoomController {

    private final ModerationActionService actionService;

    public AdminRoomController(ModerationActionService actionService) {
        this.actionService = actionService;
    }

    /** API-ADM-05：强制关播 → 写 {@code CUT} 留痕 + 审计，返回处置 ID 与房间新状态。 */
    @PostMapping("/{roomId}/force-stop")
    public ApiResponse<ForceStopAck> forceStop(@PathVariable long roomId,
                                               @RequestBody ForceStopRequest body,
                                               HttpServletRequest request) {
        ModerationActionService.ForceStopResult result = actionService.forceStop(
                roomId, null, body.reasonCode(), body.remark(),
                AdminActor.id(), AdminActor.name(), AdminActor.role(), AdminActor.ip(request));
        return ApiResponse.ok(new ForceStopAck(result.actionId(), result.status()));
    }

    /** 强制关播请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ForceStopRequest(String reasonCode, String remark) {
    }

    /** 强制关播应答（S2a mock：{@code {action_id, status}}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ForceStopAck(String actionId, String status) {
    }
}
