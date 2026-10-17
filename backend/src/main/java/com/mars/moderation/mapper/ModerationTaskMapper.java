package com.mars.moderation.mapper;

import com.mars.moderation.ModerationTask;
import com.mars.moderation.TaskStatus;
import com.mars.moderation.repo.ModerationTaskRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 审核任务 Mapper（表 {@code moderation_task}）。
 *
 * <p>记录型（record）结果按<b>构造参数顺序</b>由 MyBatis 自动装配，故 SELECT 的列顺序与
 * {@link ModerationTask} 组件顺序严格一致（同 {@code RechargeOrderMapper} 的既有约定）。
 * 队列排序在 SQL 内完成（{@code HIGH→MEDIUM→LOW} 再按入队时间）。
 */
@Mapper
public interface ModerationTaskMapper extends ModerationTaskRepository {

    String COLUMNS = "task_id AS taskId, event_id AS eventId, source AS source, "
            + "room_id AS roomId, room_no AS roomNo, anchor_id AS anchorId, "
            + "anchor_name AS anchorName, session_id AS sessionId, risk_level AS riskLevel, "
            + "alert_type AS alertType, reason_summary AS reasonSummary, "
            + "snapshot_url AS snapshotUrl, rule_id AS ruleId, confidence AS confidence, "
            + "status AS status, claimed_by AS claimedBy, claimed_by_name AS claimedByName, "
            + "created_at AS createdAt";

    @Insert("INSERT INTO moderation_task (task_id, event_id, source, room_id, room_no, anchor_id, "
            + "anchor_name, session_id, risk_level, alert_type, reason_summary, snapshot_url, "
            + "rule_id, confidence, status, claimed_by, claimed_by_name, created_at) "
            + "VALUES (#{taskId}, #{eventId}, #{source}, #{roomId}, #{roomNo}, #{anchorId}, "
            + "#{anchorName}, #{sessionId}, #{riskLevel}, #{alertType}, #{reasonSummary}, "
            + "#{snapshotUrl}, #{ruleId}, #{confidence}, #{status}, #{claimedBy}, "
            + "#{claimedByName}, #{createdAt}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), claimed_by = VALUES(claimed_by), "
            + "claimed_by_name = VALUES(claimed_by_name)")
    int upsert(ModerationTask task);

    @Override
    default void save(ModerationTask task) {
        upsert(task);
    }

    @Override
    @Select("SELECT " + COLUMNS + " FROM moderation_task WHERE task_id = #{taskId}")
    Optional<ModerationTask> findById(String taskId);

    @Override
    @Select("SELECT " + COLUMNS + " FROM moderation_task WHERE event_id = #{eventId}")
    Optional<ModerationTask> findByEventId(String eventId);

    @Override
    @Select("<script>SELECT " + COLUMNS + " FROM moderation_task "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY FIELD(risk_level, 'HIGH', 'MEDIUM', 'LOW'), created_at ASC</script>")
    List<ModerationTask> listByStatus(@Param("status") TaskStatus status);

    /** 条件置位：仅 {@code PENDING} → {@code CLAIMED} 才生效（防并发抢单）。 */
    @Update("UPDATE moderation_task SET status = 'CLAIMED', claimed_by = #{operatorId}, "
            + "claimed_by_name = #{operatorName} "
            + "WHERE task_id = #{taskId} AND status = 'PENDING'")
    int claimRow(@Param("taskId") String taskId,
                 @Param("operatorId") long operatorId,
                 @Param("operatorName") String operatorName);

    @Override
    default boolean claim(String taskId, long operatorId, String operatorName) {
        return claimRow(taskId, operatorId, operatorName) > 0;
    }
}
