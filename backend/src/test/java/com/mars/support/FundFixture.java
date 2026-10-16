package com.mars.support;

import com.mars.account.MinorLimitConfig;
import com.mars.account.MinorLimitPolicy;
import com.mars.asset.gift.GiftItem;
import com.mars.asset.gift.GiftResultCache;
import com.mars.asset.gift.GiftSendService;
import com.mars.asset.iap.IapVerifyService;
import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.recharge.RechargeService;
import com.mars.common.tx.InMemoryUnitOfWork;
import com.mars.common.tx.UnitOfWork;
import com.mars.earning.WithdrawalPolicy;
import com.mars.earning.WithdrawalService;

/**
 * 资金链路测试夹具：把领域服务接到内存仓储 + 内存事务管理器上。
 *
 * <p>事务参与者覆盖<b>全部</b>存储，因此任何一步抛异常都会整体回到进入事务前的状态
 * —— 这正是 AC-5「异常自动回滚、不留部分写入」的验证基础。
 */
public final class FundFixture {

    /** 未成年限额档（OP-6 / OP-21 开关默认开）。 */
    public static final MinorLimitConfig MINOR_LIMIT = new MinorLimitConfig(1_000L, 5_000L, true);

    public final InMemoryStores.Wallets wallets = new InMemoryStores.Wallets();
    public final InMemoryStores.WalletLedgers walletLedgers = new InMemoryStores.WalletLedgers();
    public final InMemoryStores.Earnings earnings = new InMemoryStores.Earnings();
    public final InMemoryStores.EarningLedgers earningLedgers = new InMemoryStores.EarningLedgers();
    public final InMemoryStores.GiftOrders giftOrders = new InMemoryStores.GiftOrders();
    public final InMemoryStores.Recharges recharges = new InMemoryStores.Recharges();
    public final InMemoryStores.IapTransactions iapTransactions = new InMemoryStores.IapTransactions();
    public final InMemoryStores.WithdrawalOrders withdrawalOrders = new InMemoryStores.WithdrawalOrders();
    public final InMemoryStores.Pools pools = new InMemoryStores.Pools();
    public final InMemoryStores.GiftCatalogImpl catalog = new InMemoryStores.GiftCatalogImpl();
    public final InMemoryStores.SeqOrderNoGenerator orderNos = new InMemoryStores.SeqOrderNoGenerator();
    public final GiftResultCache giftResults = new GiftResultCache();

    public final UnitOfWork unitOfWork = new InMemoryUnitOfWork(
            wallets, walletLedgers, earnings, earningLedgers,
            giftOrders, recharges, iapTransactions, withdrawalOrders, pools);

    public final GiftSendService giftSend;
    public final RechargeService recharge;
    public final IapVerifyService iap;
    public final WithdrawalService withdrawal;

    public FundFixture() {
        MinorLimitPolicy minorLimitPolicy = new MinorLimitPolicy();
        this.giftSend = new GiftSendService(catalog, minorLimitPolicy, MINOR_LIMIT,
                wallets, walletLedgers, earnings, earningLedgers, giftOrders,
                unitOfWork, orderNos, giftResults);
        this.recharge = new RechargeService(recharges, wallets, walletLedgers, pools,
                unitOfWork, orderNos);
        this.iap = new IapVerifyService(iapTransactions, wallets, walletLedgers, pools, unitOfWork);
        this.withdrawal = new WithdrawalService(new WithdrawalPolicy(), earnings, earningLedgers,
                withdrawalOrders, unitOfWork, orderNos);
    }

    public void seedWallet(long userId, long balance) {
        wallets.seed(new WalletAccount(userId, balance, 1L, false));
    }

    public void seedGift(long giftId, long priceCoin, boolean onSale) {
        catalog.put(new GiftItem(giftId, "gift-" + giftId, priceCoin, onSale));
    }

    public long balanceOf(long userId) {
        return wallets.find(userId).map(WalletAccount::balance).orElse(0L);
    }

    public boolean frozenOf(long userId) {
        return wallets.find(userId).map(WalletAccount::frozen).orElse(false);
    }

    public long totalEarnedOf(long anchorId) {
        return earnings.find(anchorId).map(EarningAccount::totalEarned).orElse(0L);
    }

    public long availableOf(long anchorId) {
        return earnings.find(anchorId).map(EarningAccount::available).orElse(0L);
    }
}
