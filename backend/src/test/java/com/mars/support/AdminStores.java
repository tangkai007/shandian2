package com.mars.support;

import com.mars.admin.model.AdminRoomView;
import com.mars.admin.model.AdminWithdrawalView;
import com.mars.admin.model.Banner;
import com.mars.admin.model.Category;
import com.mars.admin.model.GiftAdminItem;
import com.mars.admin.model.Invoice;
import com.mars.admin.model.RealnameRecord;
import com.mars.admin.model.RefreshToken;
import com.mars.admin.model.SettlementBillEntity;
import com.mars.admin.model.UserAccount;
import com.mars.admin.repo.AdminRoomRepository;
import com.mars.admin.repo.AdminWithdrawalQuery;
import com.mars.admin.repo.CatalogRepository;
import com.mars.admin.repo.GiftAdminRepository;
import com.mars.admin.repo.IdentityRepository;
import com.mars.admin.repo.InvoiceRepository;
import com.mars.admin.repo.RealnameRepository;
import com.mars.admin.repo.RefreshTokenRepository;
import com.mars.admin.repo.SettlementBillRepository;
import com.mars.admin.repo.SettlementSource;
import com.mars.admin.repo.UserAccountRepository;
import com.mars.asset.iap.IapProductCatalog;
import com.mars.asset.pool.CoinPoolState;
import com.mars.asset.repo.WithdrawalOrderRepository;
import com.mars.common.tx.Snapshotable;
import com.mars.earning.WithdrawalOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 后台域内存仓储（测试替身）。与 {@link InMemoryStores} 同风格：唯一约束以「已存在即拒绝」模拟，
 * 需要参与事务回滚的实现 {@link Snapshotable}。
 */
public final class AdminStores {

    private AdminStores() {
    }

    /** 用户账号（{@code user} ⋈ {@code user_auth} ⋈ {@code live_room}）。 */
    public static final class UserAccounts implements UserAccountRepository, Snapshotable {
        private final Map<Long, UserAccount> byId = new LinkedHashMap<>();
        private long seq = 0L;

        public void seed(UserAccount account) {
            byId.put(account.userId(), account);
            if (account.userId() > seq) {
                seq = account.userId();
            }
        }

        @Override
        public List<UserAccount> search(String keyword) {
            String needle = keyword == null ? "" : keyword.trim();
            List<UserAccount> result = new ArrayList<>();
            for (UserAccount account : byId.values()) {
                if (needle.isEmpty() || matches(account, needle)) {
                    result.add(account);
                }
            }
            return result;
        }

        private static boolean matches(UserAccount account, String needle) {
            return contains(account.nickname(), needle)
                    || contains(account.phoneHash(), needle)
                    || String.valueOf(account.userId()).contains(needle);
        }

        private static boolean contains(String value, String needle) {
            return value != null && value.contains(needle);
        }

        @Override
        public Optional<UserAccount> findById(long userId) {
            return Optional.ofNullable(byId.get(userId));
        }

        @Override
        public void save(UserAccount account) {
            byId.put(account.userId(), account);
            if (account.userId() > seq) {
                seq = account.userId();
            }
        }

        @Override
        public long insertUser(String nickname, String phoneHash) {
            long id = ++seq;
            byId.put(id, new UserAccount(id, nickname, phoneHash, UserAccount.STATUS_NORMAL,
                    false, false, null, null, null));
            return id;
        }

