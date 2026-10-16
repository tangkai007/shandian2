package com.mars.app.config;

import com.mars.account.MinorLimitConfig;
import com.mars.account.MinorLimitPolicy;
import com.mars.asset.gift.GiftCatalog;
import com.mars.asset.gift.GiftResultCache;
import com.mars.asset.gift.GiftSendService;
import com.mars.asset.recharge.RechargeService;
import com.mars.asset.repo.CoinPoolRepository;
import com.mars.asset.repo.EarningAccountRepository;
import com.mars.asset.repo.EarningLedgerRepository;
import com.mars.asset.repo.GiftOrderRepository;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.SpringUnitOfWork;
import com.mars.common.tx.UnitOfWork;
import com.mars.earning.settlement.SettlementCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

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
}
