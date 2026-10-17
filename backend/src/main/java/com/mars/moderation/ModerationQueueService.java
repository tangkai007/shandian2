package com.mars.moderation;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.repo.ModerationTaskRepository;
import com.mars.moderation.repo.RoomLookup;
import com.mars.moderation.repo.WebhookEventRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 审核队列服务（契约 §4.10 API-CB-03/04 入队 · §4.9 API-ADM-06/07 队列与认领 · AC-7）。
 *
 * <p><b>幂等</b>：以回调事件 ID 为唯一键（{@code webhook_event} 主键 + {@code moderation_task.event_id}
 * 唯一），同一事件重复投递只入队一次，返回既有任务。<b>分流</b>：置信度 → 风险等级；
 * 队列按风险等级升序（高在前）。
 *
 * <p><b>认领不写审计</b>：S2a mock {@code moderationClaim} 无审计留痕，故此处与之一致。
 */
public final class ModerationQueueService {

    private final ModerationTaskRepository tasks;
    private final WebhookEventRepository webhookEvents;
    private final RoomLookup rooms;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator ids;
    private final Clock clock;

    public ModerationQueueService(ModerationTaskRepository tasks,
                                  WebhookEventRepository webhookEvents,
                                  RoomLookup rooms,
                                  UnitOfWork unitOfWork,
                                  OrderNoGenerator ids,
                                  Clock clock) {
        this.tasks = tasks;
        this.webhookEvents = webhookEvents;
        this.rooms = rooms;
        this.unitOfWork = unitOfWork;
        this.ids = ids;
        this.clock = clock;
    }

    /** 入队结果：{@code taskId} 为任务主键，{@code duplicate} 标识本次是否为重复投递。 */
    public record EnqueueResult(String taskId, boolean duplicate) {
    }

    /** 机审回调入队（幂等）。 */
    public EnqueueResult enqueue(ModerationAlertEvent event) {
        requireEvent(event);
        return unitOfWork.execute(() -> {
            boolean firstTime = webhookEvents.register(event.eventId(), event.source());
            if (!firstTime) {
                // 重复投递：回吐既有任务，不重复入队（§4.10 幂等）。
                return tasks.findByEventId(event.eventId())
                        .map(existing -> new EnqueueResult(existing.taskId(), true))
                        .orElseGet(() -> new EnqueueResult(null, true));
            }
            RoomBrief room = event.roomId() == null ? null : rooms.findById(event.roomId()).orElse(null);
            Long roomNo = event.roomNo() != null ? event.roomNo()
                    : (room == null ? null : room.roomNo());
            Long anchorId = event.anchorId() != null ? event.anchorId()
                    : (room == null ? null : room.anchorId());
            String anchorName = firstNonBlank(event.anchorName(), room == null ? null : room.anchorName());

            ModerationTask task = new ModerationTask(
                    ids.next("MT"), event.eventId(), event.source(), event.roomId(), roomNo,
                    anchorId, anchorName, event.sessionId(), event.riskLevel(), event.alertType(),
                    blankToEmpty(event.reasonSummary()),
                    blankToEmpty(event.snapshotUrl()),
                    blankToEmpty(event.ruleId()),
                    event.confidence(), TaskStatus.PENDING, null, null, now());
            tasks.save(task);
            return new EnqueueResult(task.taskId(), false);
        });
    }

    /**
     * 审核队列（API-ADM-06）：按风险等级升序（高在前）再按入队时间升序。
     *
     * <p>S2a mock {@code moderationTasks} 返回<b>全部状态</b>的任务（含已认领/已处置），故此处取全量。
     */
    public List<ModerationTask> queue() {
        return tasks.listByStatus(null);
    }

    /** 认领（API-ADM-07）：并发抢单失败 → {@code 40900}。 */
    public ModerationTask claim(String taskId, long operatorId, String operatorName) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "task_id 必填", "task_id", "required");
        }
        return unitOfWork.execute(() -> {
            ModerationTask task = tasks.findById(taskId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.RESOURCE_GONE, "审核任务不存在", "task_id", "not_found"));
            if (task.status() == TaskStatus.DONE) {
                throw new BizException(ErrorCode.CONFLICT, "任务已处置", "task_id", "done");
            }
            if (!tasks.claim(taskId, operatorId, operatorName)) {
                throw new BizException(ErrorCode.CONFLICT, "任务已被他人认领", "task_id", "claimed");
            }
            return task.claimedBy(operatorId, operatorName);
        });
    }

    private void requireEvent(ModerationAlertEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "事件 ID 必填", "event_id", "required");
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
