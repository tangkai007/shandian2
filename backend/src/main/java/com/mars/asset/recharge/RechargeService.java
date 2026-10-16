package com.mars.asset.recharge;

import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.pool.CoinPoolState;
import com.mars.asset.repo.CoinPoolRepository;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.money.MoneyMath;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;

import java.math.BigDecimal;

/**
 * 充值与支付回调（契约 §4.6 API-ASSET-05 / §4.10 API-CB-05、06 · S1a §3.4.2）。
 *
 * <p>关键点：
 * <ul>
 *   <li>下单时<b>快照渠道费率</b>，历史账单不受后续配置变更影响；</li>
 *   <li>支付成功回调在<b>同一事务</b>内：置单为 PAID → 钱包加币 → 写钱包流水 → <b>币池增量</b>
 *       （{@code coin_pool_state} 与 {@code coin_pool_channel}）；</li>
 *   <li>回调<b>幂等</b>：已 PAID 的订单重复回调不重复加币（三方会重投）。</li>
 * </ul>
 */
public final class RechargeService {

    public static final String BIZ_TYPE = "RECHARGE";

    private final RechargeOrderRepository orders;
    private final WalletAccountRepository wallets;
    private final WalletLedgerRepository walletLedger;
    private final CoinPoolRepository poolRepository;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator orderNoGenerator;

    public RechargeService(RechargeOrderRepository orders,
                           WalletAccountRepository wallets,
                           WalletLedgerRepository walletLedger,
                           CoinPoolRepository poolRepository,
                           UnitOfWork unitOfWork,
                           OrderNoGenerator orderNoGenerator) {
        this.orders = orders;
        this.wallets = wallets;
        this.walletLedger = walletLedger;
        this.poolRepository = poolRepository;
        this.unitOfWork = unitOfWork;
        this.orderNoGenerator = orderNoGenerator;
    }

    /**
     * 下单（费率快照）。
     *
     * @param feeRate 该渠道当前费率（一次性取快照并入单，后续改配置不影响本单）
     */
    public RechargeOrder createOrder(long userId, long amountFen, long coinAmount,
                                     String payChannel, BigDecimal feeRate) {
        RechargeOrder order = new RechargeOrder(orderNoGenerator.next(BIZ_TYPE), userId,
                amountFen, coinAmount, payChannel, feeRate, RechargeOrder.STATUS_CREATED);
        return unitOfWork.execute(() -> {
            orders.save(order);
            return order;
        });
    }

    /** 支付成功回调：加币 + 池增量，同一事务；重复回调幂等。 */
    public RechargeOrder onPaid(String orderNo) {
        return unitOfWork.execute(() -> {
            RechargeOrder order = orders.findByOrderNo(orderNo)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.PARAM_INVALID, "充值单不存在", "order_no", "not_found"));
            if (RechargeOrder.STATUS_PAID.equals(order.status())) {
                return order; // 回调重放：不重复加币
            }

            WalletAccount wallet = wallets.find(order.userId())
                    .orElseGet(() -> WalletAccount.empty(order.userId()));
            WalletAccount after = wallet.credit(order.coinAmount());
            wallets.save(after, wallet.version());
            walletLedger.append(new WalletLedgerEntry(order.userId(), BIZ_TYPE, order.orderNo(),
                    LedgerDirection.IN, order.coinAmount(), after.balance(), order.payChannel()));

            // 币池增量：coins / gross_fen / fee_fen（fee = round(实付 × 费率快照)）
            CoinPoolState pool = poolRepository.load();
            long feeFen = MoneyMath.halfUp(
                    BigDecimal.valueOf(order.amountFen()).multiply(order.channelFeeRate()));
            pool.credit(order.payChannel(), order.coinAmount(), order.amountFen(), feeFen, order.channelFeeRate());
            poolRepository.save(pool);

            RechargeOrder paid = order.paid();
            orders.save(paid);
            return paid;
        });
    }
}
