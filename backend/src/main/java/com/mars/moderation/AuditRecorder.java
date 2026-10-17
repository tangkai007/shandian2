package com.mars.moderation;

import com.mars.common.support.OrderNoGenerator;
import com.mars.moderation.repo.AdminOperationLogRepository;
import com.mars.moderation.repo.OperatorLookup;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * 后台操作审计记录器（契约 §4.9 API-ADM-29）。
 *
 * <p>契约措辞为「AOP 切面写 {@code admin_operation_log}」；本实现改用<b>显式调用</b>：
 * Spring 装配里未引入 {@code spring-boot-starter-aop}（增依赖有构建风险），故由各后台写端点
 * 主动调用本记录器 —— 覆盖等价、偏差已登记于实现说明。
 *
 * <p><b>昵称来源（闭合「已知限制 9」）</b>：{@code JwtClaims} 不含昵称，故入参 {@code operatorName}
 * 常为用户 ID 字符串。记录器经 {@link OperatorLookup}（{@code user.nickname}）解析真实昵称覆盖；
 * 查不到时保留入参。装配未提供 lookup 时使用空实现（保持既有测试与调用方零改动）。
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
    public static final String ACTION_USER_BAN = "USER_BAN";
    public static final String ACTION_USER_UNBAN = "USER_UNBAN";
    public static final String ACTION_GIFT_CREATE = "GIFT_CREATE";
    public static final String ACTION_GIFT_UPDATE = "GIFT_UPDATE";
    public static final String ACTION_CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String ACTION_BANNER_CREATE = "BANNER_CREATE";
    public static final String ACTION_BANNER_UPDATE = "BANNER_UPDATE";
    public static final String ACTION_SETTLEMENT_GENERATE = "SETTLEMENT_GENERATE";
    public static final String ACTION_WITHDRAWAL_REVIEW = "WITHDRAWAL_REVIEW";
    public static final String ACTION_WITHDRAWAL_PAY = "WITHDRAWAL_PAY";
    public static final String ACTION_INVOICE_ISSUE = "INVOICE_ISSUE";

    private static final OperatorLookup NO_LOOKUP = userId -> Optional.empty();

    private final AdminOperationLogRepository logs;
    private final OrderNoGenerator ids;
    private final Clock clock;
    private final OperatorLookup operators;

    /** 兼容装配：不解析昵称，入参原样落痕。 */
    public AuditRecorder(AdminOperationLogRepository logs, OrderNoGenerator ids, Clock clock) {
        this(logs, ids, clock, NO_LOOKUP);
    }

    public AuditRecorder(AdminOperationLogRepository logs, OrderNoGenerator ids, Clock clock,
                         OperatorLookup operators) {
        this.logs = logs;
        this.ids = ids;
        this.clock = clock;
        this.operators = operators;
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
        String displayName = resolveName(operatorId, operatorName);
        logs.append(new AdminOperationLog(ids.next("AOL"), operatorId, displayName, operatorRole,
                action, targetType, targetId, targetLabel, reasonCode, remark, evidenceId,
                beforeJson, afterJson, ip, now));
    }

    public List<AdminOperationLog> list(String keyword) {
        return logs.list(keyword);
    }

    /** 昵称解析：命中 user.nickname 用之，否则保留入参（用户 ID 字符串）。 */
    private String resolveName(long operatorId, String fallback) {
        return operators.nicknameOf(operatorId)
                .filter(nickname -> !nickname.isBlank())
                .orElse(fallback);
    }
}
