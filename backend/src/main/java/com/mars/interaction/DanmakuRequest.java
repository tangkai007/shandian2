package com.mars.interaction;

/**
 * 弹幕发送前校验的入参（契约 §4.5 API-INT-02）。
 *
 * @param userId      发送者
 * @param content     弹幕正文
 * @param phoneBound  是否已绑定手机号（BR-1 / OP-14 发言门槛）
 * @param muted       是否处于本房间禁言态（房管处置）
 * @param sensitive   是否命中敏感词（服务端词库判定后传入）
 * @param nowMillis   服务端时间（毫秒，UTC 基准），用于限流与去重窗口
 */
public record DanmakuRequest(
        String userId,
        String content,
        boolean phoneBound,
        boolean muted,
        boolean sensitive,
        long nowMillis) {
}
