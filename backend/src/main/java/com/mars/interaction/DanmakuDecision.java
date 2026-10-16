package com.mars.interaction;

import com.mars.common.ErrorCode;

/**
 * 弹幕校验结果。
 *
 * @param outcome   结果分类
 * @param errorCode 需失败时给出的错误码（{@link Outcome#PASS} / {@link Outcome#DEDUPED} 时为 {@code null}）
 * @param message   面向开发者的说明（用户可见文案由客户端按 S1b §09 取）
 */
public record DanmakuDecision(Outcome outcome, ErrorCode errorCode, String message) {

    public enum Outcome {
        /** 校验通过，可经 IM 通道投递。 */
        PASS,
        /** 5s 内重复内容：去重、不投递，但<b>不是错误</b>（HTTP 200 + {@code deduped=true}）。 */
        DEDUPED,
        /** 被拒绝，按 {@link #errorCode} 返回。 */
        REJECTED
    }

    public static DanmakuDecision pass() {
        return new DanmakuDecision(Outcome.PASS, null, "ok");
    }

    public static DanmakuDecision deduped() {
        return new DanmakuDecision(Outcome.DEDUPED, null, "duplicated within 5s window");
    }

    public static DanmakuDecision reject(ErrorCode code, String message) {
        return new DanmakuDecision(Outcome.REJECTED, code, message);
    }

    public boolean accepted() {
        return outcome == Outcome.PASS;
    }

    public boolean delivered() {
        return outcome == Outcome.PASS;
    }
}
