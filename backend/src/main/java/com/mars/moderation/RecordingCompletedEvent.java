package com.mars.moderation;

import java.time.LocalDateTime;

/**
 * 录制完成事件（入参，契约 §4.10 API-CB-02 `POST /webhook/css/record`）。
 *
 * <p>{@code vodFileId} 为幂等键（唯一）；{@code endedAt} 为留存起算点。
 */
public record RecordingCompletedEvent(
        String eventId,
        long roomId,
        Long anchorId,
        Long sessionId,
        String vodFileId,
        String playUrl,
        int durationSec,
        LocalDateTime startedAt,
        LocalDateTime endedAt) {
}
