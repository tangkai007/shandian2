package com.mars.moderation.mapper;

import com.mars.common.support.JsonLists;
import com.mars.moderation.ModerationEvidence;
import com.mars.moderation.repo.ModerationEvidenceRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 证据快照 Mapper（表 {@code moderation_evidence}，<b>与处置一一对应、不可删</b>，AC-7）。
 *
 * <p>{@code evidence_urls} 落库为 JSON 数组文本；读侧经 {@link JsonLists} 容错解析回
 * {@code List<String>}。为避免 MyBatis 对 {@code List} 型构造参数无内建类型处理器，
 * 读路径先取字符串行（{@link EvidenceRow}）再在 {@code default} 方法里组装领域对象。
 */
@Mapper
public interface ModerationEvidenceMapper extends ModerationEvidenceRepository {

    /** 读路径中间行：与 {@code moderation_evidence} 列一一对应（evidence_urls 保持字符串）。 */
    record EvidenceRow(String evidenceId,
                       String actionId,
                       LocalDateTime snapshotAt,
                       String ruleId,
                       int confidence,
                       String operatorName,
                       String evidenceUrls) {
    }

    @Insert("INSERT INTO moderation_evidence (evidence_id, action_id, snapshot_at, rule_id, "
            + "confidence, operator_name, evidence_urls) "
            + "VALUES (#{evidenceId}, #{actionId}, #{snapshotAt}, #{ruleId}, #{confidence}, "
            + "#{operatorName}, #{evidenceUrlsJson}) "
            + "ON DUPLICATE KEY UPDATE evidence_urls = VALUES(evidence_urls), "
            + "snapshot_at = VALUES(snapshot_at), rule_id = VALUES(rule_id), "
            + "confidence = VALUES(confidence), operator_name = VALUES(operator_name)")
    int insertRow(@Param("evidenceId") String evidenceId,
                  @Param("actionId") String actionId,
                  @Param("snapshotAt") LocalDateTime snapshotAt,
                  @Param("ruleId") String ruleId,
                  @Param("confidence") int confidence,
                  @Param("operatorName") String operatorName,
                  @Param("evidenceUrlsJson") String evidenceUrlsJson);

    @Override
    default void save(ModerationEvidence evidence) {
        insertRow(evidence.evidenceId(), evidence.actionId(), evidence.snapshotAt(),
                evidence.ruleId(), evidence.confidence(), evidence.operatorName(),
                JsonLists.toJson(evidence.evidenceUrls()));
    }

    @Select("SELECT evidence_id AS evidenceId, action_id AS actionId, "
            + "snapshot_at AS snapshotAt, rule_id AS ruleId, confidence AS confidence, "
            + "operator_name AS operatorName, evidence_urls AS evidenceUrls "
            + "FROM moderation_evidence WHERE action_id = #{actionId}")
    Optional<EvidenceRow> selectByActionId(String actionId);

    @Override
    default Optional<ModerationEvidence> findByActionId(String actionId) {
        return selectByActionId(actionId).map(row -> new ModerationEvidence(
                row.evidenceId(), row.actionId(), row.snapshotAt(), row.ruleId(),
                row.confidence(), row.operatorName(), JsonLists.parse(row.evidenceUrls())));
    }
}
