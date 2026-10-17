package com.mars.app.config;

import com.mars.account.MinorLimitConfig;
import com.mars.account.MinorLimitPolicy;
import com.mars.account.service.ChannelIdentityProvider;
import com.mars.account.service.PlaceholderChannelIdentityProvider;
import com.mars.account.service.PlaceholderRealnameVerifier;
import com.mars.account.service.RealnameVerifier;
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
import com.mars.admin.service.AdminRoomService;
import com.mars.admin.service.AdminUserService;
import com.mars.admin.service.CatalogAdminService;
import com.mars.admin.service.ChannelLoginService;
import com.mars.admin.service.EmptySettlementSource;
import com.mars.admin.service.FinanceAdminService;
import com.mars.admin.service.GiftAdminService;
import com.mars.admin.service.RealnameService;
import com.mars.admin.service.RefreshTokenService;
import com.mars.admin.service.SettlementReconciler;
import com.mars.asset.gift.GiftCatalog;
import com.mars.asset.gift.GiftResultCache;
import com.mars.asset.gift.GiftSendService;
import com.mars.asset.iap.IapProductCatalog;
import com.mars.asset.iap.IapReceiptVerifier;
import com.mars.asset.iap.IapVerifyService;
import com.mars.asset.iap.PlaceholderIapReceiptVerifier;
import com.mars.asset.recharge.RechargeService;
import com.mars.asset.repo.CoinPoolRepository;
import com.mars.asset.repo.EarningAccountRepository;
import com.mars.asset.repo.EarningLedgerRepository;
import com.mars.asset.repo.GiftOrderRepository;
import com.mars.asset.repo.IapTransactionRepository;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.asset.repo.WithdrawalOrderRepository;
import com.mars.common.security.JwtTokenService;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.SpringUnitOfWork;
import com.mars.common.tx.UnitOfWork;
import com.mars.earning.WithdrawalPolicy;
import com.mars.earning.WithdrawalService;
import com.mars.earning.settlement.SettlementCalculator;
import com.mars.earning.settlement.TaxCalculator;
import com.mars.earning.tax.FlatTaxCalculator;
import com.mars.interaction.mapper.SensitiveWordMapper;
import com.mars.interaction.service.CompositeSensitiveWordSource;
import com.mars.interaction.service.ResourceSensitiveWordSource;
import com.mars.interaction.service.SensitiveWordSource;
import com.mars.moderation.AppealService;
import com.mars.moderation.AuditRecorder;
import com.mars.moderation.ModerationActionService;
import com.mars.moderation.ModerationQueueService;
import com.mars.moderation.PlatformConfig;
import com.mars.moderation.ReplayService;
import com.mars.moderation.ReportService;
import com.mars.moderation.SysConfigService;
import com.mars.moderation.repo.AdminOperationLogRepository;
import com.mars.moderation.repo.AppealRepository;
import com.mars.moderation.repo.ContentReportRepository;
import com.mars.moderation.repo.ModerationActionRepository;
import com.mars.moderation.repo.ModerationEvidenceRepository;
import com.mars.moderation.repo.ModerationTaskRepository;
import com.mars.moderation.repo.OperatorLookup;
import com.mars.moderation.repo.ReplayRecordRepository;
import com.mars.moderation.repo.RoomLookup;
import com.mars.moderation.repo.SysConfigRepository;
import com.mars.moderation.repo.WebhookEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 领域层装配：把零依赖的领域服务接到 Spring 事务与 MyBatis 仓储上。
 *
 * <p>领域服务本身不认识 Spring —— 本类只做「依赖注入 + 事务管理器」的绑定，
 * 因此同一套逻辑既能在 {@code specCheck}（内存替身）里跑，也能在运行态（MySQL）里跑。
 */
@Configuration
public class DomainConfig {

