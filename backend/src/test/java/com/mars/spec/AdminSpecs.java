package com.mars.spec;

import com.mars.admin.model.AdminRoomView;
import com.mars.admin.model.AdminWithdrawalView;
import com.mars.admin.model.Banner;
import com.mars.admin.model.Category;
import com.mars.admin.model.GiftAdminItem;
import com.mars.admin.model.Invoice;
import com.mars.admin.model.RealnameRecord;
import com.mars.admin.model.SettlementBillEntity;
import com.mars.admin.model.UserAccount;
import com.mars.admin.service.ChannelLoginService;
import com.mars.admin.service.FinanceAdminService;
import com.mars.admin.service.RefreshTokenService;
import com.mars.asset.iap.IapTransaction;
import com.mars.asset.iap.IapVerifyCommand;
import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.pool.CoinPoolState;
import com.mars.common.BizException;
import com.mars.common.security.AdminOperator;
import com.mars.earning.AnchorType;
import com.mars.earning.TaxMode;
import com.mars.earning.WithdrawalCommand;
import com.mars.earning.WithdrawalOrder;
import com.mars.earning.settlement.SettlementBill;
import com.mars.moderation.AdminOperationLog;
import com.mars.moderation.AlertType;
import com.mars.moderation.ModerationTask;
import com.mars.moderation.RiskLevel;
import com.mars.moderation.TaskStatus;
import com.mars.support.AdminFixture;
import com.mars.testkit.Assert;
import com.mars.testkit.Registry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 后台 / 账号 / 财务 / IAP spec（增量 5 · 契约 §4.1 / §4.9 · FR-11.1/11.2/11.3 · AC-1 / AC-6 / AC-8）。
 *
 * <p>覆盖：用户管理（含审计昵称来源，闭合「已知限制 9」）→ 巡房锁聚合 → 礼物目录 →
 * 类目 / 运营位 → 财务（月结幂等 / 对账冻结 / 提现审核打款 / 发票）→ 实名状态机（5 次锁定）→
 * 渠道登录 + refresh 轮换 / 登出 → IAP 幂等加币与退款拒收。
 */
public final class AdminSpecs {

    private static final LocalDateTime NOW = LocalDateTime.ofInstant(AdminFixture.NOW, ZoneOffset.UTC);
    private static final AdminOperator OPS = new AdminOperator(1001L, "1001", "ops", "10.0.0.1");
    private static final AdminOperator FINANCE = new AdminOperator(1001L, "1001", "finance", "10.0.0.2");

    private AdminSpecs() {
    }

