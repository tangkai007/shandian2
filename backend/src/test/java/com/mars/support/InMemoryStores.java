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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
