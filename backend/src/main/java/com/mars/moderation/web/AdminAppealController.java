package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.Appeal;
import com.mars.moderation.AppealService;
import com.mars.moderation.ModerationAction;
import com.mars.moderation.ModerationActionType;
import com.mars.moderation.RoomBrief;
import com.mars.moderation.repo.ModerationActionRepository;
import com.mars.moderation.repo.RoomLookup;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台申诉处理（契约 §4.9 · API-ADM-12 / 13 · BR-8 / OP-17）。
 *
 * <p>角色 {@code OPS}（与 S2a mock 守卫一致）。申诉时限取自后台配置，
 * <b>不硬编码 7 天</b>（{@code sys_config.appeal_days}）。
 *
 * <p>{@code target_label} / {@code remark} 不在 {@code appeal} 表内，需回查原处置与其所属房间。
 */
@RestController
@RequestMapping("/api/v1/admin/appeals")
@RequireRoles({"OPS"})
public class AdminAppealController {

    private final AppealService appealService;
    private final ModerationActionRepository actions;
    private final RoomLookup rooms;

    public AdminAppealController(AppealService appealService,
                                 ModerationActionRepository actions,
                                 RoomLookup rooms) {
        this.appealService = appealService;
        this.actions = actions;
        this.rooms = rooms;
    }

    /** API-ADM-12：申诉队列（按创建时间升序）。 */
    @GetMapping
    public ApiResponse<PageResult<AppealRow>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        List<AppealRow> rows = appealService.queue(null).stream().map(this::toRow).toList();
        return ApiResponse.ok(AdminPaging.of(rows, page, size));
    }

    /** API-ADM-13：处理申诉（{@code ACCEPT} → 解除原处置；否则维持，{@code 40900} 重复处理）。 */
    @PostMapping("/{appealId}/handle")
    public ApiResponse<AppealRow> handle(@PathVariable String appealId,
                                         @RequestBody HandleRequest body,
                                         HttpServletRequest request) {
        Appeal handled = appealService.handle(appealId, body.decision(), body.remark(),
                AdminActor.id(), AdminActor.name(), AdminActor.role(), AdminActor.ip(request));
        return ApiResponse.ok(toRow(handled));
    }

    private AppealRow toRow(Appeal appeal) {
        ModerationAction action = actions.findById(appeal.actionId()).orElse(null);
        String targetLabel = null;
        String remark = "";
        if (action != null) {
            remark = action.remark() == null ? "" : action.remark();
            if (action.roomId() != null) {
                RoomBrief room = rooms.findById(action.roomId()).orElse(null);
                if (room != null) {
                    targetLabel = room.targetLabel();
                }
            }
        }
        if (targetLabel == null) {
            targetLabel = "处置 " + appeal.actionId();
        }
        return new AppealRow(appeal.appealId(), appeal.actionId(), appeal.actionType(), targetLabel,
                appeal.reasonCode(), remark, appeal.actionTime(), appeal.deadlineAt(),
                appeal.status(), appeal.dispositionEffective());
    }

    /** 申诉处理请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HandleRequest(String decision, String remark) {
    }

    /** 申诉行（S2a §4.9 API-ADM-12）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AppealRow(String appealId,
                            String actionId,
                            ModerationActionType actionType,
                            String targetLabel,
                            String reasonCode,
                            String remark,
                            LocalDateTime actionTime,
                            LocalDateTime deadlineAt,
                            Appeal.AppealStatus status,
                            boolean dispositionEffective) {
    }
}
