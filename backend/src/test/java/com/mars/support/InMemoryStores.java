package com.mars.support;

import com.mars.asset.gift.GiftCatalog;
import com.mars.asset.gift.GiftItem;
import com.mars.asset.gift.GiftOrder;
import com.mars.asset.iap.IapTransaction;
import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.ledger.EarningLedgerEntry;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.pool.CoinPoolState;
import com.mars.asset.pool.PoolChannel;
import com.mars.asset.recharge.RechargeOrder;
import com.mars.asset.repo.CoinPoolRepository;
import com.mars.asset.repo.EarningAccountRepository;
import com.mars.asset.repo.EarningLedgerRepository;
import com.mars.asset.repo.GiftOrderRepository;
import com.mars.asset.repo.IapTransactionRepository;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.asset.repo.WithdrawalOrderRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.Snapshotable;
import com.mars.earning.WithdrawalOrder;
import com.mars.moderation.AdminOperationLog;
import com.mars.moderation.Appeal;
import com.mars.moderation.ContentReport;
import com.mars.moderation.ModerationAction;
import com.mars.moderation.ModerationEvidence;
import com.mars.moderation.ModerationTask;
import com.mars.moderation.ReplayRecord;
import com.mars.moderation.RoomBrief;
import com.mars.moderation.SysConfigEntry;
import com.mars.moderation.TaskStatus;
import com.mars.moderation.repo.AdminOperationLogRepository;
import com.mars.moderation.repo.AppealRepository;
import com.mars.moderation.repo.ContentReportRepository;
import com.mars.moderation.repo.ModerationActionRepository;
import com.mars.moderation.repo.ModerationEvidenceRepository;
import com.mars.moderation.repo.ModerationTaskRepository;
import com.mars.moderation.repo.ReplayRecordRepository;
import com.mars.moderation.repo.RoomLookup;
import com.mars.moderation.repo.SysConfigRepository;
import com.mars.moderation.repo.WebhookEventRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 内存版仓储（测试替身）。每个 store 都是 {@link Snapshotable}，配合
 * {@code InMemoryUnitOfWork} 即可<b>真实验证资金链路的原子性</b>（异常即整体回滚）。
 *
 * <p>唯一约束在这里以「已存在即抛冲突」模拟，等价于 DB 的 unique index —— 幂等与
 * 「不重复扣费」的保证主要落在这一层。
 */
public final class InMemoryStores {

    private InMemoryStores() {
    }

    /** 钱包账户。 */
    public static final class Wallets implements WalletAccountRepository, Snapshotable {
        private final Map<Long, WalletAccount> byUser = new HashMap<>();

        @Override
        public Optional<WalletAccount> find(long userId) {
            return Optional.ofNullable(byUser.get(userId));
        }

        @Override
        public void save(WalletAccount account, long expectedVersion) {
            WalletAccount current = byUser.get(account.userId());
            long currentVersion = current == null ? 0L : current.version();
            if (currentVersion != expectedVersion) {
                throw new BizException(ErrorCode.CONFLICT, "钱包版本冲突", "version", "stale");
            }
            byUser.put(account.userId(), account);
        }

        public void seed(WalletAccount account) {
            byUser.put(account.userId(), account);
        }

        @Override
        public Object snapshot() {
            return new HashMap<>(byUser);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byUser.clear();
            byUser.putAll((Map<Long, WalletAccount>) snapshot);
        }
    }

    /** 钱包流水（{@code biz_no} 唯一）。 */
    public static final class WalletLedgers implements WalletLedgerRepository, Snapshotable {
        private final List<WalletLedgerEntry> entries = new ArrayList<>();
        private final Map<String, WalletLedgerEntry> byBizNo = new HashMap<>();

        @Override
        public void append(WalletLedgerEntry entry) {
            if (byBizNo.putIfAbsent(entry.bizNo(), entry) != null) {
                throw new BizException(ErrorCode.CONFLICT, "流水号重复", "biz_no", "duplicate");
            }
            entries.add(entry);
        }

        @Override
        public List<WalletLedgerEntry> findByUser(long userId) {
            List<WalletLedgerEntry> result = new ArrayList<>();
            for (WalletLedgerEntry entry : entries) {
                if (entry.userId() == userId) {
                    result.add(entry);
                }
            }
            return result;
        }

        @Override
        public Optional<WalletLedgerEntry> findByBizNo(String bizNo) {
            return Optional.ofNullable(byBizNo.get(bizNo));
        }

