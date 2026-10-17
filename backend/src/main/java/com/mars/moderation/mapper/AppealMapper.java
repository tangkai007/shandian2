package com.mars.moderation.mapper;

import com.mars.moderation.Appeal;
import com.mars.moderation.repo.AppealRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 申诉 Mapper（表 {@code appeal}，{@code action_id} 唯一 —— 一条处置仅可申诉一次）。
 *
 * <p>「采纳 → 解除处置」以 {@code ON DUPLICATE KEY UPDATE disposition_effective} 表达；
 * 不可物理删除。枚举列经全局 {@code EnumTypeHandler} 按名存取。
 */
@Mapper
public interface AppealMapper extends AppealRepository {

    String COLUMNS = "appeal_id AS appealId, action_id AS actionId, appellant_id AS appellantId, "
            + "action_type AS actionType, reason_code AS reasonCode, content AS content, "
            + "action_time AS actionTime, deadline_at AS deadlineAt, status AS status, "
            + "disposition_effective AS dispositionEffective, handled_by AS handledBy, "
            + "handle_remark AS handleRemark, created_at AS createdAt, handled_at AS handledAt";

    @Insert("INSERT INTO appeal (appeal_id, action_id, appellant_id, action_type, reason_code, "
            + "content, action_time, deadline_at, status, disposition_effective, handled_by, "
            + "handle_remark, created_at, handled_at) "
            + "VALUES (#{appealId}, #{actionId}, #{appellantId}, #{actionType}, #{reasonCode}, "
            + "#{content}, #{actionTime}, #{deadlineAt}, #{status}, #{dispositionEffective}, "
            + "#{handledBy}, #{handleRemark}, #{createdAt}, #{handledAt}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), "
            + "disposition_effective = VALUES(disposition_effective), "
            + "handled_by = VALUES(handled_by), handle_remark = VALUES(handle_remark), "
            + "handled_at = VALUES(handled_at)")
    int upsert(Appeal appeal);

    @Override
    default void save(Appeal appeal) {
        upsert(appeal);
    }

    @Override
    @Select("SELECT " + COLUMNS + " FROM appeal WHERE appeal_id = #{appealId}")
    Optional<Appeal> findById(String appealId);

    @Override
    @Select("SELECT " + COLUMNS + " FROM appeal WHERE action_id = #{actionId}")
    Optional<Appeal> findByActionId(String actionId);

    @Override
    @Select("<script>SELECT " + COLUMNS + " FROM appeal "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY created_at ASC</script>")
    List<Appeal> listByStatus(@Param("status") Appeal.AppealStatus status);
}
