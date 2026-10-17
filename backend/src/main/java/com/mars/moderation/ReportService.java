package com.mars.moderation;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.repo.ContentReportRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 用户举报服务（契约 §4.5 API-INT-09 上报 · §4.9 API-ADM-10/11 处理，FR-9.4）。
 *
 * <p>举报进审核队列（{@code PENDING}）；处理结果二值化：{@code HANDLED}（受理）/
 * {@code REJECTED}（驳回）—— 与 S2a mock {@code reportHandle} 口径一致（仅 {@code REJECT}
 * 映射为驳回，其余视为受理）。每次处理写一条 {@code REPORT_HANDLE} 后台审计。
 */
public final class ReportService {

    private final ContentReportRepository reports;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator ids;
    private final AuditRecorder audit;
    private final Clock clock;

    public ReportService(ContentReportRepository reports,
                         UnitOfWork unitOfWork,
                         OrderNoGenerator ids,
                         AuditRecorder audit,
                         Clock clock) {
        this.reports = reports;
        this.unitOfWork = unitOfWork;
        this.ids = ids;
        this.audit = audit;
        this.clock = clock;
    }

    /** 提交举报（API-INT-09），返回举报 ID。 */
    public String file(long reporterId, String targetType, String targetId, String targetLabel,
                       Long roomId, String reasonType, String reasonDesc, List<String> images) {
        if (reasonType == null || reasonType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "举报原因必填", "reason_type", "required");
        }
        return unitOfWork.execute(() -> {
            String reportId = ids.next("RPT");
            reports.save(new ContentReport(reportId, reporterId,
                    blankTo(targetType, "ROOM"), blankTo(targetId, String.valueOf(roomId == null ? 0 : roomId)),
                    blankTo(targetLabel, ""), roomId, reasonType, blankTo(reasonDesc, ""),
                    images == null ? List.of() : images, ContentReport.ReportStatus.PENDING,
                    null, null, null, now(), null));
            return reportId;
        });
    }

    /** 举报队列（API-ADM-10）：按创建时间升序；{@code status} 可空取全部。 */
    public List<ContentReport> queue(ContentReport.ReportStatus status) {
        return reports.listByStatus(status);
    }

    /**
     * 处理举报（API-ADM-11）：{@code decision=REJECT} → 驳回，否则受理；终态不可重复处理。
     * 写一条 {@code REPORT_HANDLE} 审计。
     */
    public ContentReport handle(String reportId, String decision, String reasonCode, String remark,
                                long operatorId, String operatorName, String operatorRole, String ip) {
        if (decision == null || decision.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "处理结论必填", "decision", "required");
        }
        return unitOfWork.execute(() -> {
            ContentReport report = reports.findById(reportId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.RESOURCE_GONE, "举报不存在", "report_id", "not_found"));
            if (report.status() != ContentReport.ReportStatus.PENDING) {
                throw new BizException(ErrorCode.CONFLICT, "举报已处理", "report_id", "handled");
            }
            boolean rejected = "REJECT".equalsIgnoreCase(decision.trim());
            ContentReport.ReportStatus next = rejected
                    ? ContentReport.ReportStatus.REJECTED : ContentReport.ReportStatus.HANDLED;
            ContentReport handled = report.handle(next, operatorId, reasonCode, remark, now());
            reports.save(handled);

            audit.record(operatorId, operatorName, operatorRole, AuditRecorder.ACTION_REPORT_HANDLE,
                    "REPORT", report.reportId(), report.targetLabel(), reasonCode, remark, null, ip);
            return handled;
        });
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