        @Override
        public long countByBizNo(String bizNo) {
            long count = 0L;
            for (WalletLedgerEntry entry : entries) {
                if (entry.bizNo().equals(bizNo)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Object snapshot() {
            return new Object[]{new ArrayList<>(entries), new HashMap<>(byBizNo)};
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            Object[] parts = (Object[]) snapshot;
            entries.clear();
            entries.addAll((List<WalletLedgerEntry>) parts[0]);
            byBizNo.clear();
            byBizNo.putAll((Map<String, WalletLedgerEntry>) parts[1]);
        }
    }

    /** 收益账户。 */
    public static final class Earnings implements EarningAccountRepository, Snapshotable {
        private final Map<Long, EarningAccount> byAnchor = new HashMap<>();

        @Override
        public Optional<EarningAccount> find(long anchorId) {
            return Optional.ofNullable(byAnchor.get(anchorId));
        }

        @Override
        public void save(EarningAccount account, long expectedVersion) {
            EarningAccount current = byAnchor.get(account.anchorId());
            long currentVersion = current == null ? 0L : current.version();
            if (currentVersion != expectedVersion) {
                throw new BizException(ErrorCode.CONFLICT, "收益账户版本冲突", "version", "stale");
            }
            byAnchor.put(account.anchorId(), account);
        }

        public void seed(EarningAccount account) {
            byAnchor.put(account.anchorId(), account);
        }

        @Override
        public Object snapshot() {
            return new HashMap<>(byAnchor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byAnchor.clear();
            byAnchor.putAll((Map<Long, EarningAccount>) snapshot);
        }
    }

    /** 收益流水。 */
    public static final class EarningLedgers implements EarningLedgerRepository, Snapshotable {
        private final List<EarningLedgerEntry> entries = new ArrayList<>();
        private final Map<String, EarningLedgerEntry> byBizNo = new HashMap<>();

        @Override
        public void append(EarningLedgerEntry entry) {
            if (byBizNo.putIfAbsent(entry.bizNo(), entry) != null) {
                throw new BizException(ErrorCode.CONFLICT, "收益流水号重复", "biz_no", "duplicate");
            }
            entries.add(entry);
        }

        @Override
        public List<EarningLedgerEntry> findByAnchor(long anchorId) {
            List<EarningLedgerEntry> result = new ArrayList<>();
            for (EarningLedgerEntry entry : entries) {
                if (entry.anchorId() == anchorId) {
                    result.add(entry);
                }
            }
            return result;
        }

        @Override
        public Optional<EarningLedgerEntry> findByBizNo(String bizNo) {
            return Optional.ofNullable(byBizNo.get(bizNo));
        }

        @Override
        public long countByBizNo(String bizNo) {
            long count = 0L;
            for (EarningLedgerEntry entry : entries) {
                if (entry.bizNo().equals(bizNo)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Object snapshot() {
            return new Object[]{new ArrayList<>(entries), new HashMap<>(byBizNo)};
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            Object[] parts = (Object[]) snapshot;
            entries.clear();
            entries.addAll((List<EarningLedgerEntry>) parts[0]);
            byBizNo.clear();
            byBizNo.putAll((Map<String, EarningLedgerEntry>) parts[1]);
        }
    }

    /** 礼物订单（{@code idempotency_key} 唯一）。 */
    public static final class GiftOrders implements GiftOrderRepository, Snapshotable {
        private final Map<String, GiftOrder> byKey = new HashMap<>();

        @Override
        public Optional<GiftOrder> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(byKey.get(idempotencyKey));
        }

        @Override
        public void save(GiftOrder order) {
            if (byKey.putIfAbsent(order.idempotencyKey(), order) != null) {
                throw new BizException(ErrorCode.CONFLICT, "幂等键重复", "idempotency_key", "duplicate");
            }
        }

        @Override
        public List<GiftOrder> findBySender(long senderId) {
            List<GiftOrder> result = new ArrayList<>();
            for (GiftOrder order : byKey.values()) {
                if (order.senderId() == senderId) {
                    result.add(order);
                }
            }
            return result;
        }

        @Override
        public Object snapshot() {
            return new HashMap<>(byKey);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byKey.clear();
            byKey.putAll((Map<String, GiftOrder>) snapshot);
        }
    }

    /** 充值订单。 */
    public static final class Recharges implements RechargeOrderRepository, Snapshotable {
        private final Map<String, RechargeOrder> byOrderNo = new HashMap<>();

        @Override
        public Optional<RechargeOrder> findByOrderNo(String orderNo) {
            return Optional.ofNullable(byOrderNo.get(orderNo));
        }

        @Override
        public void save(RechargeOrder order) {
            byOrderNo.put(order.orderNo(), order);
        }

        @Override
        public Object snapshot() {
            return new HashMap<>(byOrderNo);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byOrderNo.clear();
            byOrderNo.putAll((Map<String, RechargeOrder>) snapshot);
        }
    }

    /** IAP 交易（{@code transaction_id} 唯一）。 */
    public static final class IapTransactions implements IapTransactionRepository, Snapshotable {
        private final Map<String, IapTransaction> byId = new HashMap<>();

        @Override
        public Optional<IapTransaction> findByTransactionId(String transactionId) {
            return Optional.ofNullable(byId.get(transactionId));
        }

        @Override
        public void save(IapTransaction transaction) {
            byId.put(transaction.transactionId(), transaction);
        }

        @Override
        public Object snapshot() {
            return new HashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, IapTransaction>) snapshot);
        }
    }

    /** 币池（单例）。 */
    public static final class Pools implements CoinPoolRepository, Snapshotable {
        private CoinPoolState state = new CoinPoolState();

        @Override
        public CoinPoolState load() {
            return state;
        }

        @Override
        public void save(CoinPoolState pool) {
            this.state = pool;
        }

        @Override
        public Object snapshot() {
            CoinPoolState copy = new CoinPoolState();
            for (PoolChannel channel : state.channels()) {
                copy.credit(channel.channel(), channel.coins(), channel.grossFen(),
                        channel.feeFen(), channel.feeRate());
            }
            return copy;
        }

        @Override
        public void restore(Object snapshot) {
            this.state = (CoinPoolState) snapshot;
        }
    }

    /** 提现单。 */
    public static final class WithdrawalOrders implements WithdrawalOrderRepository, Snapshotable {
        private final Map<String, WithdrawalOrder> byOrderNo = new HashMap<>();

        @Override
        public Optional<WithdrawalOrder> findByOrderNo(String orderNo) {
            return Optional.ofNullable(byOrderNo.get(orderNo));
        }

        @Override
        public void save(WithdrawalOrder order) {
            byOrderNo.put(order.orderNo(), order);
        }

        @Override
        public List<WithdrawalOrder> findByAnchor(long anchorId) {
            List<WithdrawalOrder> result = new ArrayList<>();
            for (WithdrawalOrder order : byOrderNo.values()) {
                if (order.anchorId() == anchorId) {
                    result.add(order);
                }
            }
            return result;
        }

        @Override
        public Object snapshot() {
            return new HashMap<>(byOrderNo);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byOrderNo.clear();
            byOrderNo.putAll((Map<String, WithdrawalOrder>) snapshot);
        }
    }

    /** 礼物目录。 */
    public static final class GiftCatalogImpl implements GiftCatalog {        private final Map<Long, GiftItem> byId = new HashMap<>();

        public void put(GiftItem item) {
            byId.put(item.id(), item);
        }

        @Override
        public Optional<GiftItem> find(long giftId) {
            return Optional.ofNullable(byId.get(giftId));
        }
    }

    // ------------------------------------------------------------------
    // 审核域 + 平台口径 + 回放 + 审计（AC-7 / AC-9 · 契约 §4.8 / §4.9）
    // ------------------------------------------------------------------

    /** 审核任务（{@code event_id} 唯一；{@code save} 兼作插入 / 更新）。 */
    public static final class ModerationTasks implements ModerationTaskRepository, Snapshotable {
        private final Map<String, ModerationTask> byId = new LinkedHashMap<>();

        @Override
        public void save(ModerationTask task) {
            byId.put(task.taskId(), task);
        }

        @Override
        public Optional<ModerationTask> findById(String taskId) {
            return Optional.ofNullable(byId.get(taskId));
        }

        @Override
        public Optional<ModerationTask> findByEventId(String eventId) {
            for (ModerationTask task : byId.values()) {
                if (Objects.equals(task.eventId(), eventId)) {
                    return Optional.of(task);
                }
            }
            return Optional.empty();
        }

        /** 队列：风险等级升序（高在前）再按入队时间升序；{@code status=null} 取全部。 */
        @Override
        public List<ModerationTask> listByStatus(TaskStatus status) {
            List<ModerationTask> result = new ArrayList<>();
            for (ModerationTask task : byId.values()) {
                if (status == null || task.status() == status) {
                    result.add(task);
                }
            }
            result.sort(Comparator
                    .comparingInt((ModerationTask task) -> task.riskLevel().rank())
                    .thenComparing(task -> task.createdAt() == null ? LocalDateTime.MIN : task.createdAt()));
            return result;
        }

        /** 仅 {@code PENDING} 可认领（并发抢单防重）。 */
        @Override
        public boolean claim(String taskId, long operatorId, String operatorName) {
            ModerationTask current = byId.get(taskId);
            if (current == null || current.status() != TaskStatus.PENDING) {
                return false;
            }
            byId.put(taskId, current.claimedBy(operatorId, operatorName));
            return true;
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, ModerationTask>) snapshot);
        }
    }

    /** 处置记录（不可删；{@code save} 兼作插入 / 申诉解除更新）。 */
    public static final class ModerationActions implements ModerationActionRepository, Snapshotable {
        private final Map<String, ModerationAction> byId = new LinkedHashMap<>();

        @Override
        public void save(ModerationAction action) {
            byId.put(action.actionId(), action);
        }

        @Override
        public Optional<ModerationAction> findById(String actionId) {
            return Optional.ofNullable(byId.get(actionId));
        }

        @Override
        public List<ModerationAction> listByTask(String taskId) {
            List<ModerationAction> result = new ArrayList<>();
            for (ModerationAction action : byId.values()) {
                if (Objects.equals(action.taskId(), taskId)) {
                    result.add(action);
                }
            }
            return result;
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, ModerationAction>) snapshot);
        }
    }