    public static void register(Registry registry) {

        // 1. ADM-01/02/03 用户管理 + 审计昵称来源（闭合已知限制 9）
        registry.add("ADM-01/02/03 · 用户检索/封禁/解封 + 审计昵称解析", a -> {
            AdminFixture f = new AdminFixture();
            f.users.seed(new UserAccount(1001L, "小闪", "a1b2c3d4e5f6", UserAccount.STATUS_NORMAL,
                    true, false, 2001L, NOW, null));
            f.users.seed(new UserAccount(1002L, "阿明", "ffff0000aaaa", UserAccount.STATUS_NORMAL,
                    false, false, null, NOW, null));

            a.eqLong(f.adminUserService.list("").size(), 2L, "空关键字取全部");
            a.eqLong(f.adminUserService.list("小闪").size(), 1L, "按昵称命中");
            a.eqLong(f.adminUserService.list("1002").size(), 1L, "按用户 ID 命中");
            a.eqLong(f.adminUserService.list("zzz").size(), 0L, "无命中");

            UserAccount banned = f.adminUserService.ban(1001L, "VIOLATION", "涉黄", OPS);
            a.eq(banned.status(), UserAccount.STATUS_BANNED, "封禁置 BANNED");
            a.eq(banned.banReason(), "VIOLATION", "封禁原因落库");

            List<AdminOperationLog> logs = f.auditLogs.list("USER_BAN");
            a.eqLong(logs.size(), 1L, "封禁写审计");
            AdminOperationLog log = logs.get(0);
            a.eq(log.operatorName(), "小闪", "审计昵称由 user.nickname 解析（闭合限制 9）");
            a.eq(log.operatorRole(), "ops", "审计角色小写落库");
            a.eq(log.action(), "USER_BAN", "审计动作名");
            a.eqLong(log.operatorId(), 1001L, "审计操作者 ID");
            a.eq(log.targetType(), "USER", "审计目标类型");
            a.eq(log.reasonCode(), "VIOLATION", "审计原因码");

            UserAccount unbanned = f.adminUserService.unban(1001L, OPS);
            a.eq(unbanned.status(), UserAccount.STATUS_NORMAL, "解封置 NORMAL");
            a.isNull(unbanned.banReason(), "解封清空原因");

            a.throwsType(BizException.class,
                    () -> f.adminUserService.ban(9999L, "X", "y", OPS), "用户不存在 → 40330");
        });

        // 2. ADM-04 巡房列表：任务级认领聚合为房间锁
        registry.add("ADM-04 · 巡房列表（locked_by 聚合 + risk_score + alerts 倒序）", a -> {
            AdminFixture f = new AdminFixture();
            f.adminRooms.seed(10241L, 10241L, "小闪", "才艺", "LIVE");
            f.adminRooms.seed(10242L, 10242L, "阿明", "游戏", "LIVE");

            f.tasks.save(task("t-1", 10241L, 90, TaskStatus.PENDING));
            f.tasks.save(task("t-1", 10241L, 90, TaskStatus.PENDING).claimedBy(5001L, "审核员甲"));
            f.tasks.save(task("t-2", 10241L, 40, TaskStatus.PENDING));
            f.tasks.save(task("t-3", 10242L, 99, TaskStatus.DONE));

            List<AdminRoomView> views = f.adminRoomService.list();
            a.eqLong(views.size(), 2L, "全部房间");

            AdminRoomView room1 = views.get(0);
            a.eqLong(room1.roomId(), 10241L, "房 1");
            a.eqLong(room1.lockedBy(), 5001L, "锁定人取 CLAIMED 任务认领者");
            a.eq(room1.lockedByName(), "审核员甲", "锁定人展示名");
            a.eqLong(room1.riskScore(), 90L, "风险分取未关闭任务最高置信度");
            a.eqLong(room1.alerts().size(), 2L, "未关闭告警 2 条");
            a.eqLong(room1.alerts().get(0).confidence(), 90L, "告警按置信度倒序");

            AdminRoomView room2 = views.get(1);
            a.isNull(room2.lockedBy(), "无 CLAIMED 任务 → 未锁定");
            a.eqLong(room2.riskScore(), 0L, "DONE 任务不计入风险分");
            a.eqLong(room2.alerts().size(), 0L, "DONE 任务不计入告警");
        });

        // 3. ADM-14/15/16 礼物目录
        registry.add("ADM-14/15/16 · 礼物列表/新建/局部更新", a -> {
            AdminFixture f = new AdminFixture();
            f.gifts.seed(new GiftAdminItem(1L, "玫瑰", 10L, "STATIC", GiftAdminItem.STATUS_ON));

            a.eqLong(f.giftAdminService.list().size(), 1L, "列表");

            GiftAdminItem created = f.giftAdminService.create("火箭", 1000L, "ANIM", OPS);
            a.eqLong(created.id(), 2L, "自增主键");
            a.eq(created.status(), GiftAdminItem.STATUS_ON, "新建默认在售");
            a.eq(created.effectType(), "ANIM", "动效类型落库");

            GiftAdminItem updated = f.giftAdminService.update(2L, null, 2000L, "OFF", OPS);
            a.eq(updated.name(), "火箭", "null 字段保持不变（名称）");
            a.eqLong(updated.priceCoin(), 2000L, "价格更新");
            a.eq(updated.status(), GiftAdminItem.STATUS_OFF, "状态更新");
            a.eqLong(f.auditLogs.list("GIFT_UPDATE").size(), 1L, "更新写审计");

            a.throwsType(BizException.class,
                    () -> f.giftAdminService.create("X", -1L, "", OPS), "负价 → 40001");
            a.throwsType(BizException.class,
                    () -> f.giftAdminService.create("  ", 1L, "", OPS), "空名 → 40001");
            a.throwsType(BizException.class,
                    () -> f.giftAdminService.update(2L, null, null, "BAD", OPS), "非法状态 → 40001");
            a.throwsType(BizException.class,
                    () -> f.giftAdminService.update(999L, null, null, null, OPS), "不存在 → 40330");
        });

        // 4. ADM-17/18/19 类目 + 运营位
        registry.add("ADM-17/18/19 · 类目更新 + 运营位新建/更新", a -> {
            AdminFixture f = new AdminFixture();
            f.catalog.seedCategory(new Category(1L, "才艺", false, false, 1, true));

            a.eqLong(f.catalogAdminService.listCategories().size(), 1L, "类目列表");
            Category category = f.catalogAdminService.updateCategory(1L, false, 5, null, OPS);
            a.isFalse(category.enabled(), "启用位更新");
            a.eqLong(category.sort(), 5L, "排序更新");
            a.eq(category.name(), "才艺", "null 字段保持不变");

            Banner created = f.catalogAdminService.createBanner(
                    new Banner(0L, "开屏", "ROOM", "10241", NOW, NOW.plusDays(7), true, 1), OPS);
            a.eqLong(created.id(), 1L, "运营位自增主键");
            a.eq(created.targetType(), "ROOM", "跳转类型");
            a.eqLong(f.auditLogs.list("BANNER_CREATE").size(), 1L, "新建写审计");

            Banner updated = f.catalogAdminService.updateBanner(1L,
                    new Banner(1L, "开屏v2", "URL", "https://cos/x.png", NOW, NOW.plusDays(3), false, 2), OPS);
            a.eq(updated.title(), "开屏v2", "整行覆盖标题");
            a.eq(updated.targetType(), "URL", "整行覆盖跳转类型");

            a.throwsType(BizException.class, () -> f.catalogAdminService.createBanner(
                    new Banner(0L, "x", "BAD", "v", null, null, true, 1), OPS), "非法 target_type → 40001");
            a.throwsType(BizException.class, () -> f.catalogAdminService.createBanner(
                    new Banner(0L, "x", "ROOM", "v", NOW, NOW.minusDays(1), true, 1), OPS),
                    "结束早于开始 → 40001");
            a.throwsType(BizException.class, () -> f.catalogAdminService.createBanner(
                    new Banner(0L, " ", "ROOM", "v", null, null, true, 1), OPS), "空标题 → 40001");
            a.throwsType(BizException.class,
                    () -> f.catalogAdminService.updateCategory(999L, true, null, null, OPS), "类目不存在 → 40330");
        });

        // 5. ADM-21…28 财务：月结幂等 / 对账 / 提现 / 发票
        registry.add("ADM-21…28 · 月结幂等 + 对账闭合 + 提现审核打款 + 发票", a -> {
            AdminFixture f = new AdminFixture();

            // 月结：固定来源一条主播
            CoinPoolState pool = new CoinPoolState();
            pool.credit("WECHAT", 1_000L, 6_000L, 36L, new BigDecimal("0.006"));
            f.settlementSource.seed(2001L, pool, 500L);

            a.throwsType(BizException.class,
                    () -> f.financeAdminService.generate("2026-1", FINANCE), "账期格式 → 40001");

            FinanceAdminService.GenerateResult first = f.financeAdminService.generate("2026-10", FINANCE);
            a.isTrue(first.generated(), "首次出账 generated=true");
            a.eqLong(f.bills.listAll().size(), 1L, "落一条账单");

            FinanceAdminService.GenerateResult again = f.financeAdminService.generate("2026-10", FINANCE);
            a.isFalse(again.generated(), "同期重复出账 → 幂等");
            a.eqLong(f.bills.listAll().size(), 1L, "不重复建账");
            a.eqLong(f.auditLogs.list("SETTLEMENT_GENERATE").size(), 2L, "两次触发各留痕一次");

            long billId = f.bills.listAll().get(0).billId();
            a.eqLong(f.financeAdminService.settlement(billId).billId(), billId, "账单详情");
            a.throwsType(BizException.class,
                    () -> f.financeAdminService.settlement(999L), "账单不存在 → 40330");

            FinanceAdminService.ReconcileResult clean = f.financeAdminService.reconcile("2026-10");
            a.eqLong(clean.diffFen(), 0L, "五条等式闭合 → diff=0");
            a.isFalse(clean.frozen(), "无差异不冻结");

            // 对账差异 → 冻结
            f.bills.seed(new SettlementBillEntity(99L, 2002L, corruptBill()));
            FinanceAdminService.ReconcileResult dirty = f.financeAdminService.reconcile("2026-10");
            a.isTrue(dirty.diffFen() != 0L, "构造脏账单 → diff≠0");
            a.isTrue(dirty.frozen(), "差异≠0 → 冻结出款");

            // 提现：种子可提现余额 + 视图
            f.earnings.seed(new EarningAccount(2001L, 0L, 100_000L, 0L, 0L));
            WithdrawalOrder order = f.withdrawalService.request(new WithdrawalCommand(
                    2001L, AnchorType.PERSONAL, 50_000L, true, TaxMode.SETTLE, 0L));
            f.adminWithdrawals.seed(new AdminWithdrawalView(order.orderNo(), "2026-10", "小闪", "张三",
                    50_000L, 0L, 50_000L, "6222****1234", true, WithdrawalOrder.STATUS_PENDING, null, null));

            a.eqLong(f.financeAdminService.withdrawals("2026-10").size(), 1L, "提现列表按账期筛选");
            a.eqLong(f.financeAdminService.withdrawals(null).size(), 1L, "提现列表全量");

            AdminWithdrawalView paid = f.financeAdminService.review(order.orderNo(), "APPROVE", null, null, FINANCE);
            a.eq(paid.status(), WithdrawalOrder.STATUS_PAID, "通过 → 打款 PAID");
            a.eq(f.financeAdminService.pay(order.orderNo(), FINANCE).status(),
                    WithdrawalOrder.STATUS_PAID, "打款幂等 → 仍 PAID");
            a.eqLong(f.auditLogs.list("WITHDRAWAL_REVIEW").size(), 1L, "审核写审计");
            a.eqLong(f.auditLogs.list("WITHDRAWAL_PAY").size(), 1L, "打款写审计");

            WithdrawalOrder rejectOrder = f.withdrawalService.request(new WithdrawalCommand(
                    2001L, AnchorType.PERSONAL, 20_000L, true, TaxMode.SETTLE, 0L));
            f.adminWithdrawals.seed(new AdminWithdrawalView(rejectOrder.orderNo(), "2026-10", "小闪", "张三",
                    20_000L, 0L, 20_000L, "6222****1234", true, WithdrawalOrder.STATUS_PENDING, null, null));
            AdminWithdrawalView rejected = f.financeAdminService.review(
                    rejectOrder.orderNo(), "REJECT", "BANK_MISMATCH", "姓名不符", FINANCE);
            a.eq(rejected.status(), WithdrawalOrder.STATUS_REJECTED, "驳回 → REJECTED");
            a.notNull(rejected.statusNote(), "驳回原因回填");

            a.throwsType(BizException.class, () -> f.financeAdminService.review(
                    order.orderNo(), "NOPE", null, null, FINANCE), "非法 decision → 40001");

            // 发票
            f.invoices.seed(new Invoice("INV-1", "ORDER-1", "买家", 600L, "WECHAT",
                    Invoice.STATUS_PENDING, null));
            a.eqLong(f.financeAdminService.invoices().size(), 1L, "发票列表");
            Invoice issued = f.financeAdminService.issueInvoice("INV-1", FINANCE);
            a.eq(issued.status(), Invoice.STATUS_ISSUED, "开票 → ISSUED");
            a.eq(issued.invoiceUrl(), "/invoices/INV-1.pdf", "回填发票 URL");
            a.throwsType(BizException.class,
                    () -> f.financeAdminService.issueInvoice("NOPE", FINANCE), "发票不存在 → 40330");
        });

        // 6. AC-1 / AC-6 实名状态机（出生日期推导未成年 + 5 次锁定）
        registry.add("AC-1/AC-6 · 实名提交/状态（is_minor 推导 + 5 次锁定）", a -> {
            AdminFixture f = new AdminFixture();
            LocalDate birth = LocalDate.of(2008, 5, 1);
            LocalDate minorBirth = LocalDate.of(2010, 1, 1);
            LocalDate adultBirth = LocalDate.of(1990, 1, 1);

            a.eq(f.realnameService.status(3001L).verifyStatus(), RealnameRecord.UNVERIFIED, "无记录 → UNVERIFIED");

            RealnameRecord failed = f.realnameService.submit(3001L, "张三", "123", birth);
            a.eq(failed.verifyStatus(), RealnameRecord.FAILED, "证件号过短 → FAILED");
            a.eqLong(failed.attemptCount(), 1L, "失败计数 +1");
            a.eq(failed.failReason(), "ID_MISMATCH", "失败原因 ID_MISMATCH");

            f.realnameService.submit(3001L, "张三", "123", birth);
            f.realnameService.submit(3001L, "张三", "123", birth);
            f.realnameService.submit(3001L, "张三", "123", birth);
            RealnameRecord locked = f.realnameService.submit(3001L, "张三", "123", birth);
            a.eq(locked.verifyStatus(), RealnameRecord.LOCKED, "第 5 次失败 → LOCKED");
            a.throwsType(BizException.class,
                    () -> f.realnameService.submit(3001L, "张三", "123", birth), "锁定后再提交 → 42900");

            RealnameRecord minor = f.realnameService.submit(3002L, "李四", "110101200001011234", minorBirth);
            a.eq(minor.verifyStatus(), RealnameRecord.VERIFIED, "核验通过 → VERIFIED");
            a.isTrue(minor.isMinor(), "2010 出生 → 未成年（AC-6 前置）");
            a.eq(minor.verifyChannel(), "PLACEHOLDER", "核验渠道落库");
            a.eq(minor.realName(), "李四", "实名姓名回填");

            RealnameRecord adult = f.realnameService.submit(3003L, "王五", "110101199001011234", adultBirth);
            a.isFalse(adult.isMinor(), "1990 出生 → 成年");

            a.throwsType(BizException.class,
                    () -> f.realnameService.submit(3004L, "  ", "110101200001011234", birth), "姓名为空 → 40001");
            a.throwsType(BizException.class,
                    () -> f.realnameService.submit(3005L, "赵六", "110101200001011234", null), "缺出生日期 → 40001");
        });

        // 7. 渠道登录 + refresh 轮换 / 登出（DECISION-3/4）
        registry.add("AUTH-03/04/05/06 · 渠道登录 + refresh 轮换 + 登出", a -> {
            AdminFixture f = new AdminFixture();
            ChannelLoginService.RoleResolver viewer = userId -> Set.of("VIEWER");
            RefreshTokenService.RoleResolver refreshRoles = userId -> Set.of("VIEWER");

            ChannelLoginService.LoginResult first = f.channelLoginService.login("WECHAT", "wx-code-1", viewer);
            a.isTrue(first.needBindPhone(), "新用户未绑手机号 → need_bind_phone=true");
            a.notNull(first.accessToken(), "签发 access");
            a.notNull(first.refreshToken(), "签发 refresh");
            a.eqLong(first.expireIn(), 7_200L, "access 有效期 2h");

            ChannelLoginService.LoginResult second = f.channelLoginService.login("WECHAT", "wx-code-1", viewer);
            a.eqLong(second.userId(), first.userId(), "同授权码重复登录命中同账号");

            f.identities.bindPhone(first.userId(), "hash-abc");
            a.isFalse(f.channelLoginService.login("WECHAT", "wx-code-1", viewer).needBindPhone(),
                    "已绑手机号 → need_bind_phone=false");

            ChannelLoginService.LoginResult apple = f.channelLoginService.login("APPLE", "apple-token-1", viewer);
            a.notNull(apple.accessToken(), "Apple 登录同流程");
            a.isTrue(apple.needBindPhone(), "Apple 首次登录同样强制补绑");

            a.throwsType(BizException.class,
                    () -> f.channelLoginService.login("WECHAT", "   ", viewer), "空授权码 → 40001");
            a.throwsType(BizException.class,
                    () -> f.channelLoginService.login("NOPE", "code", viewer), "未知渠道 → 40001");

            RefreshTokenService.TokenPair pair = f.refreshTokenService.issue(3001L, Set.of("VIEWER"));
            a.eqLong(f.refreshTokenService.userIdOf(pair.refreshToken()).orElse(0L), 3001L, "refresh 可解析归属");

            RefreshTokenService.TokenPair rotated = f.refreshTokenService.rotate(pair.refreshToken(), refreshRoles);
            a.isFalse(rotated.refreshToken().equals(pair.refreshToken()), "轮换出新 refresh");
            a.isTrue(f.refreshTokenService.userIdOf(pair.refreshToken()).isEmpty(), "旧 refresh 立即失效");
            a.throwsType(BizException.class,
                    () -> f.refreshTokenService.rotate(pair.refreshToken(), refreshRoles), "旧票复用 → 40100");

            f.refreshTokenService.revoke(rotated.refreshToken());
            f.refreshTokenService.revoke(rotated.refreshToken());
            a.isTrue(f.refreshTokenService.userIdOf(rotated.refreshToken()).isEmpty(), "登出后失效（幂等）");
        });

        // 8. IAP（API-ASSET-07/08 · 幂等加币）
        registry.add("ASSET-07/08 · IAP 商品 + 回执校验 + 幂等加币", a -> {
            AdminFixture f = new AdminFixture();
            f.iapProducts.seed("com.mars.iap.coins.6", 600L, 600L, "¥6", "ON");

            a.eqLong(f.iapProducts.all().size(), 1L, "商品列表");
            a.eq(f.iapVerifier.verify("com.mars.iap.coins.6", "sandbox-receipt-1"), "SANDBOX", "沙盒环境识别");
            a.eq(f.iapVerifier.verify("com.mars.iap.coins.6", "prod-receipt-1"), "PROD", "生产环境识别");
            a.throwsType(BizException.class,
                    () -> f.iapVerifier.verify("com.mars.iap.coins.6", ""), "空回执 → 40345");
            a.throwsType(BizException.class,
                    () -> f.iapVerifier.verify("nope", "r"), "商品不存在 → 40345");

            IapVerifyCommand command = new IapVerifyCommand("TX-1", 4001L, "com.mars.iap.coins.6",
                    600L, 600L, new BigDecimal("0.30"), "SANDBOX");
            IapTransaction tx = f.iapVerifyService.verify(command);
            a.eq(tx.status(), IapTransaction.STATUS_VERIFIED, "交易置 VERIFIED");
            a.eqLong(f.wallets.find(4001L).orElseThrow().balance(), 600L, "钱包加币一次");
            a.eqLong(f.pools.load().coinPoolTotal(), 600L, "币池同事务累加");

            IapTransaction replay = f.iapVerifyService.verify(command);
            a.eqLong(f.wallets.find(4001L).orElseThrow().balance(), 600L, "同 transaction_id 不重复加币");
            a.eq(replay.transactionId(), tx.transactionId(), "幂等返回原交易");

            IapTransaction refunded = f.iapVerifyService.reverse("TX-1", false);
            a.eq(refunded.status(), IapTransaction.STATUS_REFUNDED, "退款逆向冲减");
            a.eqLong(f.wallets.find(4001L).orElseThrow().balance(), 0L, "退款扣回币");
        });
    }

    private static ModerationTask task(String taskId, long roomId, int confidence, TaskStatus status) {
        return new ModerationTask(taskId, "evt-" + taskId, "CSS", roomId, roomId, 2001L, "小闪", 1L,
                RiskLevel.fromConfidence(confidence), AlertType.IMAGE, "疑似违规", "https://cos/snap.jpg",
                "R-1001", confidence, status, null, null, NOW);
    }

    /** 构造一条「五条等式不闭合」的账单，用于对账冻结断言。 */
    private static SettlementBill corruptBill() {
        return new SettlementBill("2026-10", new BigDecimal("0.50"),
                100L, 0L, 0L, 100L, 50L, 50L, 0L, 50L, 0L, 0L,
                SettlementBill.STATUS_GENERATED, List.of());
    }
}
