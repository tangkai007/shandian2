package com.mars.spec;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.moderation.AdminOperationLog;
import com.mars.moderation.AlertType;
import com.mars.moderation.Appeal;
import com.mars.moderation.ContentReport;
import com.mars.moderation.DispositionCommand;
import com.mars.moderation.DispositionResult;
import com.mars.moderation.ModerationAction;
import com.mars.moderation.ModerationActionService;
import com.mars.moderation.ModerationActionType;
import com.mars.moderation.ModerationAlertEvent;
import com.mars.moderation.ModerationEvidence;
import com.mars.moderation.ModerationQueueService;
import com.mars.moderation.ModerationTask;
import com.mars.moderation.RecordingCompletedEvent;
import com.mars.moderation.ReplayRecord;
import com.mars.moderation.ReplayService;
import com.mars.moderation.RiskLevel;
import com.mars.moderation.SysConfigEntry;
import com.mars.moderation.TaskStatus;
import com.mars.support.ModerationFixture;
import com.mars.testkit.Assert;
import com.mars.testkit.Registry;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 审核域 spec（AC-7 全链路 · AC-9 回放 · ADM-20 口径 · ADM-29 审计）。
 *
 * <p>覆盖：机审回调入队（幂等 + 置信度分流）→ 队列排序 → 处置留痕 + 证据快照 +
 * 申诉时限（取后台配置，不硬编码 7 天）→ 举报处理 → 申诉采纳解除处置 → 强制关播 →
 * 回放留存（幂等 + 过期 40330）→ 口径目录 / 审计关键字过滤。
 */
public final class ModerationSpecs {

    /** 夹具固定时钟对应的本地时间（UTC）：2026-11-20T10:00。 */
    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(ModerationFixture.NOW, ZoneOffset.UTC);

    private ModerationSpecs() {
    }

    public static void register(Registry registry) {

        // 1. 机审回调入队 + 置信度分流 + 幂等
        registry.add("AC-7 · 机审回调入队：置信度分流 + 同事件幂等", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");

            ModerationQueueService.EnqueueResult first = f.queue.enqueue(alert("evt-1", 90));
            a.isFalse(first.duplicate(), "首次投递非重复");
            a.notNull(first.taskId(), "生成任务 ID");

            ModerationTask task = f.tasks.findById(first.taskId()).orElseThrow();
            a.eq(task.riskLevel(), RiskLevel.HIGH, "置信度 90 → HIGH");
            a.eq(task.status(), TaskStatus.PENDING, "入队即 PENDING");
            a.eq(task.anchorName(), "小闪", "主播名来自回调");
            a.eqLong(task.roomNo(), 10241L, "房号落库");
            a.eqLong(task.confidence(), 90L, "置信度落库");
            a.eq(task.alertType(), AlertType.IMAGE, "告警类型落库");
            a.eq(task.reasonSummary(), "疑似违规截图", "原因摘要落库");
            a.eq(task.snapshotUrl(), "https://cos/snap.jpg", "快照 URL 落库");

            // 同一 eventId 重复投递 → 回吐既有任务，不重复入队
            ModerationQueueService.EnqueueResult again = f.queue.enqueue(alert("evt-1", 90));
            a.isTrue(again.duplicate(), "重复投递被识别");
            a.eq(again.taskId(), first.taskId(), "回吐同一任务 ID");
            a.eqLong(f.queue.queue().size(), 1L, "队列不重复入队");

            // 置信度分档
            a.eq(alert("evt-m", 60).riskLevel(), RiskLevel.MEDIUM, "60 → MEDIUM");
            a.eq(alert("evt-l", 10).riskLevel(), RiskLevel.LOW, "10 → LOW");
        });

