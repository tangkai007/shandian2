package com.mars.moderation;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.repo.ReplayRecordRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 回放服务（AC-9；契约 §4.8 API-REP-01/02 · §4.10 API-CB-02）。
 *
 * <p>录制完成回调落一条回放，<b>留存天数取后台配置</b>：{@code expireAt = endedAt +
 * sys_config.replay_retention_days}（默认 30，运营可改）。过期/下架后取播放地址 → {@code 40330}。
 * 回调以 {@code vod_file_id} 幂等：同一文件重复投递只落一条。
 */
public final class ReplayService {

    private final ReplayRecordRepository replays;
    private final PlatformConfig platformConfig;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator ids;
    private final Clock clock;

    public ReplayService(ReplayRecordRepository replays,
                         PlatformConfig platformConfig,
                         UnitOfWork unitOfWork,
                         OrderNoGenerator ids,
                         Clock clock) {
        this.replays = replays;
        this.platformConfig = platformConfig;
        this.unitOfWork = unitOfWork;
        this.ids = ids;
        this.clock = clock;
    }

    /** 播放地址（API-REP-02 响应负载）。 */
    public record PlayInfo(String protocol, String url, LocalDateTime expireAt) {
    }

    /** 录制完成回调（API-CB-02，幂等）。返回既有或新建的回放记录。 */
    public ReplayRecord onRecordingCompleted(RecordingCompletedEvent event) {
        requireEvent(event);
        return unitOfWork.execute(() -> {
            ReplayRecord existing = replays.findByVodFileId(event.vodFileId()).orElse(null);
            if (existing != null) {
                return existing; // 重复投递：回吐既有记录，不重复落库
            }
            LocalDateTime ended = event.endedAt() == null ? now() : event.endedAt();
            LocalDateTime expireAt = ended.plusDays(platformConfig.replayRetentionDays());
            ReplayRecord record = new ReplayRecord(ids.next("RP"), event.sessionId(), event.roomId(),
                    event.anchorId(), event.vodFileId(), blankTo(event.playUrl(), ""),
                    event.durationSec(), event.startedAt(), event.endedAt(), expireAt,
                    ReplayRecord.ReplayStatus.READY);
            replays.save(record);
            return record;
        });
    }

    /**
     * 房间回放列表（API-REP-01）：按开播时间倒序，<b>读出即判定过期</b>
     * （{@code expireIfDue}），让前端拿到明确的 {@code EXPIRED} 而非空白 404。
     */
    public List<ReplayRecord> listByRoom(long roomId) {
        LocalDateTime now = now();
        List<ReplayRecord> result = new ArrayList<>();
        for (ReplayRecord replay : replays.listByRoom(roomId)) {
            result.add(replay.expireIfDue(now));
        }
        result.sort((left, right) -> nullSafe(right.startedAt()).compareTo(nullSafe(left.startedAt())));
        return result;
    }

    /** 取播放地址（API-REP-02）：过期或已下架 → {@code 40330}。 */
    public PlayInfo playUrl(String replayId) {
        LocalDateTime now = now();
        ReplayRecord replay = replays.findById(replayId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_GONE, "回放不存在或已过期", "replay_id", "not_found"))
                .expireIfDue(now);
        if (!replay.playableAt(now)) {
            throw new BizException(ErrorCode.RESOURCE_GONE, "回放不存在或已过期", "replay_id", "expired");
        }
        return new PlayInfo("hls", replay.playUrl(), replay.expireAt());
    }

    private void requireEvent(RecordingCompletedEvent event) {
        if (event == null || event.vodFileId() == null || event.vodFileId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "录制文件 ID 必填", "vod_file_id", "required");
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LocalDateTime nullSafe(LocalDateTime value) {
        return value == null ? LocalDateTime.MIN : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
