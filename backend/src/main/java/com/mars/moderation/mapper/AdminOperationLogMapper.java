package com.mars.moderation.mapper;

import com.mars.moderation.AdminOperationLog;
import com.mars.moderation.repo.AdminOperationLogRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 后台操作审计 Mapper（表 {@code admin_operation_log}，API-ADM-29）。
 *
 * <p><b>仅追加</b>：无 UPDATE / DELETE 路径（保留 ≥1 年）。{@code before_json / after_json}
 * 为空时写 NULL；有值须为合法 JSON 文本。
 */
@Mapper
public interface AdminOperationLogMapper extends AdminOperationLogRepository {

    String COLUMNS = "log_id AS logId, operator_id AS operatorId, operator_name AS operatorName, "
            + "operator_role AS operatorRole, action AS action, target_type AS targetType, "
            + "target_id AS targetId, target_label AS targetLabel, reason_code AS reasonCode, "
            + "remark AS remark, evidence_id AS evidenceId, before_json AS beforeJson, "
            + "after_json AS afterJson, ip AS ip, created_at AS createdAt";

    @Override
    @Insert("INSERT INTO admin_operation_log (log_id, operator_id, operator_name, operator_role, "
            + "action, target_type, target_id, target_label, reason_code, remark, evidence_id, "
            + "before_json, after_json, ip, created_at) "
            + "VALUES (#{logId}, #{operatorId}, #{operatorName}, #{operatorRole}, #{action}, "
            + "#{targetType}, #{targetId}, #{targetLabel}, #{reasonCode,jdbcType=VARCHAR}, "
            + "#{remark,jdbcType=VARCHAR}, #{evidenceId,jdbcType=VARCHAR}, "
            + "#{beforeJson,jdbcType=VARCHAR}, #{afterJson,jdbcType=VARCHAR}, #{ip}, #{createdAt})")
    void append(AdminOperationLog log);

    @Override
    @Select("<script>SELECT " + COLUMNS + " FROM admin_operation_log "
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + "(operator_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR action LIKE CONCAT('%', #{keyword}, '%') "
            + "OR target_label LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if></where> ORDER BY created_at DESC, log_id DESC</script>")
    List<AdminOperationLog> list(@Param("keyword") String keyword);
}
