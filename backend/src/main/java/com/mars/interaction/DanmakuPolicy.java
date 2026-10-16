package com.mars.interaction;

import com.mars.common.ErrorCode;
import com.mars.common.ratelimit.SlidingWindowRateLimiter;

import java.util.HashMap;
import java.util.Map;

/**
 * 弹幕发送前校验（契约 §4.5 API-INT-02 · AC-4）。
 *
 * <p><b>限流参数为唯一口径，取自 S1a，UI 不另立、不覆盖、不缓存</b>：
 * {@code 1 条/秒 · 单条 ≤50 字 · 5s 去重}。
 *
 * <p>校验顺序（与契约 §4.5 逐条一致）：
 * <ol>
 *   <li>已绑手机号，否则 {@code 40325}；</li>
 *   <li>非空且 ≤50 个 Unicode 码点，否则 {@code 40001}（{@code field=content}）；</li>
 *   <li>频控 1 条/秒，超频 {@code 42900}；</li>
 *   <li>5s 内相同内容 → <b>去重</b>（非错误，{@code deduped=true}，不投递）；</li>
 *   <li>敏感词 → {@code 40001}（{@code reason=敏感词}）；</li>
 *   <li>本房间被禁言 → {@code 40300}。</li>
 * </ol>
 *
 * <p>说明：第 3 步在后续步骤之前<b>已消费一次配额</b>——被拒的尝试同样计入频控，
 * 这是有意的抗刷设计；不影响任一单项判定的结果。
 *
 * <p>校验通过后由<b>客户端经 IM 通道投递</b>（房间 = 直播场次），UI 做
 * 「本地先回显 + 服务端确认」双段渲染。
 */
public final class DanmakuPolicy {

    /** 单条最大字数（Unicode 码点）。 */
    public static final int MAX_CHARS = 50;
    /** 频控：窗口内最大条数。 */
    public static final int RATE_MAX_PER_WINDOW = 1;
    /** 频控窗口：1 秒。 */
    public static final long RATE_WINDOW_MILLIS = 1_000L;
    /** 去重窗口：5 秒。 */
    public static final long DEDUP_WINDOW_MILLIS = 5_000L;

    private final SlidingWindowRateLimiter limiter =
            new SlidingWindowRateLimiter(RATE_MAX_PER_WINDOW, RATE_WINDOW_MILLIS);
    private final Map<String, DedupEntry> lastByUser = new HashMap<>();

    private record DedupEntry(String content, long atMillis) {
    }

    public DanmakuDecision check(DanmakuRequest request) {
        if (!request.phoneBound()) {
            return DanmakuDecision.reject(ErrorCode.PHONE_NOT_BOUND, "phone not bound");
        }
        String content = request.content() == null ? "" : request.content();
        if (content.isBlank()) {
            return DanmakuDecision.reject(ErrorCode.PARAM_INVALID, "content is blank");
        }
        if (content.codePointCount(0, content.length()) > MAX_CHARS) {
            return DanmakuDecision.reject(ErrorCode.PARAM_INVALID, "content exceeds " + MAX_CHARS + " chars");
        }
        if (!limiter.tryAcquire(request.userId(), request.nowMillis())) {
            return DanmakuDecision.reject(ErrorCode.RATE_LIMITED, "danmaku rate limited (1/s)");
        }
        DedupEntry last = lastByUser.get(request.userId());
        if (last != null && last.content().equals(content)
                && request.nowMillis() - last.atMillis() < DEDUP_WINDOW_MILLIS) {
            return DanmakuDecision.deduped();
        }
        if (request.sensitive()) {
            return DanmakuDecision.reject(ErrorCode.PARAM_INVALID, "sensitive word");
        }
        if (request.muted()) {
            return DanmakuDecision.reject(ErrorCode.FORBIDDEN, "muted in this room");
        }
        lastByUser.put(request.userId(), new DedupEntry(content, request.nowMillis()));
        return DanmakuDecision.pass();
    }
}
