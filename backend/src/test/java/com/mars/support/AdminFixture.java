package com.mars.support;

import com.mars.account.service.ChannelIdentityProvider;
import com.mars.account.service.PlaceholderChannelIdentityProvider;
import com.mars.account.service.PlaceholderRealnameVerifier;
import com.mars.account.service.RealnameVerifier;
import com.mars.admin.service.AdminRoomService;
import com.mars.admin.service.AdminUserService;
import com.mars.admin.service.CatalogAdminService;
import com.mars.admin.service.ChannelLoginService;
import com.mars.admin.service.FinanceAdminService;
import com.mars.admin.service.GiftAdminService;
import com.mars.admin.service.RealnameService;
import com.mars.admin.service.RefreshTokenService;
import com.mars.admin.service.SettlementReconciler;
import com.mars.asset.iap.IapReceiptVerifier;
import com.mars.asset.iap.IapVerifyService;
import com.mars.asset.iap.PlaceholderIapReceiptVerifier;
import com.mars.common.security.JwtTokenService;
import com.mars.common.tx.InMemoryUnitOfWork;
import com.mars.common.tx.UnitOfWork;
import com.mars.earning.WithdrawalPolicy;
import com.mars.earning.WithdrawalService;
import com.mars.earning.settlement.SettlementCalculator;
import com.mars.earning.settlement.TaxCalculator;
import com.mars.earning.tax.FlatTaxCalculator;
import com.mars.moderation.AuditRecorder;
import com.mars.moderation.PlatformConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 后台 / 账号 / 财务 / IAP 的测试夹具（无 MySQL）。
 *
 * <p>资金存储复用 {@link InMemoryStores}，后台仓储用 {@link AdminStores}；
 * 全部参与者进 {@link InMemoryUnitOfWork}，于是「异常即整体回滚」可被真实验证。
 * 时钟固定 {@code 2026-11-20T10:00:00Z}，使 {@code deadline / expire_at / verified_at} 可精确断言。
 */
public final class AdminFixture {

    /** 固定时间基准（UTC）。 */
    public static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    // 资金 / 币池 / IAP（复用既有内存替身）
    public final InMemoryStores.Wallets wallets = new InMemoryStores.Wallets();
    public final InMemoryStores.WalletLedgers walletLedgers = new InMemoryStores.WalletLedgers();
    public final InMemoryStores.Earnings earnings = new InMemoryStores.Earnings();
    public final InMemoryStores.EarningLedgers earningLedgers = new InMemoryStores.EarningLedgers();
    public final InMemoryStores.Pools pools = new InMemoryStores.Pools();
    public final InMemoryStores.IapTransactions iapTransactions = new InMemoryStores.IapTransactions();
    public final InMemoryStores.WithdrawalOrders withdrawalOrders = new InMemoryStores.WithdrawalOrders();

    // 审核 / 平台口径 / 审计（复用既有内存替身）
    public final InMemoryStores.ModerationTasks tasks = new InMemoryStores.ModerationTasks();
    public final InMemoryStores.AdminOperationLogs auditLogs = new InMemoryStores.AdminOperationLogs();
    public final InMemoryStores.SysConfigs sysConfigs = new InMemoryStores.SysConfigs();
    public final InMemoryStores.Rooms rooms = new InMemoryStores.Rooms();
    public final InMemoryStores.SeqOrderNoGenerator orderNos = new InMemoryStores.SeqOrderNoGenerator();

    // 后台域（本轮新增内存替身）
    public final AdminStores.UserAccounts users = new AdminStores.UserAccounts();
    public final AdminStores.Identities identities = new AdminStores.Identities();
    public final AdminStores.AdminRooms adminRooms = new AdminStores.AdminRooms();
    public final AdminStores.GiftAdmins gifts = new AdminStores.GiftAdmins();
    public final AdminStores.Catalog catalog = new AdminStores.Catalog();
    public final AdminStores.SettlementBills bills = new AdminStores.SettlementBills();
    public final AdminStores.Invoices invoices = new AdminStores.Invoices();
    public final AdminStores.Realnames realnames = new AdminStores.Realnames();
    public final AdminStores.RefreshTokens refreshTokens = new AdminStores.RefreshTokens();
    public final AdminStores.AdminWithdrawals adminWithdrawals =
            new AdminStores.AdminWithdrawals(withdrawalOrders);
    public final AdminStores.FixedSettlementSource settlementSource = new AdminStores.FixedSettlementSource();
    public final AdminStores.IapProducts iapProducts = new AdminStores.IapProducts();

    public final UnitOfWork unitOfWork = new InMemoryUnitOfWork(
            wallets, walletLedgers, earnings, earningLedgers, pools, iapTransactions, withdrawalOrders,
            tasks, auditLogs, sysConfigs, rooms,
            users, identities, gifts, catalog, bills, invoices, realnames, refreshTokens);

    public final PlatformConfig platformConfig = new PlatformConfig(sysConfigs, 7, 30);

    /** 审计记录器（4 参）：昵称由 {@code user.nickname} 解析，闭合「已知限制 9」。 */
    public final AuditRecorder audit = new AuditRecorder(auditLogs, orderNos, clock, users);

    public final JwtTokenService jwt = new JwtTokenService("mars-test-secret-0123456789");

    public final RealnameVerifier realnameVerifier = new PlaceholderRealnameVerifier();
    public final ChannelIdentityProvider channelIdentities = new PlaceholderChannelIdentityProvider();
    public final IapReceiptVerifier iapVerifier = new PlaceholderIapReceiptVerifier(iapProducts);

    public final RealnameService realnameService = new RealnameService(realnames, realnameVerifier, clock);
    public final RefreshTokenService refreshTokenService =
            new RefreshTokenService(refreshTokens, jwt, clock);
    public final ChannelLoginService channelLoginService = new ChannelLoginService(
            channelIdentities, identities, users, refreshTokenService, unitOfWork);

    public final WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy();
    public final WithdrawalService withdrawalService = new WithdrawalService(withdrawalPolicy, earnings,
            earningLedgers, withdrawalOrders, unitOfWork, orderNos);
    public final TaxCalculator taxCalculator = FlatTaxCalculator.placeholder();
    public final SettlementCalculator settlementCalculator = new SettlementCalculator();
    public final SettlementReconciler settlementReconciler = new SettlementReconciler();

    public final AdminUserService adminUserService = new AdminUserService(users, audit, clock);
    public final AdminRoomService adminRoomService = new AdminRoomService(adminRooms, tasks);
    public final GiftAdminService giftAdminService = new GiftAdminService(gifts, audit);
    public final CatalogAdminService catalogAdminService = new CatalogAdminService(catalog, audit);
    public final FinanceAdminService financeAdminService = new FinanceAdminService(bills, invoices,
            adminWithdrawals, withdrawalService, settlementSource, platformConfig, taxCalculator,
            settlementCalculator, settlementReconciler, audit);
    public final IapVerifyService iapVerifyService = new IapVerifyService(iapTransactions, wallets,
            walletLedgers, pools, unitOfWork);

    public AdminFixture() {
    }
}
