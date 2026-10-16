package com.mars.interaction.web;

import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.security.AuthContext;
import com.mars.common.security.RequireRoles;
import com.mars.interaction.DanmakuDecision;
import com.mars.interaction.service.DanmakuGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 弹幕发送前校验（契约 §4.5 API-INT-02 · <b>AC-4</b>）。
 *
 * <p>限流唯一口径：<b>1 条/秒 · ≤50 字 · 5s 去重</b>（S1a）。
 * <b>敏感词与禁言已接入</b>：由 {@link DanmakuGuard} 从词库（内置资源 + 运营配置）与
 * {@code room_mute} 实时取得后交给业务规则判定；校验通过后由客户端经 IM 通道投递
 * （UI 做「本地先回显 + 服务端确认」双段渲染）。
 */
@RestController
@RequestMapping("/api/v1/live/rooms")
public class DanmakuController {

    private final DanmakuGuard guard;

    public DanmakuController(DanmakuGuard guard) {
        this.guard = guard;
    }

    public record DanmakuSendRequest(@NotBlank String content, String clientMsgId) {
    }

    public record DanmakuResponse(boolean pass, boolean deduped) {
    }

    @PostMapping("/{roomId}/danmaku")
    @RequireRoles({"VIEWER", "ANCHOR"})
    public ApiResponse<DanmakuResponse> send(@PathVariable long roomId,
                                            @Valid @RequestBody DanmakuSendRequest request) {
        long userId = AuthContext.requireUserId();
        // 三渠道强制补绑手机号（DECISION-3/4）→ 已登录即视为已绑手机号，满足发言门槛
        boolean phoneBound = true;

        DanmakuDecision decision = guard.check(roomId, userId, phoneBound, request.content());
        if (decision.outcome() == DanmakuDecision.Outcome.REJECTED) {
            throw new BizException(decision.errorCode(), decision.message(),
                    "content", decision.errorCode().name());
        }
        return ApiResponse.ok(new DanmakuResponse(
                decision.outcome() == DanmakuDecision.Outcome.PASS,
                decision.outcome() == DanmakuDecision.Outcome.DEDUPED));
    }
}
