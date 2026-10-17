package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.moderation.AlertType;
import com.mars.moderation.ModerationAlertEvent;
import com.mars.moderation.ModerationQueueService;
import com.mars.moderation.RecordingCompletedEvent;
import com.mars.moderation.ReplayRecord;
import com.mars.moderation.ReplayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 第三方机审 / 录制回调（契约 §4.10 · AC-7 / AC-9）。
 *
 * <p>路径在 {@code /webhook/**}（不参与 {@code /api/v1} 版本前缀），<b>不经 RBAC / 幂等拦截器</b>：
 * 鉴权与幂等由回调自身承担（事件 ID 唯一约束，见 {@code webhook_event}）。统一返回
 * {@code 200 {code:0}}，业务处理同步完成（一期数据量小，无需异步队列）。
 *
 * <ul>
 *   <li>{@code POST /webhook/css/image-audit} 截图审核 → 审核任务（风险分流）；</li>
 *   <li>{@code POST /webhook/cms/callback} 文本 / 图片审核 → 审核任务；</li>
 *   <li>{@code POST /webhook/css/record} 录制完成 → 回放记录。</li>
 * </ul>
 *
 * <p><b>入参形态</b>：腾讯云 CSS / CMS 原始回调体字段由配置决定，本增量以「平台自有字段」定义
 * 回调映射（见各请求记录），真实密钥与字段映射待接入腾讯云后按需适配（登记于实现说明）。
 */
@RestController
@RequestMapping("/webhook")
public class ModerationWebhookController {

    private final ModerationQueueService moderationQueue;
    private final ReplayService replayService;

    public ModerationWebhookController(ModerationQueueService moderationQueue,
                                       ReplayService replayService) {
        this.moderationQueue = moderationQueue;
        this.replayService = replayService;
    }

    /** API-CB-01/03：CSS 截图审核回调 → 审核任务。 */
    @PostMapping("/css/image-audit")
    public ApiResponse<AlertAck> imageAudit(@RequestBody AlertCallbackRequest body) {
        return ApiResponse.ok(enqueue("CSS", body));
    }

    /** API-CB-04：CMS 文本 / 图片审核回调 → 审核任务。 */
    @PostMapping("/cms/callback")
    public ApiResponse<AlertAck> cmsCallback(@RequestBody AlertCallbackRequest body) {
        return ApiResponse.ok(enqueue("CMS", body));
    }

    /** API-CB-02：CSS 录制完成回调 → 回放记录（{@code expire_at = ended_at + 留存天数}）。 */
    @PostMapping("/css/record")
    public ApiResponse<RecordAck> recordCompleted(@RequestBody RecordCallbackRequest body) {
        ReplayRecord replay = replayService.onRecordingCompleted(new RecordingCompletedEvent(
                body.eventId(), body.roomId() == null ? 0L : body.roomId(), body.anchorId(),
                body.sessionId(), body.vodFileId(), body.playUrl(),
                body.durationSec() == null ? 0 : body.durationSec(),
                body.startedAt(), body.endedAt()));
        return ApiResponse.ok(new RecordAck(replay.replayId(), replay.status().name()));
    }

    private AlertAck enqueue(String source, AlertCallbackRequest body) {
        ModerationAlertEvent event = new ModerationAlertEvent(
                body.eventId(),
                source,
                body.roomId(),
                body.roomNo(),
                body.anchorId(),
                body.anchorName(),
                body.sessionId(),
                AlertType.fromName(body.alertType()),
                body.reasonSummary(),
                body.snapshotUrl(),
                body.ruleId(),
                body.confidence() == null ? 0 : body.confidence(),
                body.occurredAt());
        ModerationQueueService.EnqueueResult result = moderationQueue.enqueue(event);
        return new AlertAck(result.taskId(), result.duplicate());
    }

    /** 机审回调请求体（截图 / 文本审核共用）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AlertCallbackRequest(
            String eventId,
            Long roomId,
            Long roomNo,
            Long anchorId,
            String anchorName,
            Long sessionId,
            /** {@code IMAGE / TEXT / AUDIO}。 */
            String alertType,
            String reasonSummary,
            String snapshotUrl,
            String ruleId,
            /** 置信度 0–100，映射风险等级（≥80 高 / ≥50 中 / 其余低）。 */
            Integer confidence,
            LocalDateTime occurredAt) {
    }

    /** 机审回调应答：{@code duplicate=true} 表示重复投递（幂等命中，未重复入队）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AlertAck(String taskId, boolean duplicate) {
    }

    /** 录制完成回调请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecordCallbackRequest(
            String eventId,
            Long roomId,
            Long anchorId,
            Long sessionId,
            String vodFileId,
            String playUrl,
            Integer durationSec,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
    }

    /** 录制回调应答。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RecordAck(String replayId, String status) {
    }
}
