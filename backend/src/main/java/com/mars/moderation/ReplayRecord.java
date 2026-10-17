package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 回放记录（AC-9；契约 §4.8 API-REP-01/02 · §4.10 API-CB-02）。
 *
 * <p>{@code expireAt = endedAt + replay_retention_days}（留存天数取自 {@code sys_config}，
 * 默认 30，后台可改）；过期后取播放地址 → {@code 40330}。
 */
public record ReplayRecord(
        String replayId,
        Long sessionId,
        long roomId,
        Long anchorId,
        String vodFileId,
        String playUrl,
        int durationSec,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime expireAt,
        ReplayStatus status) {

    /** 回放状态（契约 §4.8 API-REP-01 冻结值：{@code READY|EXPIRED|REMOVED}）。 */
    public enum ReplayStatus {
        READY,
        EXPIRED,
        REMOVED
    }

    /** 在给定时刻是否仍可播放（未下架且未过期）。 */
    public boolean playableAt(LocalDateTime now) {
        return status == ReplayStatus.READY && (expireAt == null || now.isBefore(expireAt));
    }

    /** 到期判定：已过期 → {@code EXPIRED}（供列表展示明确状态而非 404 空白）。 */
    public ReplayRecord expireIfDue(LocalDateTime now) {
        if (status == ReplayStatus.READY && expireAt != null && !now.isBefore(expireAt)) {
            return expire();
        }
        return this;
    }

    public ReplayRecord expire() {
        return new ReplayRecord(replayId, sessionId, roomId, anchorId, vodFileId, playUrl,
                durationSec, startedAt, endedAt, expireAt, ReplayStatus.EXPIRED);
    }
}
