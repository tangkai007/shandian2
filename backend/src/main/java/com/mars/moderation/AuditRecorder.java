package com.mars.moderation;

import com.mars.common.support.OrderNoGenerator;
import com.mars.moderation.repo.AdminOperationLogRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 后台操作审计记录器（契约 §4.9 API-ADM-29）。
 *
 * <p>契约措辞为「AOP 切面写 {@code admin_operation_log}」；本实现改用<b>显式调用</b>：
 * Spring 装配里未引入 {@code spring-boot-starter-aop}（增依赖有构建风险），故由各后台写端点
 * 主动调用本记录器 —— 覆盖等价、偏差已登记于实现说明。
 */
public final class AuditRecorder {

    /**
     * 动作名常量（与前端 `AuditLogRow.action` / `mock.ts#writeAudit` 逐字一致）。
     *
     * <p>处置动作的 {@code action} 直接取处置类型名（{@code WARN|CUT|BAN|REPORT|MARK_OK}），
     * 故这里不设 {@code MODERATION_ACTION} 常量；认领（claim）按 S2a mock 口径<b>不写审计</b>。
     */
    public static final String ACTION_FORCE_STOP = "FORCE_STOP";
    public static final String ACTION_REPORT_HANDLE = "REPORT_HANDLE";
    public static final String ACTION_APPEAL_ACCEPT = "APPEAL_ACCEPT";
    public static final String ACTION_APPEAL_REJECT = "APPEAL_REJECT";
    public static final String ACTION_SYS_CONFIG_UPDATE = "SYS_CONFIG_UPDATE";

    private final AdminOperationLogRepository logs;
    private final OrderNoGenerator ids;
    private final Clock clock;

    public AuditRecorder(AdminOperationLogRepository logs, OrderNoGenerator ids, Clock clock) {
        this.logs = logs;
        this.ids = ids;
        this.clock = clock;
    }

    public void record(long operatorId, String operatorName, String operatorRole, String action,
                       String targetType, String targetId, String targetLabel,
                       String reasonCode, String remark, String evidenceId, String ip) {
        record(operatorId, operatorName, operatorRole, action, targetType, targetId, targetLabel,
                reasonCode, remark, evidenceId, null, null, ip);
    }

    public void record(long operatorId, String operatorName, String operatorRole, String action,
                       String targetType, String targetId, String targetLabel,
                       String reasonCode, String remark, String evidenceId,
                       String beforeJson, String afterJson, String ip) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        logs.append(new AdminOperationLog(ids.next("AOL"), operatorId, operatorName, operatorRole,
                action, targetType, targetId, targetLabel, reasonCode, remark, evidenceId,
                beforeJson, afterJson, ip, now));
    }

    public List<AdminOperationLog> list(String keyword) {
        return logs.list(keyword);
    }
}
