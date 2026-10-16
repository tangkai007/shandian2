package com.mars.asset.iap;

import com.mars.asset.ledger.EarningLedgerEntry;
import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.pool.CoinPoolState;
import com.mars.asset.repo.CoinPoolRepository;
import com.mars.asset.repo.IapTransactionRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.money.MoneyMath;
import com.mars.common.tx.UnitOfWork;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * IAP 交易校验与退款逆向（契约 §4.6 API-ASSET-08 / §4.10 API-CB-07 · S1a §3.3）。
 *
 * <p>本轮实现<b>服务端业务侧</b>：{@code transaction_id} 唯一 → 同一交易只加币一次；
 * 退款/撤销（{@code REFUND} / {@code REVOKE}）<b>逆向冲减</b>钱包与币池，余额不足则记负向并冻结打赏
 * （不静默吞掉）。
 *
 * <p>JWS 验签与调 App Store Server API 查状态属适配层（需真实凭据），见
 * {@code com.mars.asset.iap.IapReceiptVerifier} 的接入位；本类只接收「已验证通过」的交易事实。
 */
public final class IapVerifyService {

    public static final String BIZ_TYPE = "IAP";
    public static final String BIZ_REFUND = "IAP_REFUND";

    private final IapTransactionRepository transactions;
    private final WalletAccountRepository wallets;
    private final WalletLedgerRepository walletLedger;
    private final CoinPoolRepository poolRepository;
    private final UnitOfWork unitOfWork;

    public IapVerifyService(IapTransactionRepository transactions,
                            WalletAccountRepository wallets,
                            WalletLedgerRepository walletLedger,
                            CoinPoolRepository poolRepository,
                            UnitOfWork unitOfWork) {
        this.transactions = transactions;
        this.wallets = wallets;
        this.walletLedger = walletLedger;
        this.poolRepository = poolRepository;
        this.unitOfWork = unitOfWork;
    }

    /**
     * 校验通过后加币（幂等）。
     *
     * @param command 已经过 JWS 验签 + 商品/环境校验 + App Store Server API 状态查询的交易事实
     */
    public IapTransaction verify(IapVerifyCommand command) {
        Objects.requireNonNull(command, "command");
        return unitOfWork.execute(() -> {
            var existing = transactions.findByTransactionId(command.transactionId());
            if (existing.isPresent()) {
                return existing.get(); // 幂等：同一交易不重复加币
            }

            WalletAccount wallet = wallets.find(command.userId())
                    .orElseGet(() -> WalletAccount.empty(command.userId()));
            WalletAccount after = wallet.credit(command.coinAmount());
            wallets.save(after, wallet.version());
            walletLedger.append(new WalletLedgerEntry(command.userId(), BIZ_TYPE, command.transactionId(),
                    LedgerDirection.IN, command.coinAmount(), after.balance(), BIZ_TYPE));

            CoinPoolState pool = poolRepository.load();
            long feeFen = MoneyMath.halfUp(
                    BigDecimal.valueOf(command.amountFen()).multiply(command.channelFeeRate()));
            pool.credit(BIZ_TYPE, command.coinAmount(), command.amountFen(), feeFen, command.channelFeeRate());
            poolRepository.save(pool);

            IapTransaction transaction = new IapTransaction(command.transactionId(), command.userId(),
                    command.productId(), command.amountFen(), command.coinAmount(),
                    command.channelFeeRate(), command.env(), IapTransaction.STATUS_VERIFIED);
            transactions.save(transaction);
            return transaction;
        });
    }

    /**
     * 退款/撤销逆向（App Store Server Notifications V2）。
     *
     * @param revoke {@code true} = 撤销（家庭共享/撤销购买）；{@code false} = 退款
     */
    public IapTransaction reverse(String transactionId, boolean revoke) {
        return unitOfWork.execute(() -> {
            IapTransaction transaction = transactions.findByTransactionId(transactionId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.PARAM_INVALID, "IAP 交易不存在", "transaction_id", "not_found"));
            if (IapTransaction.STATUS_REFUNDED.equals(transaction.status())
                    || IapTransaction.STATUS_REVOKED.equals(transaction.status())) {
                return transaction; // 幂等：重复通知不重复冲减
            }

            WalletAccount wallet = wallets.find(transaction.userId())
                    .orElseGet(() -> WalletAccount.empty(transaction.userId()));
            WalletAccount after = wallet.reverse(transaction.coinAmount());
            wallets.save(after, wallet.version());
            // 冲正流水使用独立 biz_no，避免与首次加币流水（biz_no = transaction_id）唯一键冲突
            walletLedger.append(new WalletLedgerEntry(transaction.userId(), BIZ_REFUND,
                    transactionId + "#REFUND", LedgerDirection.OUT, transaction.coinAmount(),
                    after.balance(), BIZ_TYPE));

            // 币池同步冲减（§3.4.7①）：池为全局累计，不允许为负
            CoinPoolState pool = poolRepository.load();
            pool.reverse(BIZ_TYPE, transaction.coinAmount(), transaction.amountFen(),
                    MoneyMath.halfUp(BigDecimal.valueOf(transaction.amountFen())
                            .multiply(transaction.channelFeeRate())), transaction.channelFeeRate());
            poolRepository.save(pool);

            IapTransaction updated = transaction.withStatus(
                    revoke ? IapTransaction.STATUS_REVOKED : IapTransaction.STATUS_REFUNDED);
            transactions.save(updated);
            return updated;
        });
    }
}
