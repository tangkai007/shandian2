package com.mars.moderation.mapper;

import com.mars.common.support.JsonLists;
import com.mars.moderation.ContentReport;
import com.mars.moderation.repo.ContentReportRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户举报 Mapper（表 {@code content_report}）。
 *
 * <p>{@code images} 以 JSON 数组文本落库，读侧经 {@link JsonLists} 解析（同证据快照的写法）。
 */
@Mapper
public interface ContentReportMapper extends ContentReportRepository {

    /** 读路径中间行：{@code images} 保持 JSON 字符串。 */
    record ReportRow(String reportId,
                     long reporterId,
                     String targetType,
                     String targetId,
                     String targetLabel,
                     Long roomId,
                     String reasonType,
                     String reasonDesc,
                     String images,
                     String status,
                     Long handledBy,
                     String handleReasonCode,
                     String handleRemark,
                     LocalDateTime createdAt,
                     LocalDateTime handledAt) {

        ContentReport toDomain() {
            return new ContentReport(reportId, reporterId, targetType, targetId, targetLabel, roomId,
                    reasonType, reasonDesc, JsonLists.parse(images),
                    ContentReport.ReportStatus.valueOf(status), handledBy, handleReasonCode,
                    handleRemark, createdAt, handledAt);
        }
    }

    String ROW_COLUMNS = "report_id AS reportId, reporter_id AS reporterId, "
            + "target_type AS targetType, target_id AS targetId, target_label AS targetLabel, "
            + "room_id AS roomId, reason_type AS reasonType, reason_desc AS reasonDesc, "
            + "images AS images, status AS status, handled_by AS handledBy, "
            + "handle_reason_code AS handleReasonCode, handle_remark AS handleRemark, "
            + "created_at AS createdAt, handled_at AS handledAt";

    @Insert("INSERT INTO content_report (report_id, reporter_id, target_type, target_id, "
            + "target_label, room_id, reason_type, reason_desc, images, status, handled_by, "
            + "handle_reason_code, handle_remark, created_at, handled_at) "
            + "VALUES (#{reportId}, #{reporterId}, #{targetType}, #{targetId}, #{targetLabel}, "
            + "#{roomId}, #{reasonType}, #{reasonDesc}, #{imagesJson}, #{status}, #{handledBy}, "
            + "#{handleReasonCode}, #{handleRemark}, #{createdAt}, #{handledAt}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), handled_by = VALUES(handled_by), "
            + "handle_reason_code = VALUES(handle_reason_code), "
            + "handle_remark = VALUES(handle_remark), handled_at = VALUES(handled_at)")
    int upsertRow(@Param("reportId") String reportId,
                  @Param("reporterId") long reporterId,
                  @Param("targetType") String targetType,
                  @Param("targetId") String targetId,
                  @Param("targetLabel") String targetLabel,
                  @Param("roomId") Long roomId,
                  @Param("reasonType") String reasonType,
                  @Param("reasonDesc") String reasonDesc,
                  @Param("imagesJson") String imagesJson,
                  @Param("status") String status,
                  @Param("handledBy") Long handledBy,
                  @Param("handleReasonCode") String handleReasonCode,
                  @Param("handleRemark") String handleRemark,
                  @Param("createdAt") LocalDateTime createdAt,
                  @Param("handledAt") LocalDateTime handledAt);

    @Override
    default void save(ContentReport report) {
        upsertRow(report.reportId(), report.reporterId(), report.targetType(), report.targetId(),
                report.targetLabel(), report.roomId(), report.reasonType(), report.reasonDesc(),
                JsonLists.toJson(report.images()), report.status().name(), report.handledBy(),
                report.handleReasonCode(), report.handleRemark(), report.createdAt(),
                report.handledAt());
    }

    @Select("SELECT " + ROW_COLUMNS + " FROM content_report WHERE report_id = #{reportId}")
    Optional<ReportRow> selectById(String reportId);

    @Override
    default Optional<ContentReport> findById(String reportId) {
        return selectById(reportId).map(ReportRow::toDomain);
    }

    @Select("<script>SELECT " + ROW_COLUMNS + " FROM content_report "
            + "<where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY created_at ASC</script>")
    List<ReportRow> selectByStatus(@Param("status") String status);

    @Override
    default List<ContentReport> listByStatus(ContentReport.ReportStatus status) {
        return selectByStatus(status == null ? null : status.name()).stream()
                .map(ReportRow::toDomain)
                .toList();
    }
}
