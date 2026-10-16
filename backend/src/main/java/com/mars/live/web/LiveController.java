package com.mars.live.web;

import com.mars.common.ApiResponse;
import com.mars.common.security.RequireRoles;
import com.mars.live.PrecheckItem;
import com.mars.live.PrecheckPolicy;
import com.mars.live.PrecheckResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开播端点（契约 §4.3 · <b>AC-1</b>）。
 *
 * <p>本轮装配 {@code precheck}（逐项校验，未实名 → {@code 40310}）。
 * <p><b>待接入</b>：{@code start}（带防盗链签名与过期时间的 RTMP 地址，需腾讯云 CSS 密钥）、
 * {@code stop}、类目/房间 CRUD、标题封面机审 —— 未接入前不暴露，避免出现语义不完整的接口。
 */
@RestController
@RequestMapping("/api/v1/live/rooms")
public class LiveController {

    private final PrecheckPolicy precheckPolicy = new PrecheckPolicy();

    public record PrecheckResponse(boolean pass, List<PrecheckItem> items,
                                   Integer primaryErrorCode, String primaryItem) {
    }

    public record StartResponse(String pushUrl, String status, String pending) {
    }

    /** 契约 §4.3 API-LIVE-03：开播前置校验（实名 / 类目 / 标题 / 封面）。 */
    @PostMapping("/{roomId}/precheck")
    @RequireRoles({"ANCHOR"})
    public ApiResponse<PrecheckResponse> precheck(@PathVariable long roomId) {
        // TODO(live): 实名取自 user_auth；类目白名单取自 live_category；标题/封面取自机审结果
        boolean realnameVerified = false;   // fail-safe：实名服务接入前一律视为未实名
        boolean categoryAllowed = true;
        boolean titleAuditPass = true;
        boolean coverAuditPass = true;

        PrecheckResult result = precheckPolicy.check(
                realnameVerified, categoryAllowed, titleAuditPass, coverAuditPass);
        String primaryItem = result.items().stream()
                .filter(item -> !item.pass())
                .map(PrecheckItem::key)
                .collect(Collectors.joining(","));
        return ApiResponse.ok(new PrecheckResponse(result.pass(), result.items(),
                result.primaryErrorCode() == null ? null : result.primaryErrorCode().code(),
                primaryItem));
    }

    /** 契约 §4.3 API-LIVE-04：开始直播（推流地址签发）—— <b>待接入 CSS 密钥</b>。 */
    @PostMapping("/{roomId}/start")
    @RequireRoles({"ANCHOR"})
    public ApiResponse<Map<String, Object>> start(@PathVariable long roomId) {
        // TODO(live): 生成带防盗链签名与过期时间的 RTMP 地址；live_session=PREPARING；同时开启录制
        return ApiResponse.ok(Map.of(
                "room_id", roomId,
                "status", "NOT_IMPLEMENTED",
                "pending", "腾讯云 CSS 推流地址签发（需密钥）待接入"));
    }
}
