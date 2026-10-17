package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.moderation.ReplayRecord;
import com.mars.moderation.ReplayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回放（契约 §4.8 · <b>AC-9</b> · OP-13）。
 *
 * <p>API-REP-01 房间回放列表 / API-REP-02 取回放播放地址。契约标注为「公开/用户」，
 * 观看回放无需登录，故本控制器<b>不加角色注解</b>（匿名可读）；播放地址过期或已下架 →
 * {@code 40330}（语义：资源已结束或不存在），由 {@link ReplayService} 抛出。
 *
 * <p>字段名一律 <b>snake_case</b>（契约 §4.8 冻结）。
 */
@RestController
@RequestMapping("/api/v1")
public class ReplayController {

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    /** API-REP-01：房间回放列表（按开播时间倒序，过期项回显 {@code EXPIRED} 而非 404 空白）。 */
    @GetMapping("/live/rooms/{roomId}/replays")
    public ApiResponse<ReplayList> listByRoom(@PathVariable long roomId) {
        List<ReplayRow> rows = replayService.listByRoom(roomId).stream().map(ReplayRow::of).toList();
        return ApiResponse.ok(new ReplayList(rows));
    }

    /** API-REP-02：取回放播放地址（{@code protocol=hls}；过期/下架 → {@code 40330}）。 */
    @GetMapping("/replays/{replayId}/play-url")
    public ApiResponse<PlayUrl> playUrl(@PathVariable String replayId) {
        ReplayService.PlayInfo info = replayService.playUrl(replayId);
        return ApiResponse.ok(new PlayUrl(info.protocol(), info.url(), info.expireAt()));
    }

    /** API-REP-01 响应体（契约外层为 {@code { "list": [...] }}，非分页信封）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReplayList(List<ReplayRow> list) {
    }

    /** 回放行（契约 §4.8 API-REP-01）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReplayRow(String replayId,
                            Long sessionId,
                            String playUrl,
                            int duration,
                            LocalDateTime expireAt,
                            String status) {

        static ReplayRow of(ReplayRecord replay) {
            boolean playable = replay.status() == ReplayRecord.ReplayStatus.READY;
            return new ReplayRow(replay.replayId(), replay.sessionId(),
                    playable ? replay.playUrl() : "", replay.durationSec(),
                    replay.expireAt(), replay.status().name());
        }
    }

    /** API-REP-02 响应体（契约 §4.8）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PlayUrl(String protocol, String url, LocalDateTime expireAt) {
    }
}