    /** 处置证据（{@code action_id} 唯一，不可删）。 */
    public static final class ModerationEvidences implements ModerationEvidenceRepository, Snapshotable {
        private final Map<String, ModerationEvidence> byActionId = new LinkedHashMap<>();

        @Override
        public void save(ModerationEvidence evidence) {
            byActionId.put(evidence.actionId(), evidence);
        }

        @Override
        public Optional<ModerationEvidence> findByActionId(String actionId) {
            return Optional.ofNullable(byActionId.get(actionId));
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byActionId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byActionId.clear();
            byActionId.putAll((Map<String, ModerationEvidence>) snapshot);
        }
    }

    /** 用户举报。 */
    public static final class ContentReports implements ContentReportRepository, Snapshotable {
        private final Map<String, ContentReport> byId = new LinkedHashMap<>();

        @Override
        public void save(ContentReport report) {
            byId.put(report.reportId(), report);
        }

        @Override
        public Optional<ContentReport> findById(String reportId) {
            return Optional.ofNullable(byId.get(reportId));
        }

        @Override
        public List<ContentReport> listByStatus(ContentReport.ReportStatus status) {
            List<ContentReport> result = new ArrayList<>();
            for (ContentReport report : byId.values()) {
                if (status == null || report.status() == status) {
                    result.add(report);
                }
            }
            result.sort(Comparator.comparing(report -> report.createdAt() == null
                    ? LocalDateTime.MIN : report.createdAt()));
            return result;
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, ContentReport>) snapshot);
        }
    }

    /** 申诉（{@code action_id} 唯一）。 */
    public static final class Appeals implements AppealRepository, Snapshotable {
        private final Map<String, Appeal> byId = new LinkedHashMap<>();

        @Override
        public void save(Appeal appeal) {
            byId.put(appeal.appealId(), appeal);
        }

        @Override
        public Optional<Appeal> findById(String appealId) {
            return Optional.ofNullable(byId.get(appealId));
        }

        @Override
        public Optional<Appeal> findByActionId(String actionId) {
            for (Appeal appeal : byId.values()) {
                if (Objects.equals(appeal.actionId(), actionId)) {
                    return Optional.of(appeal);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<Appeal> listByStatus(Appeal.AppealStatus status) {
            List<Appeal> result = new ArrayList<>();
            for (Appeal appeal : byId.values()) {
                if (status == null || appeal.status() == status) {
                    result.add(appeal);
                }
            }
            result.sort(Comparator.comparing(appeal -> appeal.createdAt() == null
                    ? LocalDateTime.MIN : appeal.createdAt()));
            return result;
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, Appeal>) snapshot);
        }
    }

    /** 回放记录（{@code vod_file_id} 唯一）。 */
    public static final class ReplayRecords implements ReplayRecordRepository, Snapshotable {
        private final Map<String, ReplayRecord> byId = new LinkedHashMap<>();

        @Override
        public void save(ReplayRecord replay) {
            byId.put(replay.replayId(), replay);
        }

        @Override
        public Optional<ReplayRecord> findById(String replayId) {
            return Optional.ofNullable(byId.get(replayId));
        }

        @Override
        public Optional<ReplayRecord> findByVodFileId(String vodFileId) {
            for (ReplayRecord replay : byId.values()) {
                if (Objects.equals(replay.vodFileId(), vodFileId)) {
                    return Optional.of(replay);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<ReplayRecord> listByRoom(long roomId) {
            List<ReplayRecord> result = new ArrayList<>();
            for (ReplayRecord replay : byId.values()) {
                if (replay.roomId() == roomId) {
                    result.add(replay);
                }
            }
            result.sort(Comparator.comparing((ReplayRecord replay) -> replay.startedAt() == null
                    ? LocalDateTime.MIN : replay.startedAt()).reversed());
            return result;
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, ReplayRecord>) snapshot);
        }
    }

    /** 平台口径（{@code sys_config}）：只存「键 → 覆盖值 + 备注」，类型由目录提供。 */
    public static final class SysConfigs implements SysConfigRepository, Snapshotable {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, String> notes = new LinkedHashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value, String description) {
            values.put(key, value);
            if (description != null) {
                notes.put(key, description);
            }
        }

        @Override
        public List<SysConfigEntry> list() {
            List<SysConfigEntry> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result.add(new SysConfigEntry(entry.getKey(), null, null, entry.getValue(),
                        notes.get(entry.getKey())));
            }
            return result;
        }

        @Override
        public Object snapshot() {
            return new Object[]{new LinkedHashMap<>(values), new LinkedHashMap<>(notes)};
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            Object[] parts = (Object[]) snapshot;
            values.clear();
            values.putAll((Map<String, String>) parts[0]);
            notes.clear();
            notes.putAll((Map<String, String>) parts[1]);
        }
    }

    /** 后台操作审计（仅追加）。 */
    public static final class AdminOperationLogs implements AdminOperationLogRepository, Snapshotable {
        private final List<AdminOperationLog> logs = new ArrayList<>();

        @Override
        public void append(AdminOperationLog log) {
            logs.add(log);
        }

        /** 按时间倒序；{@code keyword} 命中操作者 / 动作 / 目标 / 原因码 / 备注 / IP 即入选。 */
        @Override
        public List<AdminOperationLog> list(String keyword) {
            List<AdminOperationLog> result = new ArrayList<>();
            for (AdminOperationLog log : logs) {
                if (keyword == null || keyword.isBlank() || matches(log, keyword.trim())) {
                    result.add(log);
                }
            }
            result.sort(Comparator.comparing((AdminOperationLog log) -> log.createdAt() == null
                    ? LocalDateTime.MIN : log.createdAt()).reversed());
            return result;
        }

        private static boolean matches(AdminOperationLog log, String keyword) {
            return contains(log.operatorName(), keyword)
                    || contains(log.action(), keyword)
                    || contains(log.targetType(), keyword)
                    || contains(log.targetLabel(), keyword)
                    || contains(log.reasonCode(), keyword)
                    || contains(log.remark(), keyword)
                    || contains(log.ip(), keyword);
        }

        private static boolean contains(String value, String keyword) {
            return value != null && value.contains(keyword);
        }

        @Override
        public Object snapshot() {
            return new ArrayList<>(logs);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            logs.clear();
            logs.addAll((List<AdminOperationLog>) snapshot);
        }
    }

    /** 回调事件幂等（主键唯一：首次 true，重复 false）。 */
    public static final class WebhookEvents implements WebhookEventRepository, Snapshotable {
        private final Set<String> seen = new LinkedHashSet<>();

        @Override
        public boolean register(String eventId, String source) {
            return seen.add(eventId);
        }

        @Override
        public Object snapshot() {
            return new LinkedHashSet<>(seen);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            seen.clear();
            seen.addAll((Set<String>) snapshot);
        }
    }

    /** 直播间查询（{@code live_room} ⋈ {@code user} 的内存替身）。 */
    public static final class Rooms implements RoomLookup, Snapshotable {
        private final Map<Long, RoomBrief> byId = new LinkedHashMap<>();

        public void seed(RoomBrief room) {
            byId.put(room.roomId(), room);
        }

        @Override
        public Optional<RoomBrief> findById(long roomId) {
            return Optional.ofNullable(byId.get(roomId));
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<Long, RoomBrief>) snapshot);
        }
    }

    /** 确定性单号生成器（可复算）：{@code <BIZ>-<业务类型内序号>}。 */
    public static final class SeqOrderNoGenerator implements OrderNoGenerator {
        private final Map<String, Integer> counters = new HashMap<>();

        @Override
        public String next(String bizType) {
            int next = counters.merge(bizType, 1, Integer::sum);
            return bizType + "-" + next;
        }
    }
}
