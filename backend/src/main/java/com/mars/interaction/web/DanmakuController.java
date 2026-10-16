package com.mars.interaction.web;

import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AuthContext;
import com.mars.common.security.RequireRoles;
import com.mars.interaction.DanmakuDecision;
import com.mars.interaction.DanmakuRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/**
 * 弹幕发送前校验（契约 §4.5 API-INT-02 · <b>AC-4</b>）。
 *
 * <p>限流唯一口径：<b>1 条/秒 · ≤50 字 · 5s 去重</b>（S1a）。校验通过后由客户端经 IM 通道投递。
 *
 * <p><b>已知装配缺口（见实现说明）</b>：
 * <ul>
 *   <li>敏感词命中当前以 {@code sensitive=false} 传入 —— <b>词库校验未接入</b>，
 *       在接入前本端点<b>不得对外正式开放</b>（合规风险）；</li>
 *   <li>禁言状态 {@code muted=false} —— 待 {@code room_mute} 查询接入。</li>
 * </ul>
 * 即：限流与去重（本轮已实现并验证）可用，内容安全相关两个入参为待接入项。
 */
@RestController
@RequestMapping("/api/v1/live/rooms")
public class DanmakuController {

    private final RoomDanmakuPolicies policies;
    private final Clock clock;

    public DanmakuController(RoomDanmakuPolicies policies, Clock clock) {
        this.policies = policies;
        this.clock = clock;
    }

    public record DanmakuSendRequest(@NotBlank String content, String clientMsgId) {
    }

    public record DanmakuResponse(boolean pass, boolean deduped, String traceHint) {
    }

    @PostMapping("/{roomId}/danmaku")
    @RequireRoles({"VIEWER", "ANCHOR"})
    public ApiResponse<DanmakuResponse> send(@PathVariable long roomId,
                                            @Valid @RequestBody DanmakuSendRequest request) {
        long userId = AuthContext.requireUserId();
        // 三渠道强制补绑手机号（DECISION-3/4）→ 已登录即视为已绑手机号，满足发言门槛
        boolean phoneBound = true;
        boolean muted = false;      // TODO(interaction): 查询 room_mute（含 expire_at）
        boolean sensitive = false;  // TODO(interaction): 接入敏感词库（合规上线前置项）

        DanmakuRequest domainRequest = new DanmakuRequest(
                String.valueOf(userId), request.content(), phoneBound, muted, sensitive,
                clock.millis());
        DanmakuDecision decision = policies.forRoom(roomId).check(domainRequest);

        if (decision.outcome() == DanmakuDecision.Outcome.REJECTED) {
            throw new BizException(decision.errorCode(), decision.message(),
                    "content", decision.errorCode().name());
        }
        return ApiResponse.ok(new DanmakuResponse(
                decision.outcome() == DanmakuDecision.Outcome.PASS,
                decision.outcome() == DanmakuDecision.Outcome.DEDUPED,
                Map.of("limits", "1/s, <=50 chars, 5s dedup").toString()));
    }
}
