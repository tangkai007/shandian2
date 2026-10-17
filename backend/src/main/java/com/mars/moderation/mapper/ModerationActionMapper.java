package com.mars.moderation.mapper;

import com.mars.moderation.ModerationAction;
import com.mars.moderation.repo.ModerationActionRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 处置记录 Mapper（表 {@code moderation_action}，<b>仅追加不可物理删除</b>，AC-7）。
 *
 * <p>唯一「更新」是申诉采纳后的解除（{@code disposition_effective=false}），以
 * {@code ON DUPLICATE KEY UPDATE} 表达；不提供任何 DELETE 路径。
 */
@Mapper
public interface ModerationActionMapper extends ModerationActionRepository {

    String COLUMNS = "action_id AS actionId, task_id AS taskId, room_id AS roomId, "
            + "session_id AS sessionId, target_user_id AS targetUserId, action_type AS actionType, "
            + "reason_code AS reasonCode, remark AS remark, operator_id AS operatorId, "
            + "operator_name AS operatorName, notify_channels AS notifyChannels, "
            + "evidence_id AS evidenceId, action_time AS actionTime, "
            + "appeal_deadline_at AS appealDeadlineAt, "
            + "disposition_effective AS dispositionEffective";

    @Insert("INSERT INTO moderation_action (action_id, task_id, room_id, session_id, "
            + "target_user_id, action_type, reason_code, remark, operator_id, operator_name, "
            + "notify_channels, evidence_id, action_time, appeal_deadline_at, disposition_effective) "
            + "VALUES (#{actionId}, #{taskId}, #{roomId}, #{sessionId}, #{targetUserId}, "
            + "#{actionType}, #{reasonCode}, #{remark}, #{operatorId}, #{operatorName}, "
            + "#{notifyChannels}, #{evidenceId}, #{actionTime}, #{appealDeadlineAt}, "
            + "#{dispositionEffective}) "
            + "ON DUPLICATE KEY UPDATE disposition_effective = VALUES(disposition_effective), "
            + "remark = VALUES(remark), reason_code = VALUES(reason_code), "
            + "notify_channels = VALUES(notify_channels), evidence_id = VALUES(evidence_id)")
    int upsert(ModerationAction action);

    @Override
    default void save(ModerationAction action) {
        upsert(action);
    }

    @Override
    @Select("SELECT " + COLUMNS + " FROM moderation_action WHERE action_id = #{actionId}")
    Optional<ModerationAction> findById(String actionId);

    @Override
    @Select("SELECT " + COLUMNS + " FROM moderation_action WHERE task_id = #{taskId} "
            + "ORDER BY action_time ASC")
    List<ModerationAction> listByTask(String taskId);
}
