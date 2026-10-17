package com.mars.moderation.repo;

import com.mars.moderation.ModerationTask;
import com.mars.moderation.TaskStatus;

import java.util.List;
import java.util.Optional;

/** 审核任务仓储（表 {@code moderation_task}）。 */
public interface ModerationTaskRepository {

    void save(ModerationTask task);

    Optional<ModerationTask> findById(String taskId);

    /** 按回调事件 ID 查（幂等回吐）。 */
    Optional<ModerationTask> findByEventId(String eventId);

    /**
     * 队列：默认取 {@code PENDING}，按 {@code risk_level} 升序（高在前）再按入队时间升序。
     * 传 {@code null} 取全部状态。
     */
    List<ModerationTask> listByStatus(TaskStatus status);

    /** 认领：仅当当前为 PRECEDING 状态时置 CLAIMED；返回是否成功（防并发抢单）。 */
    boolean claim(String taskId, long operatorId, String operatorName);
}
