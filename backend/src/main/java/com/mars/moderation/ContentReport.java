package com.mars.moderation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户举报（契约 §4.5 API-INT-09 → §4.9 API-ADM-10/11，FR-9.4）。
 *
 * <p>举报进审核队列；处理结果 {@code HANDLED}（受理）/ {@code REJECTED}（驳回）。
 */
public record ContentReport(
        String reportId,
        long reporterId,
        String targetType,
        String targetId,
        String targetLabel,
        Long roomId,
        String reasonType,
        String reasonDesc,
        List<String> images,
        ReportStatus status,
        Long handledBy,
        String handleReasonCode,
        String handleRemark,
        LocalDateTime createdAt,
        LocalDateTime handledAt) {

    /** 举报状态（前端 `PENDING/HANDLED/REJECTED`）。 */
    public enum ReportStatus {
        PENDING,
        HANDLED,
        REJECTED
    }

    public ContentReport {
        images = images == null ? List.of() : List.copyOf(images);
    }

    /** 处理为受理 / 驳回（终态）。 */
    public ContentReport handle(ReportStatus decision, long operatorId,
                                String reasonCode, String remark, LocalDateTime at) {
        return new ContentReport(reportId, reporterId, targetType, targetId, targetLabel, roomId,
                reasonType, reasonDesc, images, decision, operatorId, reasonCode, remark,
                createdAt, at);
    }
}
