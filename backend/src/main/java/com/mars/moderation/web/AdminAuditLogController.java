package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.AdminOperationLog;
import com.mars.moderation.AuditRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台操作审计（契约 §4.9 · API-ADM-29 · FR-11.4）。
 *
 * <p>角色 {@code OPS / FINANCE}（与 S2a mock 守卫一致）。关键字 {@code keyword} 命中
 * 操作者 / 动作 / 目标 / 原因码 / 备注 / 来源 IP 任一字段即入选（前端搜索框「知网感」全文过滤）。
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequireRoles({"OPS", "FINANCE"})
public class AdminAuditLogController {

    private final AuditRecorder auditRecorder;

    public AdminAuditLogController(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    /** API-ADM-29：操作审计（按时间倒序，保留 ≥1 年）。 */
    @GetMapping
    public ApiResponse<PageResult<AuditLogRow>> list(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        List<AuditLogRow> rows = auditRecorder.list(keyword).stream().map(AuditLogRow::of).toList();
        return ApiResponse.ok(AdminPaging.of(rows, page, size));
    }

    /** 审计行（S2a §4.9 API-ADM-29 / 前端 {@code AuditLogRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuditLogRow(String logId,
                              String operatorName,
                              String operatorRole,
                              String action,
                              String targetType,
                              String targetLabel,
                              String reasonCode,
                              String remark,
                              String evidenceId,
                              String ip,
                              LocalDateTime createdAt) {

        static AuditLogRow of(AdminOperationLog log) {
            return new AuditLogRow(log.logId(), log.operatorName(), log.operatorRole(), log.action(),
                    log.targetType(), log.targetLabel(), log.reasonCode(), log.remark(),
                    log.evidenceId(), log.ip(), log.createdAt());
        }
    }
}