        @Override
        public Optional<String> nicknameOf(long userId) {
            return Optional.ofNullable(byId.get(userId)).map(UserAccount::nickname);
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<Long, UserAccount>) snapshot);
        }
    }

    /** 登录渠道绑定（{@code user_identity} ⋈ {@code user.phone_hash}）。 */
    public static final class Identities implements IdentityRepository, Snapshotable {
        private final Map<String, Long> byProviderOpenId = new LinkedHashMap<>();
        private final Map<Long, String> phoneHashByUser = new LinkedHashMap<>();

        public void bindPhone(long userId, String phoneHash) {
            phoneHashByUser.put(userId, phoneHash);
        }

        @Override
        public Optional<Long> findUserId(String provider, String openId) {
            return Optional.ofNullable(byProviderOpenId.get(key(provider, openId)));
        }

        @Override
        public void bind(long userId, String provider, String openId, String unionId) {
            byProviderOpenId.put(key(provider, openId), userId);
        }

        @Override
        public boolean hasPhone(long userId) {
            String hash = phoneHashByUser.get(userId);
            return hash != null && !hash.isBlank();
        }

        @Override
        public Optional<Long> findUserIdByPhoneHash(String phoneHash) {
            for (Map.Entry<Long, String> entry : phoneHashByUser.entrySet()) {
                if (Objects.equals(entry.getValue(), phoneHash)) {
                    return Optional.of(entry.getKey());
                }
            }
            return Optional.empty();
        }

        private static String key(String provider, String openId) {
            return provider + "|" + openId;
        }

        @Override
        public Object snapshot() {
            return new Object[]{new LinkedHashMap<>(byProviderOpenId), new LinkedHashMap<>(phoneHashByUser)};
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            Object[] parts = (Object[]) snapshot;
            byProviderOpenId.clear();
            byProviderOpenId.putAll((Map<String, Long>) parts[0]);
            phoneHashByUser.clear();
            phoneHashByUser.putAll((Map<Long, String>) parts[1]);
        }
    }

    /** 房间基本行（{@code live_room} ⋈ {@code user} ⋈ {@code live_category}）。 */
    public static final class AdminRooms implements AdminRoomRepository {
        private final List<RoomRow> rooms = new ArrayList<>();

        public void seed(long roomId, Long roomNo, String anchorName, String categoryName, String status) {
            rooms.add(new RoomRow(roomId, roomNo, anchorName, categoryName, status));
        }

        @Override
        public List<RoomRow> listRooms() {
            List<RoomRow> copy = new ArrayList<>(rooms);
            copy.sort(Comparator.comparingLong(RoomRow::roomId));
            return copy;
        }
    }

    /** 礼物目录（{@code gift}）。 */
    public static final class GiftAdmins implements GiftAdminRepository, Snapshotable {
        private final Map<Long, GiftAdminItem> byId = new LinkedHashMap<>();
        private long seq = 0L;

        public void seed(GiftAdminItem item) {
            byId.put(item.id(), item);
            if (item.id() > seq) {
                seq = item.id();
            }
        }

        @Override
        public List<GiftAdminItem> listAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public Optional<GiftAdminItem> findById(long giftId) {
            return Optional.ofNullable(byId.get(giftId));
        }

        @Override
        public long insert(GiftAdminItem item) {
            long id = ++seq;
            byId.put(id, new GiftAdminItem(id, item.name(), item.priceCoin(), item.effectType(), item.status()));
            return id;
        }

        @Override
        public void update(GiftAdminItem item) {
            byId.put(item.id(), item);
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<Long, GiftAdminItem>) snapshot);
        }
    }

    /** 目录 / 运营位（{@code live_category}、{@code banner}）。 */
    public static final class Catalog implements CatalogRepository, Snapshotable {
        private final Map<Long, Category> categories = new LinkedHashMap<>();
        private final Map<Long, Banner> banners = new LinkedHashMap<>();
        private long seq = 0L;

        public void seedCategory(Category category) {
            categories.put(category.id(), category);
        }

        public void seedBanner(Banner banner) {
            banners.put(banner.id(), banner);
            if (banner.id() > seq) {
                seq = banner.id();
            }
        }

        @Override
        public List<Category> listCategories() {
            return new ArrayList<>(categories.values());
        }

        @Override
        public Optional<Category> findCategory(long id) {
            return Optional.ofNullable(categories.get(id));
        }

        @Override
        public void updateCategory(Category category) {
            categories.put(category.id(), category);
        }

        @Override
        public List<Banner> listBanners() {
            return new ArrayList<>(banners.values());
        }

        @Override
        public Optional<Banner> findBanner(long id) {
            return Optional.ofNullable(banners.get(id));
        }

        @Override
        public long insertBanner(Banner banner) {
            long id = ++seq;
            banners.put(id, new Banner(id, banner.title(), banner.targetType(), banner.targetValue(),
                    banner.startAt(), banner.endAt(), banner.enabled(), banner.sort()));
            return id;
        }

        @Override
        public void updateBanner(Banner banner) {
            banners.put(banner.id(), banner);
        }

        @Override
        public Object snapshot() {
            return new Object[]{new LinkedHashMap<>(categories), new LinkedHashMap<>(banners)};
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            Object[] parts = (Object[]) snapshot;
            categories.clear();
            categories.putAll((Map<Long, Category>) parts[0]);
            banners.clear();
            banners.putAll((Map<Long, Banner>) parts[1]);
        }
    }

    /** 结算账单（{@code settlement_bill} + 渠道明细）。 */
    public static final class SettlementBills implements SettlementBillRepository, Snapshotable {
        private final Map<Long, SettlementBillEntity> byId = new LinkedHashMap<>();
        private long seq = 0L;

        public void seed(SettlementBillEntity entity) {
            byId.put(entity.billId(), entity);
            if (entity.billId() > seq) {
                seq = entity.billId();
            }
        }

        @Override
        public List<SettlementBillEntity> listAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public Optional<SettlementBillEntity> findById(long billId) {
            return Optional.ofNullable(byId.get(billId));
        }

        @Override
        public boolean existsByAnchorAndPeriod(long anchorId, String period) {
            for (SettlementBillEntity entity : byId.values()) {
                if (entity.anchorId() == anchorId && Objects.equals(entity.bill().period(), period)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public long save(SettlementBillEntity entity) {
            long id = ++seq;
            byId.put(id, new SettlementBillEntity(id, entity.anchorId(), entity.bill()));
            return id;
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<Long, SettlementBillEntity>) snapshot);
        }
    }

    /** 发票（{@code invoice}）。 */
    public static final class Invoices implements InvoiceRepository, Snapshotable {
        private final Map<String, Invoice> byId = new LinkedHashMap<>();

        public void seed(Invoice invoice) {
            byId.put(invoice.invoiceId(), invoice);
        }

        @Override
        public List<Invoice> listAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public Optional<Invoice> findById(String invoiceId) {
            return Optional.ofNullable(byId.get(invoiceId));
        }

        @Override
        public void save(Invoice invoice) {
            byId.put(invoice.invoiceId(), invoice);
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byId.clear();
            byId.putAll((Map<String, Invoice>) snapshot);
        }
    }

    /** 实名记录（{@code user_auth}）。 */
    public static final class Realnames implements RealnameRepository, Snapshotable {
        private final Map<Long, RealnameRecord> byUser = new LinkedHashMap<>();

        @Override
        public Optional<RealnameRecord> find(long userId) {
            return Optional.ofNullable(byUser.get(userId));
        }

        @Override
        public void save(RealnameRecord record) {
            byUser.put(record.userId(), record);
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byUser);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byUser.clear();
            byUser.putAll((Map<Long, RealnameRecord>) snapshot);
        }
    }

    /** 刷新令牌（{@code refresh_token}）。 */
    public static final class RefreshTokens implements RefreshTokenRepository, Snapshotable {
        private final Map<String, RefreshToken> byHash = new LinkedHashMap<>();

        @Override
        public void save(RefreshToken token) {
            byHash.put(token.tokenHash(), token);
        }

        @Override
        public Optional<RefreshToken> findByHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public void revoke(String tokenHash) {
            RefreshToken current = byHash.get(tokenHash);
            if (current != null) {
                byHash.put(tokenHash, new RefreshToken(current.tokenHash(), current.userId(),
                        current.expiresAt(), true));
            }
        }

        @Override
        public Object snapshot() {
            return new LinkedHashMap<>(byHash);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(Object snapshot) {
            byHash.clear();
            byHash.putAll((Map<String, RefreshToken>) snapshot);
        }
    }

    /**
     * 财务提现行查询（{@code withdrawal} ⋈ {@code user} ⋈ {@code user_auth}）。
     *
     * <p>状态/驳回原因以 {@code withdrawal} 表的 {@link WithdrawalOrder} 为准（单一事实来源），
     * 展示字段（主播名 / 实名 / 银行卡）由 seed 提供 —— 与 DB 查询语义一致。
     */
    public static final class AdminWithdrawals implements AdminWithdrawalQuery {
        private final Map<String, AdminWithdrawalView> byOrderNo = new LinkedHashMap<>();
        private final WithdrawalOrderRepository orders;

        public AdminWithdrawals(WithdrawalOrderRepository orders) {
            this.orders = orders;
        }

        public void seed(AdminWithdrawalView view) {
            byOrderNo.put(view.orderNo(), view);
        }

        @Override
        public List<AdminWithdrawalView> list(String period) {
            List<AdminWithdrawalView> result = new ArrayList<>();
            for (AdminWithdrawalView view : byOrderNo.values()) {
                if (period == null || period.isBlank() || period.equals(view.period())) {
                    result.add(merge(view));
                }
            }
            return result;
        }

        @Override
        public Optional<AdminWithdrawalView> find(String orderNo) {
            AdminWithdrawalView base = byOrderNo.get(orderNo);
            if (base != null) {
                return Optional.of(merge(base));
            }
            return orders.findByOrderNo(orderNo).map(order -> new AdminWithdrawalView(
                    order.orderNo(), "", "", "", order.amountFen(), order.taxFen(),
                    order.netAmountFen(), "", false, order.status(), order.rejectReason(), null));
        }

        private AdminWithdrawalView merge(AdminWithdrawalView base) {
            return orders.findByOrderNo(base.orderNo())
                    .map(order -> new AdminWithdrawalView(base.orderNo(), base.period(), base.anchorName(),
                            base.realName(), base.preTaxAmount(), base.taxWithheld(), base.netAmount(),
                            base.bankAccountMasked(), base.bankNameMatch(), order.status(),
                            order.rejectReason(), base.paidAt()))
                    .orElse(base);
        }
    }

    /** 月结取数固定来源（测试用；生产替换为币池快照驱动）。 */
    public static final class FixedSettlementSource implements SettlementSource {
        private final List<AnchorSettlementInput> inputs = new ArrayList<>();

        public void seed(long anchorId, CoinPoolState pool, long consumedCoin) {
            inputs.add(new AnchorSettlementInput(anchorId, pool, consumedCoin));
        }

        @Override
        public List<AnchorSettlementInput> inputsFor(String period) {
            return new ArrayList<>(inputs);
        }
    }

    /** IAP 商品目录（测试用）。 */
    public static final class IapProducts implements IapProductCatalog {
        private final Map<String, IapProduct> byId = new LinkedHashMap<>();

        public void seed(String productId, long coinAmount, long amountFen, String priceTier, String status) {
            byId.put(productId, new IapProduct(productId, coinAmount, amountFen, priceTier, status));
        }

        @Override
        public Optional<IapProduct> find(String productId) {
            return Optional.ofNullable(byId.get(productId));
        }

        @Override
        public List<IapProduct> all() {
            return new ArrayList<>(byId.values());
        }
    }
}