        // 2. 队列排序 HIGH → MEDIUM → LOW
        registry.add("AC-7 · 审核队列按风险等级升序（高在前）", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");
            f.queue.enqueue(alert("evt-low", 10));
            f.queue.enqueue(alert("evt-high", 95));
            f.queue.enqueue(alert("evt-med", 55));

            List<ModerationTask> queue = f.queue.queue();
            a.eqLong(queue.size(), 3L, "三条任务");
            a.eq(queue.get(0).riskLevel(), RiskLevel.HIGH, "第 1 条高风险");
            a.eq(queue.get(1).riskLevel(), RiskLevel.MEDIUM, "第 2 条中风险");
            a.eq(queue.get(2).riskLevel(), RiskLevel.LOW, "第 3 条低风险");
        });

        // 3. 处置全链路：留痕 + 证据快照 + deadline = actionTime + 7 天
        registry.add("AC-7 · 处置全链路：留痕 + 证据快照 + 申诉时限 +7 天", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");
            String taskId = f.queue.enqueue(alert("evt-d", 90)).taskId();

            DispositionResult res = f.actionService.dispose(
                    cmd(taskId, ModerationActionType.CUT, "R-1102", "涉黄内容断流留痕"),
                    "AUDITOR", "10.0.0.1");
            a.isTrue(res.dispositionApplied(), "处置已落地");
            a.eq(res.taskStatus(), TaskStatus.DONE, "任务关单");
            a.eq(res.appealWindowDays(), 7, "申诉时限默认 7 天");

            ModerationAction action = f.actions.findById(res.actionId()).orElseThrow();
            a.eq(action.actionType(), ModerationActionType.CUT, "动作 = CUT");
            a.eq(action.reasonCode(), "R-1102", "原因码留痕");
            a.eq(action.appealDeadlineAt(), NOW.plusDays(7), "deadline = actionTime + 7 天");
            a.isTrue(action.dispositionEffective(), "申诉期间处置不解除");
            a.notNull(action.evidenceId(), "处置关联证据 ID");

            ModerationEvidence evidence = f.actionService.evidenceOf(res.actionId());
            a.eq(evidence.snapshotAt(), NOW, "证据快照时刻 = 处置时刻");
            a.eq(evidence.ruleId(), "R-1102", "证据带命中规则");
            a.isTrue(evidence.evidenceUrls().contains("https://cos/ev.jpg"), "证据 URL 快照");
            a.eqLong(evidence.confidence(), 90L, "证据带置信度");

            a.eq(f.tasks.findById(taskId).orElseThrow().status(), TaskStatus.DONE, "任务终态 DONE");

            List<AdminOperationLog> logs = f.audit.list(null);
            a.eqLong(logs.size(), 1L, "写一条后台审计");
            a.eq(logs.get(0).action(), "CUT", "审计动作 = CUT");
            a.eq(logs.get(0).evidenceId(), res.actionId(), "审计 evidence_id 指向处置");
        });

        // 4. 处置参数校验（备注 < 5 字 / 缺原因码 → 40001）
        registry.add("AC-7 · 处置校验：备注 <5 字 / 缺原因码 → 40001", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");
            String taskId = f.queue.enqueue(alert("evt-v", 90)).taskId();

            a.eq(codeOf(() -> f.actionService.dispose(
                    cmd(taskId, ModerationActionType.WARN, "R-1301", "太短"),
                    "AUDITOR", "ip")), ErrorCode.PARAM_INVALID, "备注不足 5 字 → 40001");
            a.eq(fieldOf(() -> f.actionService.dispose(
                    cmd(taskId, ModerationActionType.WARN, "R-1301", "太短"),
                    "AUDITOR", "ip")), "remark", "定位字段 = remark");

            a.eq(codeOf(() -> f.actionService.dispose(
                    cmd(taskId, ModerationActionType.CUT, null, "备注足够长的处置理由"),
                    "AUDITOR", "ip")), ErrorCode.PARAM_INVALID, "缺原因码 → 40001");
            a.eq(fieldOf(() -> f.actionService.dispose(
                    cmd(taskId, ModerationActionType.CUT, null, "备注足够长的处置理由"),
                    "AUDITOR", "ip")), "reason_code", "定位字段 = reason_code");
        });

        // 5. MARK_OK 只关单不留痕（仍写审计）
        registry.add("AC-7 · MARK_OK 只关单不留痕（仍写审计）", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");
            String taskId = f.queue.enqueue(alert("evt-mk", 90)).taskId();

            DispositionResult res = f.actionService.dispose(
                    cmd(taskId, ModerationActionType.MARK_OK, null, "复核后判定无风险"),
                    "AUDITOR", "ip");
            a.isFalse(res.dispositionApplied(), "MARK_OK 不产生处置");
            a.eq(res.taskStatus(), TaskStatus.DONE, "仅关单");
            a.isTrue(f.actions.findById(res.actionId()).isEmpty(), "不写处置记录");
            a.eq(codeOf(() -> f.actionService.evidenceOf(res.actionId())),
                    ErrorCode.RESOURCE_GONE, "不写证据快照");
            a.eq(f.tasks.findById(taskId).orElseThrow().status(), TaskStatus.DONE, "任务关单");

            List<AdminOperationLog> logs = f.audit.list(null);
            a.eqLong(logs.size(), 1L, "MARK_OK 仍写一条审计");
            a.eq(logs.get(0).action(), "MARK_OK", "审计动作 = MARK_OK");
            a.isNull(logs.get(0).reasonCode(), "MARK_OK 无原因码");
        });

        // 6. 申诉时限取后台配置（改配置即刻生效，不硬编码 7 天）
        registry.add("AC-7 · 申诉时限取后台配置（改口径即生效）", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");
            a.eq(f.platformConfig.appealDays(), 7, "默认 7 天");

            f.sysConfigService.update("appeal.window_days", "3", 88, "运营A", "OPS", "ip");
            a.eq(f.platformConfig.appealDays(), 3, "改配置后 = 3 天");

            String taskId = f.queue.enqueue(alert("evt-a6", 90)).taskId();
            DispositionResult res = f.actionService.dispose(
                    cmd(taskId, ModerationActionType.WARN, "R-1301", "警告主播并留痕"),
                    "AUDITOR", "ip");
            a.eq(res.appealWindowDays(), 3, "处置结果下发申诉时限 = 3");
            ModerationAction action = f.actions.findById(res.actionId()).orElseThrow();
            a.eq(action.appealDeadlineAt(), NOW.plusDays(3), "deadline = actionTime + 3 天");
        });

        // 7. 举报入队 / 受理 / 驳回 / 终态不可重复处理
        registry.add("AC-7 · 举报：上报入队 + 受理/驳回 + 终态防重", a -> {
            ModerationFixture f = new ModerationFixture();

            a.eq(codeOf(() -> f.reportService.file(555L, "ROOM", "10241", "小闪",
                    10241L, "", null, List.of())), ErrorCode.PARAM_INVALID, "缺原因 → 40001");

            String r1 = f.reportService.file(555L, "ROOM", "10241", "小闪（房号 10241）",
                    10241L, "涉黄", "露骨画面", List.of("https://cos/r.jpg"));
            a.notNull(r1, "生成举报 ID");
            a.eqLong(f.reportService.queue(ContentReport.ReportStatus.PENDING).size(), 1L,
                    "待处理队列 1 条");

            ContentReport rejected = f.reportService.handle(r1, "REJECT", "R-9001", "证据不足",
                    77, "审核员A", "AUDITOR", "ip");
            a.eq(rejected.status(), ContentReport.ReportStatus.REJECTED, "REJECT → 驳回");
            a.eqLong(rejected.handledBy(), 77L, "记录处理人");
            a.eq(codeOf(() -> f.reportService.handle(r1, "ACCEPT", "R-9001", "重复处理",
                    77, "审核员A", "AUDITOR", "ip")), ErrorCode.CONFLICT, "终态重复处理 → 40900");

            String r2 = f.reportService.file(556L, "ROOM", "10241", "小闪", 10241L,
                    "辱骂", "言语攻击", List.of());
            ContentReport handled = f.reportService.handle(r2, "ACCEPT", "R-1301", "受理并处置",
                    77, "审核员A", "AUDITOR", "ip");
            a.eq(handled.status(), ContentReport.ReportStatus.HANDLED, "非 REJECT → 受理");
            a.eqLong(f.audit.list("REPORT_HANDLE").size(), 2L, "两次处理各写一条审计");
        });

        // 8. 申诉采纳解除处置 + 一处置仅可申诉一次
        registry.add("AC-7 · 申诉：采纳解除处置 + 一处置仅申诉一次", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");
            String taskId = f.queue.enqueue(alert("evt-a8", 90)).taskId();
            DispositionResult res = f.actionService.dispose(
                    cmd(taskId, ModerationActionType.BAN, "R-1103", "违禁品封禁留痕"),
                    "AUDITOR", "ip");

            Appeal appeal = f.appealService.file(res.actionId(), 2001L, "内容被误判，请复核");
            a.eq(appeal.status(), Appeal.AppealStatus.PENDING, "申诉待处理");
            a.eq(appeal.deadlineAt(), NOW.plusDays(7), "申诉时限 = actionTime + 7 天");

            // 申诉期间处置不解除
            a.isTrue(f.actions.findById(res.actionId()).orElseThrow().dispositionEffective(),
                    "申诉期间处置维持");
            a.eq(codeOf(() -> f.appealService.file(res.actionId(), 2001L, "再申诉")),
                    ErrorCode.CONFLICT, "同一处置重复申诉 → 40900");

            Appeal accepted = f.appealService.handle(appeal.appealId(), "ACCEPT", "复核确认误判",
                    99, "审核员B", "AUDITOR", "ip");
            a.eq(accepted.status(), Appeal.AppealStatus.ACCEPTED, "采纳");
            a.isFalse(f.actions.findById(res.actionId()).orElseThrow().dispositionEffective(),
                    "采纳后解除原处置");
            a.eq(codeOf(() -> f.appealService.handle(appeal.appealId(), "REJECT", "重复",
                    99, "审核员B", "AUDITOR", "ip")), ErrorCode.CONFLICT, "终态重复处理 → 40900");

            List<AdminOperationLog> logs = f.audit.list("APPEAL_ACCEPT");
            a.eqLong(logs.size(), 1L, "写一条 APPEAL_ACCEPT 审计");
            a.eq(logs.get(0).evidenceId(), res.actionId(), "审计 evidence_id 指向原处置");
        });

        // 9. 强制关播：必填原因码 + CUT 留痕 + 未知房间 40330
        registry.add("AC-7 · 强制关播：必填原因码 + CUT 留痕 + 未知房间 40330", a -> {
            ModerationFixture f = new ModerationFixture();
            f.seedRoom(10241L, 2001L, "小闪");

            a.eq(codeOf(() -> f.actionService.forceStop(10241L, 9001L, null, "备注",
                    77, "审核员A", "AUDITOR", "ip")), ErrorCode.PARAM_INVALID, "缺原因码 → 40001");

            ModerationActionService.ForceStopResult result = f.actionService.forceStop(
                    10241L, 9001L, "R-1102", "涉黄强制关播", 77, "审核员A", "AUDITOR", "ip");
            a.eq(result.status(), "INTERRUPTED", "房间状态 = INTERRUPTED");
            ModerationAction cut = f.actions.findById(result.actionId()).orElseThrow();
            a.eq(cut.actionType(), ModerationActionType.CUT, "强制关播写 CUT 留痕");
            a.eqLong(cut.targetUserId(), 2001L, "定位到主播账号");
            a.eqLong(cut.roomId(), 10241L, "定位到房间");

            a.eq(f.audit.list("FORCE_STOP").size(), 1, "写一条 FORCE_STOP 审计");

            a.eq(codeOf(() -> f.actionService.forceStop(99999L, null, "R-1102", "备注",
                    77, "审核员A", "AUDITOR", "ip")), ErrorCode.RESOURCE_GONE, "未知房间 → 40330");
        });

        // 10. AC-9 回放：留存取配置 + 幂等 + 播放地址 hls
        registry.add("AC-9 · 回放：留存取配置 + vod 幂等 + 播放地址 hls", a -> {
            ModerationFixture f = new ModerationFixture();
            RecordingCompletedEvent event = recording("evt-rec-1", "vod-1");

            ReplayRecord record = f.replayService.onRecordingCompleted(event);
            a.eq(record.expireAt(), NOW.plusDays(30), "留存默认 30 天");
            a.eq(record.status(), ReplayRecord.ReplayStatus.READY, "新回放 READY");
            a.eq(record.vodFileId(), "vod-1", "幂等键落库");

            ReplayRecord dup = f.replayService.onRecordingCompleted(event);
            a.eq(dup.replayId(), record.replayId(), "同 vod_file_id 幂等回吐");
            a.eqLong(f.replayService.listByRoom(10241L).size(), 1L, "不重复落库");

            assertPlayInfo(f, record.replayId(), a);

            // 改留存口径 → 新回放按新口径算
            f.sysConfigs.put("replay_retention_days", "7", "回放留存");
            ReplayRecord seven = f.replayService.onRecordingCompleted(recording("evt-rec-2", "vod-2"));
            a.eq(seven.expireAt(), NOW.plusDays(7), "改口径后 = 7 天");
        });

        // 11. AC-9 回放过期 / 不存在 → 40330
        registry.add("AC-9 · 回放过期 / 不存在 → 40330", a -> {
            ModerationFixture f = new ModerationFixture();
            LocalDateTime ended = NOW.minusDays(31);
            RecordingCompletedEvent old = new RecordingCompletedEvent("evt-old", 10241L, 2001L,
                    9001L, "vod-old", "https://cos/old.m3u8", 1200, ended.minusHours(1), ended);
            ReplayRecord record = f.replayService.onRecordingCompleted(old);

            a.eq(f.replayService.listByRoom(10241L).get(0).status(),
                    ReplayRecord.ReplayStatus.EXPIRED, "列表读出即判定过期");
            a.eq(codeOf(() -> f.replayService.playUrl(record.replayId())),
                    ErrorCode.RESOURCE_GONE, "过期回放取地址 → 40330");
            a.eq(codeOf(() -> f.replayService.playUrl("RP-不存在")),
                    ErrorCode.RESOURCE_GONE, "回放不存在 → 40330");

            // 已下架同样不可播放
            f.replays.save(new ReplayRecord("RP-REMOVED", null, 10241L, 2001L, "vod-rm",
                    "https://cos/rm.m3u8", 60, NOW, NOW, NOW.plusDays(30),
                    ReplayRecord.ReplayStatus.REMOVED));
            a.eq(codeOf(() -> f.replayService.playUrl("RP-REMOVED")),
                    ErrorCode.RESOURCE_GONE, "已下架回放 → 40330");
        });

        // 12. API-ADM-20 口径目录 / 更新 / 未知键 + API-ADM-29 审计过滤
        registry.add("ADM-20/29 · 口径目录 17 项 + 更新 + 未知键 40330 + 审计关键字过滤", a -> {
            ModerationFixture f = new ModerationFixture();

            List<SysConfigEntry> catalog = f.sysConfigService.list();
            a.eqLong(catalog.size(), 17L, "口径目录 17 项");

            List<SysConfigEntry> after = f.sysConfigService.update(
                    "appeal.window_days", "3", 88, "运营A", "OPS", "1.2.3.4");
            a.eqLong(after.size(), 17L, "更新后仍返回 17 项");
            a.eq(f.sysConfigService.value("appeal_days").orElse(null), "3", "落库用存储键");
            a.eq(codeOf(() -> f.sysConfigService.update("no.such.key", "1",
                    88, "运营A", "OPS", "ip")), ErrorCode.RESOURCE_GONE, "未知键 → 40330");

            f.seedRoom(10241L, 2001L, "小闪");
            f.actionService.forceStop(10241L, 9001L, "R-1102", "涉黄强制关播",
                    77, "审核员A", "AUDITOR", "10.0.0.9");

            a.eqLong(f.audit.list(null).size(), 2L, "全量 2 条审计");
            List<AdminOperationLog> forceStop = f.audit.list("FORCE_STOP");
            a.eqLong(forceStop.size(), 1L, "关键字 FORCE_STOP 命中 1 条");
            a.eq(forceStop.get(0).action(), "FORCE_STOP", "命中动作为 FORCE_STOP");
            a.eqLong(f.audit.list("SYS_CONFIG_UPDATE").size(), 1L, "关键字命中 SYS_CONFIG_UPDATE");
            a.eqLong(f.audit.list("不存在的关键字").size(), 0L, "无匹配 → 0 条");
        });
    }

    /** 回放播放地址断言（抽出避免主 lambda 过长）。 */
    private static void assertPlayInfo(ModerationFixture f, String replayId, Assert a) {
        ReplayService.PlayInfo play = f.replayService.playUrl(replayId);
        a.eq(play.protocol(), "hls", "播放协议 hls");
        a.eq(play.url(), "https://cos/vod-1.m3u8", "播放地址回吐");
        a.eq(play.expireAt(), NOW.plusDays(30), "播放地址带过期时间");
    }

    private static ModerationAlertEvent alert(String eventId, int confidence) {
        return new ModerationAlertEvent(eventId, "CSS", 10241L, 10241L, 2001L, "小闪", 9001L,
                AlertType.IMAGE, "疑似违规截图", "https://cos/snap.jpg", "R-1102", confidence, NOW);
    }

    private static DispositionCommand cmd(String taskId, ModerationActionType type,
                                          String reasonCode, String remark) {
        return new DispositionCommand(taskId, 10241L, 9001L, null, type, reasonCode, remark,
                List.of("https://cos/ev.jpg"), List.of("INBOX"), 77L, "审核员A");
    }

    private static RecordingCompletedEvent recording(String eventId, String vodFileId) {
        return new RecordingCompletedEvent(eventId, 10241L, 2001L, 9001L, vodFileId,
                "https://cos/" + vodFileId + ".m3u8", 3600, NOW.minusHours(1), NOW);
    }

    private static ErrorCode codeOf(Runnable body) {
        try {
            body.run();
            return null;
        } catch (BizException e) {
            return e.errorCode();
        }
    }

    private static String fieldOf(Runnable body) {
        try {
            body.run();
            return null;
        } catch (BizException e) {
            return e.detailField();
        }
    }
}
