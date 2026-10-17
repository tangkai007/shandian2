package com.mars.support;

import com.mars.common.tx.InMemoryUnitOfWork;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.AuditRecorder;
import com.mars.moderation.AppealService;
import com.mars.moderation.ModerationActionService;
import com.mars.moderation.ModerationQueueService;
import com.mars.moderation.PlatformConfig;
import com.mars.moderation.ReplayService;
import com.mars.moderation.ReportService;
import com.mars.moderation.RoomBrief;
import com.mars.moderation.SysConfigService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 审核 / 举报 / 申诉 / 回放 / 平台口径 / 审计 的测试夹具。
 *
 * <p>把零依赖领域服务接到内存仓储 + 内存事务管理器（{@code InMemoryUnitOfWork}）上，
 * 事务参与者覆盖<b>全部</b>存储 —— 于是「异常即整体回滚」可在无 MySQL 环境下真实复现。
 * 时钟固定为 {@code 2026-11-20T10:00:00Z}，使 {@code deadline_at / expire_at} 可精确断言。
 */
public final class ModerationFixture {

    /** 固定时间基准（UTC）。 */
    public static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    public final InMemoryStores.ModerationTasks tasks = new InMemoryStores.ModerationTasks();
    public final InMemoryStores.ModerationActions actions = new InMemoryStores.ModerationActions();
    public final InMemoryStores.ModerationEvidences evidences = new InMemoryStores.ModerationEvidences();
    public final InMemoryStores.ContentReports reports = new InMemoryStores.ContentReports();
    public final InMemoryStores.Appeals appeals = new InMemoryStores.Appeals();
    public final InMemoryStores.ReplayRecords replays = new InMemoryStores.ReplayRecords();
    public final InMemoryStores.SysConfigs sysConfigs = new InMemoryStores.SysConfigs();
    public final InMemoryStores.AdminOperationLogs auditLogs = new InMemoryStores.AdminOperationLogs();
    public final InMemoryStores.WebhookEvents webhookEvents = new InMemoryStores.WebhookEvents();
    public final InMemoryStores.Rooms rooms = new InMemoryStores.Rooms();
    public final InMemoryStores.SeqOrderNoGenerator orderNos = new InMemoryStores.SeqOrderNoGenerator();

    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    public final UnitOfWork unitOfWork = new InMemoryUnitOfWork(
            tasks, actions, evidences, reports, appeals, replays,
            sysConfigs, auditLogs, webhookEvents, rooms);

    public final PlatformConfig platformConfig = new PlatformConfig(sysConfigs, 7, 30);
    public final AuditRecorder audit = new AuditRecorder(auditLogs, orderNos, clock);
    public final ModerationQueueService queue =
            new ModerationQueueService(tasks, webhookEvents, rooms, unitOfWork, orderNos, clock);
    public final ModerationActionService actionService =
            new ModerationActionService(tasks, actions, evidences, rooms, platformConfig,
                    unitOfWork, orderNos, audit, clock);
    public final ReportService reportService =
            new ReportService(reports, unitOfWork, orderNos, audit, clock);
    public final AppealService appealService =
            new AppealService(appeals, actions, platformConfig, unitOfWork, orderNos, audit, clock);
    public final ReplayService replayService =
            new ReplayService(replays, platformConfig, unitOfWork, orderNos, clock);
    public final SysConfigService sysConfigService =
            new SysConfigService(sysConfigs, unitOfWork, audit);

    public ModerationFixture() {
    }

    /** 预置一个直播间（房号 = 房间主键）。 */
    public void seedRoom(long roomId, long anchorId, String anchorName) {
        rooms.seed(new RoomBrief(roomId, roomId, anchorId, anchorName, "LIVE"));
    }
}
