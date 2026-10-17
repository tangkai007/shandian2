package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.ContentReport;
import com.mars.moderation.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台举报处理（契约 §4.9 · API-ADM-10 / 11 · FR-9.4）。
 *
 * <p>角色 {@code OPS / AUDITOR}（与 S2a mock 守卫一致）。
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequireRoles({"OPS", "AUDITOR"})
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** API-ADM-10：举报队列（按创建时间升序）。 */
    @GetMapping
    public ApiResponse<PageResult<ReportRow>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        List<ReportRow> rows = reportService.queue(null).stream().map(ReportRow::of).toList();
        return ApiResponse.ok(AdminPaging.of(rows, page, size));
    }

    /** API-ADM-11：处理举报（{@code REJECT} → 驳回，否则受理；重复处理 → {@code 40900}）。 */
    @PostMapping("/{reportId}/handle")
    public ApiResponse<ReportRow> handle(@PathVariable String reportId,
                                         @RequestBody HandleRequest body,
                                         HttpServletRequest request) {
        ContentReport handled = reportService.handle(reportId, body.decision(), body.reasonCode(),
                body.remark(), AdminActor.id(), AdminActor.name(), AdminActor.role(),
                AdminActor.ip(request));
        return ApiResponse.ok(ReportRow.of(handled));
    }

    /** 举报请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HandleRequest(String decision, String reasonCode, String remark) {
    }

    /** 举报行（S2a §4.9 API-ADM-10）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReportRow(String reportId,
                            String targetType,
                            String targetId,
                            String targetLabel,
                            String reason,
                            String reporterMasked,
                            LocalDateTime createdAt,
                            String status) {

        static ReportRow of(ContentReport report) {
            return new ReportRow(
                    report.reportId(),
                    report.targetType(),
                    report.targetId(),
                    report.targetLabel(),
                    firstNonBlank(report.reasonDesc(), report.reasonType()),
                    "用户 " + report.reporterId(),
                    report.createdAt(),
                    report.status().name());
        }

        private static String firstNonBlank(String primary, String fallback) {
            if (primary != null && !primary.isBlank()) {
                return primary;
            }
            return fallback == null ? "" : fallback;
        }
    }
}
