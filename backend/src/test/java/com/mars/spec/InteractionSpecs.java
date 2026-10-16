package com.mars.spec;

import com.mars.common.ErrorCode;
import com.mars.interaction.DanmakuDecision;
import com.mars.interaction.DanmakuPolicy;
import com.mars.interaction.DanmakuRequest;
import com.mars.testkit.Registry;

/**
 * 弹幕 spec（AC-4）。限流参数为 S1a 唯一口径：<b>1 条/秒 · ≤50 字 · 5s 去重</b>。
 */
public final class InteractionSpecs {

    private InteractionSpecs() {
    }

    private static DanmakuRequest req(String user, String content, boolean phoneBound,
                                     boolean muted, boolean sensitive, long now) {
        return new DanmakuRequest(user, content, phoneBound, muted, sensitive, now);
    }

    public static void register(Registry registry) {

        registry.add("AC-4 · 弹幕限流参数取自 S1a 唯一口径（防第二套数值）", a -> {
            a.eqLong(DanmakuPolicy.MAX_CHARS, 50L, "单条 ≤50 字");
            a.eqLong(DanmakuPolicy.RATE_MAX_PER_WINDOW, 1L, "1 条/秒：窗口内 1 条");
            a.eqLong(DanmakuPolicy.RATE_WINDOW_MILLIS, 1_000L, "频控窗口 1 秒");
            a.eqLong(DanmakuPolicy.DEDUP_WINDOW_MILLIS, 5_000L, "去重窗口 5 秒");
        });

        registry.add("AC-4 · 正常发送通过与字数边界（50 通过 / 51 拒绝）", a -> {
            DanmakuPolicy policy = new DanmakuPolicy();
            DanmakuDecision ok = policy.check(req("u-pass", "大家好", true, false, false, 1_000L));
            a.isTrue(ok.accepted(), "正常弹幕通过");
            a.isNull(ok.errorCode(), "通过时无错误码");

            DanmakuPolicy boundary = new DanmakuPolicy();
            DanmakuDecision exactly50 = boundary.check(req("u-50", "字".repeat(50), true, false, false, 1_000L));
            a.isTrue(exactly50.accepted(), "恰好 50 字通过（Unicode 码点计）");

            DanmakuPolicy over = new DanmakuPolicy();
            DanmakuDecision tooLong = over.check(req("u-51", "字".repeat(51), true, false, false, 1_000L));
            a.isTrue(tooLong.outcome() == DanmakuDecision.Outcome.REJECTED, "51 字被拒");
            a.eq(tooLong.errorCode(), ErrorCode.PARAM_INVALID, "超长 → 40001");
            a.isFalse(tooLong.delivered(), "超长不投递");

            DanmakuPolicy blank = new DanmakuPolicy();
            a.eq(blank.check(req("u-blank", "   ", true, false, false, 1_000L)).errorCode(),
                    ErrorCode.PARAM_INVALID, "空白内容 → 40001");
        });

        registry.add("AC-4 · 频控 1 条/秒：窗口内拒绝 42900，1s 后放行", a -> {
            DanmakuPolicy policy = new DanmakuPolicy();
            String user = "u-rate";

            DanmakuDecision first = policy.check(req(user, "第一条", true, false, false, 1_000L));
            a.isTrue(first.accepted(), "首条通过");

            DanmakuDecision tooFast = policy.check(req(user, "第二条", true, false, false, 1_500L));
            a.isTrue(tooFast.outcome() == DanmakuDecision.Outcome.REJECTED, "0.5s 内第二条被拒");
            a.eq(tooFast.errorCode(), ErrorCode.RATE_LIMITED, "超频 → 42900");
            a.eqLong(tooFast.errorCode().httpStatus(), 429L, "42900 → HTTP 429");

            DanmakuDecision afterWindow = policy.check(req(user, "第三条", true, false, false, 2_500L));
            a.isTrue(afterWindow.accepted(), "满 1s 后放行");
        });

        registry.add("AC-4 · 5s 去重：重复内容 DEDUPED 且不投递，窗口外放行", a -> {
            DanmakuPolicy policy = new DanmakuPolicy();
            String user = "u-dedup";

            a.isTrue(policy.check(req(user, "重复内容", true, false, false, 1_000L)).accepted(), "首条通过");

            DanmakuDecision duplicated = policy.check(req(user, "重复内容", true, false, false, 2_500L));
            a.isTrue(duplicated.outcome() == DanmakuDecision.Outcome.DEDUPED, "5s 内重复 → 去重");
            a.isNull(duplicated.errorCode(), "去重不是错误（无错误码）");
            a.isFalse(duplicated.delivered(), "去重内容不投递");

            DanmakuDecision afterDedupWindow = policy.check(req(user, "重复内容", true, false, false, 7_000L));
            a.isTrue(afterDedupWindow.accepted(), "超过 5s 后相同内容放行");

            DanmakuPolicy differentContent = new DanmakuPolicy();
            differentContent.check(req(user, "A", true, false, false, 1_000L));
            a.isTrue(differentContent.check(req(user, "B", true, false, false, 2_500L)).accepted(),
                    "同用户不同内容不去重");
        });

        registry.add("AC-4 · 发言门槛：未绑手机号 → 40325（与 40100 边界分离）", a -> {
            DanmakuPolicy policy = new DanmakuPolicy();
            DanmakuDecision unbound = policy.check(req("u-unbound", "你好", false, false, false, 1_000L));
            a.isTrue(unbound.outcome() == DanmakuDecision.Outcome.REJECTED, "未绑手机号被拒");
            a.eq(unbound.errorCode(), ErrorCode.PHONE_NOT_BOUND, "未绑手机号 → 40325");
            a.isFalse(unbound.errorCode() == ErrorCode.UNAUTHENTICATED, "不得复用未登录码 40100");
        });

        registry.add("AC-4 · 禁言 → 40300；敏感词 → 40001（复用既不新增码位）", a -> {
            DanmakuPolicy mutedPolicy = new DanmakuPolicy();
            DanmakuDecision muted = mutedPolicy.check(req("u-muted", "你好", true, true, false, 1_000L));
            a.eq(muted.errorCode(), ErrorCode.FORBIDDEN, "被禁言 → 40300");

            DanmakuPolicy sensitivePolicy = new DanmakuPolicy();
            DanmakuDecision sensitive = sensitivePolicy.check(req("u-sensitive", "审核词", true, false, true, 1_000L));
            a.eq(sensitive.errorCode(), ErrorCode.PARAM_INVALID, "敏感词 → 40001（契约 §2.7 备注）");
            a.isFalse(sensitive.delivered(), "敏感词不投递");
        });
    }
}