    @Bean
    public UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new SpringUnitOfWork(transactionManager);
    }

    /** 业务单号：{@code <BIZ>-<32 位随机>}（生产可替换为号段/雪花）。 */
    @Bean
    public OrderNoGenerator orderNoGenerator() {
        return bizType -> bizType + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Bean
    public MinorLimitPolicy minorLimitPolicy() {
        return new MinorLimitPolicy();
    }

    @Bean
    public MinorLimitConfig minorLimitConfig(
            @Value("${mars.minor-limit.single-limit-coin:1000}") long singleLimitCoin,
            @Value("${mars.minor-limit.daily-limit-coin:5000}") long dailyLimitCoin,
            @Value("${mars.minor-limit.requires-realname:true}") boolean requiresRealname) {
        return new MinorLimitConfig(singleLimitCoin, dailyLimitCoin, requiresRealname);
    }

    @Bean
    public GiftResultCache giftResultCache() {
        return new GiftResultCache();
    }

    @Bean
    public SettlementCalculator settlementCalculator() {
        return new SettlementCalculator();
    }

    /**
     * 敏感词来源：内置资源词表 + 运营可配置词表（{@code sensitive_word}）合并。
     *
     * <p>之所以在这里显式组装而不是让 {@code SensitiveWordMapper} 直接实现 {@code SensitiveWordSource}：
     * 后者会让容器里出现两个同类型 bean，注入单值时产生歧义。
     */
    @Bean
    public SensitiveWordSource sensitiveWordSource(SensitiveWordMapper sensitiveWordMapper) {
        return new CompositeSensitiveWordSource(List.of(
                new ResourceSensitiveWordSource(),
                () -> sensitiveWordMapper.words()));
    }

    @Bean
    public GiftSendService giftSendService(GiftCatalog giftCatalog,
                                           MinorLimitPolicy minorLimitPolicy,
                                           MinorLimitConfig minorLimitConfig,
                                           WalletAccountRepository walletAccountRepository,
                                           WalletLedgerRepository walletLedgerRepository,
                                           EarningAccountRepository earningAccountRepository,
                                           EarningLedgerRepository earningLedgerRepository,
                                           GiftOrderRepository giftOrderRepository,
                                           UnitOfWork unitOfWork,
                                           OrderNoGenerator orderNoGenerator,
                                           GiftResultCache giftResultCache) {
        return new GiftSendService(giftCatalog, minorLimitPolicy, minorLimitConfig,
                walletAccountRepository, walletLedgerRepository, earningAccountRepository,
                earningLedgerRepository, giftOrderRepository, unitOfWork, orderNoGenerator, giftResultCache);
    }

    @Bean
    public RechargeService rechargeService(RechargeOrderRepository rechargeOrderRepository,
                                           WalletAccountRepository walletAccountRepository,
                                           WalletLedgerRepository walletLedgerRepository,
                                           CoinPoolRepository coinPoolRepository,
                                           UnitOfWork unitOfWork,
                                           OrderNoGenerator orderNoGenerator) {
        return new RechargeService(rechargeOrderRepository, walletAccountRepository,
                walletLedgerRepository, coinPoolRepository, unitOfWork, orderNoGenerator);
    }

    // ------------------------------------------------------------------
    // 审核域 + 平台口径 + 回放 + 审计（AC-7 / AC-9 · 契约 §4.8 / §4.9）
    // ------------------------------------------------------------------

    /**
     * 平台口径读取：{@code appeal_days} / {@code replay_retention_days} 存 {@code sys_config}，
     * 运营可改、<b>不硬编码</b>；{@code fallback} 仅用于 DB 未覆盖时兜底（默认 7 / 30）。
     */
    @Bean
    public PlatformConfig platformConfig(SysConfigRepository sysConfigRepository,
                                         @Value("${mars.moderation.appeal-days:7}") int appealDaysFallback,
                                         @Value("${mars.moderation.replay-retention-days:30}") int replayRetentionDaysFallback) {
        return new PlatformConfig(sysConfigRepository, appealDaysFallback, replayRetentionDaysFallback);
    }

    /**
     * 审计记录器：{@code OperatorLookup} 由 {@link UserAccountRepository} 提供（{@code user.nickname}），
     * 使审计日志的操作者展示名由用户 ID 解析为真实昵称（闭合「已知限制 9」）。
     */
    @Bean
    public AuditRecorder auditRecorder(AdminOperationLogRepository adminOperationLogRepository,
                                       OrderNoGenerator orderNoGenerator,
                                       Clock clock,
                                       OperatorLookup operatorLookup) {
        return new AuditRecorder(adminOperationLogRepository, orderNoGenerator, clock, operatorLookup);
    }

    @Bean
    public ModerationQueueService moderationQueueService(ModerationTaskRepository moderationTaskRepository,
                                                         WebhookEventRepository webhookEventRepository,
                                                         RoomLookup roomLookup,
                                                         UnitOfWork unitOfWork,
                                                         OrderNoGenerator orderNoGenerator,
                                                         Clock clock) {
        return new ModerationQueueService(moderationTaskRepository, webhookEventRepository, roomLookup,
                unitOfWork, orderNoGenerator, clock);
    }

    @Bean
    public ModerationActionService moderationActionService(ModerationTaskRepository moderationTaskRepository,
                                                           ModerationActionRepository moderationActionRepository,
                                                           ModerationEvidenceRepository moderationEvidenceRepository,
                                                           RoomLookup roomLookup,
                                                           PlatformConfig platformConfig,
                                                           UnitOfWork unitOfWork,
                                                           OrderNoGenerator orderNoGenerator,
                                                           AuditRecorder auditRecorder,
                                                           Clock clock) {
        return new ModerationActionService(moderationTaskRepository, moderationActionRepository,
                moderationEvidenceRepository, roomLookup, platformConfig, unitOfWork, orderNoGenerator,
                auditRecorder, clock);
    }

    @Bean
    public ReportService reportService(ContentReportRepository contentReportRepository,
                                       UnitOfWork unitOfWork,
                                       OrderNoGenerator orderNoGenerator,
                                       AuditRecorder auditRecorder,
                                       Clock clock) {
        return new ReportService(contentReportRepository, unitOfWork, orderNoGenerator, auditRecorder, clock);
    }

    @Bean
    public AppealService appealService(AppealRepository appealRepository,
                                       ModerationActionRepository moderationActionRepository,
                                       PlatformConfig platformConfig,
                                       UnitOfWork unitOfWork,
                                       OrderNoGenerator orderNoGenerator,
                                       AuditRecorder auditRecorder,
                                       Clock clock) {
        return new AppealService(appealRepository, moderationActionRepository, platformConfig,
                unitOfWork, orderNoGenerator, auditRecorder, clock);
    }

    @Bean
    public ReplayService replayService(ReplayRecordRepository replayRecordRepository,
                                       PlatformConfig platformConfig,
                                       UnitOfWork unitOfWork,
                                       OrderNoGenerator orderNoGenerator,
                                       Clock clock) {
        return new ReplayService(replayRecordRepository, platformConfig, unitOfWork, orderNoGenerator, clock);
    }

    @Bean
    public SysConfigService sysConfigService(SysConfigRepository sysConfigRepository,
                                             UnitOfWork unitOfWork,
                                             AuditRecorder auditRecorder) {
        return new SysConfigService(sysConfigRepository, unitOfWork, auditRecorder);
    }

    // ------------------------------------------------------------------
    // 账号 / 实名 / 渠道登录（契约 §4.1 · 增量 5）
    // ------------------------------------------------------------------

    /**
     * 实名核验适配（占位）：真实实现须对接第三方云实名（OP-5）。
     * 占位口径：姓名非空且证件号长度 ≥ 15 视为通过，否则 {@code ID_MISMATCH}。
     */
    @Bean
    public RealnameVerifier realnameVerifier() {
        return new PlaceholderRealnameVerifier();
    }

    @Bean
    public RealnameService realnameService(RealnameRepository realnameRepository,
                                           RealnameVerifier realnameVerifier,
                                           Clock clock) {
        return new RealnameService(realnameRepository, realnameVerifier, clock);
    }

    @Bean
    public RefreshTokenService refreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                                   JwtTokenService jwtTokenService,
                                                   Clock clock) {
        return new RefreshTokenService(refreshTokenRepository, jwtTokenService, clock);
    }

    /** 渠道换票适配（占位）：真实实现须对接微信 {@code code2session} / Apple 验签。 */
    @Bean
    public ChannelIdentityProvider channelIdentityProvider() {
        return new PlaceholderChannelIdentityProvider();
    }

    @Bean
    public ChannelLoginService channelLoginService(ChannelIdentityProvider channelIdentityProvider,
                                                   IdentityRepository identityRepository,
                                                   UserAccountRepository userAccountRepository,
                                                   RefreshTokenService refreshTokenService,
                                                   UnitOfWork unitOfWork) {
        return new ChannelLoginService(channelIdentityProvider, identityRepository,
                userAccountRepository, refreshTokenService, unitOfWork);
    }

    // ------------------------------------------------------------------
    // 提现 / 结算 / 税务（契约 §4.7 · §4.9 API-ADM-21…28 · AC-8）
    // ------------------------------------------------------------------

    @Bean
    public WithdrawalPolicy withdrawalPolicy() {
        return new WithdrawalPolicy();
    }

    @Bean
    public WithdrawalService withdrawalService(WithdrawalPolicy withdrawalPolicy,
                                               EarningAccountRepository earningAccountRepository,
                                               EarningLedgerRepository earningLedgerRepository,
                                               WithdrawalOrderRepository withdrawalOrderRepository,
                                               UnitOfWork unitOfWork,
                                               OrderNoGenerator orderNoGenerator) {
        return new WithdrawalService(withdrawalPolicy, earningAccountRepository, earningLedgerRepository,
                withdrawalOrderRepository, unitOfWork, orderNoGenerator);
    }

    /** 个税计算占位（OP-23 待人类定值）：统一 6% 扁率，替换点见 {@link FlatTaxCalculator}。 */
    @Bean
    public TaxCalculator taxCalculator() {
        return FlatTaxCalculator.placeholder();
    }

    @Bean
    public SettlementReconciler settlementReconciler() {
        return new SettlementReconciler();
    }

    /** 月结取数空来源（占位）：无运行时来源 → {@code generate} 返回 {@code generated=false}。 */
    @Bean
    public SettlementSource settlementSource() {
        return new EmptySettlementSource();
    }

    // ------------------------------------------------------------------
    // IAP（契约 §4.6 API-ASSET-07/08 · OP-11）
    // ------------------------------------------------------------------

    @Bean
    public IapReceiptVerifier iapReceiptVerifier(IapProductCatalog iapProductCatalog) {
        return new PlaceholderIapReceiptVerifier(iapProductCatalog);
    }

    @Bean
    public IapVerifyService iapVerifyService(IapTransactionRepository iapTransactionRepository,
                                             WalletAccountRepository walletAccountRepository,
                                             WalletLedgerRepository walletLedgerRepository,
                                             CoinPoolRepository coinPoolRepository,
                                             UnitOfWork unitOfWork) {
        return new IapVerifyService(iapTransactionRepository, walletAccountRepository,
                walletLedgerRepository, coinPoolRepository, unitOfWork);
    }

    // ------------------------------------------------------------------
    // 后台管理服务（契约 §4.9 · API-ADM-01…28）
    // ------------------------------------------------------------------

    @Bean
    public AdminUserService adminUserService(UserAccountRepository userAccountRepository,
                                             AuditRecorder auditRecorder,
                                             Clock clock) {
        return new AdminUserService(userAccountRepository, auditRecorder, clock);
    }

    @Bean
    public AdminRoomService adminRoomService(AdminRoomRepository adminRoomRepository,
                                             ModerationTaskRepository moderationTaskRepository) {
        return new AdminRoomService(adminRoomRepository, moderationTaskRepository);
    }

    @Bean
    public GiftAdminService giftAdminService(GiftAdminRepository giftAdminRepository,
                                             AuditRecorder auditRecorder) {
        return new GiftAdminService(giftAdminRepository, auditRecorder);
    }

    @Bean
    public CatalogAdminService catalogAdminService(CatalogRepository catalogRepository,
                                                   AuditRecorder auditRecorder) {
        return new CatalogAdminService(catalogRepository, auditRecorder);
    }

    @Bean
    public FinanceAdminService financeAdminService(SettlementBillRepository settlementBillRepository,
                                                   InvoiceRepository invoiceRepository,
                                                   AdminWithdrawalQuery adminWithdrawalQuery,
                                                   WithdrawalService withdrawalService,
                                                   SettlementSource settlementSource,
                                                   PlatformConfig platformConfig,
                                                   TaxCalculator taxCalculator,
                                                   SettlementCalculator settlementCalculator,
                                                   SettlementReconciler settlementReconciler,
                                                   AuditRecorder auditRecorder) {
        return new FinanceAdminService(settlementBillRepository, invoiceRepository, adminWithdrawalQuery,
                withdrawalService, settlementSource, platformConfig, taxCalculator,
                settlementCalculator, settlementReconciler, auditRecorder);
    }
}
