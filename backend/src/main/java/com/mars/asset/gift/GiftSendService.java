package com.mars.asset.gift;

import com.mars.account.MinorLimitConfig;
import com.mars.account.MinorLimitPolicy;
import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.ledger.EarningLedgerEntry;
import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.repo.EarningAccountRepository;
import com.mars.asset.repo.EarningLedgerRepository;
import com.mars.asset.repo.GiftOrderRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;

/**
 * 礼物赠送（契约 §4.6 API-ASSET-02 · <b>AC-5</b> · S1a §3.4）。
 *
 * <p><b>同一本地事务内</b>完成四件事，保证「余额 / 流水 / 收益三者一致」：
 * <ol>
 *   <li>校验并扣减送礼人钱包（乐观锁 {@code version}）；</li>
 *   <li>写钱包流水（{@code wallet_ledger}，{@code biz_no} 唯一）；</li>
 *   <li>累计主播收益（打赏进 {@code total_earned}，<b>不即时可提现</b>）；</li>
 *   <li>写收益流水（{@code earning_ledger}）+ 礼物订单（{@code idempotency_key} 唯一）。</li>
 * </ol>
 *
 * <p>任一环节抛异常 → 事务回滚，<b>不留任何部分写入</b>；同 {@code Idempotency-Key} 重放 →
 * 返回首次结果，<b>绝不重复扣费</b>。
 */
public final class GiftSendService {

    /** 流水业务类型。 */
    public static final String BIZ_TYPE = "GIFT";

    private final GiftCatalog catalog;
    private final MinorLimitPolicy minorLimitPolicy;
    private final MinorLimitConfig minorLimitConfig;
    private final WalletAccountRepository walletAccounts;
    private final WalletLedgerRepository walletLedger;
    private final EarningAccountRepository earningAccounts;
    private final EarningLedgerRepository earningLedger;
    private final GiftOrderRepository giftOrders;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator orderNoGenerator;
    private final GiftResultCache resultCache;

    public GiftSendService(GiftCatalog catalog,
                           MinorLimitPolicy minorLimitPolicy,
                           MinorLimitConfig minorLimitConfig,
                           WalletAccountRepository walletAccounts,
                           WalletLedgerRepository walletLedger,
                           EarningAccountRepository earningAccounts,
                           EarningLedgerRepository earningLedger,
                           GiftOrderRepository giftOrders,
                           UnitOfWork unitOfWork,
                           OrderNoGenerator orderNoGenerator,
                           GiftResultCache resultCache) {
        this.catalog = catalog;
        this.minorLimitPolicy = minorLimitPolicy;
        this.minorLimitConfig = minorLimitConfig;
        this.walletAccounts = walletAccounts;
        this.walletLedger = walletLedger;
        this.earningAccounts = earningAccounts;
        this.earningLedger = earningLedger;
        this.giftOrders = giftOrders;
        this.unitOfWork = unitOfWork;
        this.orderNoGenerator = orderNoGenerator;
        this.resultCache = resultCache;
    }

    public GiftSendResult send(GiftSendCommand command) {
        // 快路径：命中结果缓存（Redis 语义），直接回吐首次结果，不进事务。
        var cached = resultCache.get(command.idempotencyKey());
        if (cached.isPresent()) {
            GiftSendResult previous = cached.get();
            return GiftSendResult.replay(previous.orderNo(), previous.balanceAfter(),
                    previous.totalCoin(), previous.anchorTotalEarned());
        }

        return unitOfWork.execute(() -> {
            // DB 唯一键是最终保证：同幂等键已存在 → 回吐首次结果，不再扣费。
            var existing = giftOrders.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return replayOf(existing.get());
            }

            GiftItem gift = catalog.find(command.giftId())
                    .filter(GiftItem::onSale)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.PARAM_INVALID, "礼物不存在或已下架", "gift_id", "unavailable"));

            long totalCoin = Math.multiplyExact(gift.priceCoin(), command.count());

            ErrorCode minorError = minorLimitPolicy.check(
                    command.isMinor(), minorLimitConfig, command.realnameVerified(),
                    totalCoin, command.todayConsumedCoin());
            if (minorError != null) {
                throw new BizException(minorError, "未成年打赏受限", "gift_id", "minor_limit");
            }

            WalletAccount wallet = walletAccounts.find(command.senderId())
                    .orElseThrow(() -> new BizException(
                            ErrorCode.PARAM_INVALID, "钱包不存在", "user_id", "not_found"));
            if (wallet.frozen()) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        "存在未结清退款，打赏能力已冻结", "balance", "frozen");
            }

            String orderNo = orderNoGenerator.next(BIZ_TYPE);

            WalletAccount walletAfter = wallet.debit(totalCoin);
            walletAccounts.save(walletAfter, wallet.version());
            walletLedger.append(WalletLedgerEntry.of(
                    command.senderId(), BIZ_TYPE, orderNo, LedgerDirection.OUT,
                    totalCoin, walletAfter.balance()));

            EarningAccount earning = earningAccounts.find(command.anchorId())
                    .orElseGet(() -> EarningAccount.empty(command.anchorId()));
            EarningAccount earningAfter = earning.accrueTip(totalCoin);
            earningAccounts.save(earningAfter, earning.version());
            earningLedger.append(new EarningLedgerEntry(
                    command.anchorId(), BIZ_TYPE, orderNo, LedgerDirection.IN,
                    totalCoin, earningAfter.totalEarned()));

            giftOrders.save(new GiftOrder(orderNo, command.idempotencyKey(), command.sessionId(),
                    command.senderId(), command.anchorId(), command.giftId(), command.count(),
                    totalCoin, GiftOrder.STATUS_NORMAL));

            GiftSendResult result = GiftSendResult.of(
                    orderNo, walletAfter.balance(), totalCoin, earningAfter.totalEarned());
            resultCache.put(command.idempotencyKey(), result);
            return result;
        });
    }

    /** 由已落库的单号回吐「首次结果」（余额取流水记录中的 {@code balance_after}）。 */
    private GiftSendResult replayOf(GiftOrder order) {
        long balanceAfter = walletLedger.findByBizNo(order.orderNo())
                .map(WalletLedgerEntry::balanceAfter)
                .orElse(0L);
        long anchorTotal = earningLedger.findByBizNo(order.orderNo())
                .map(EarningLedgerEntry::balanceAfter)
                .orElse(0L);
        GiftSendResult replay = GiftSendResult.replay(
                order.orderNo(), balanceAfter, order.totalCoin(), anchorTotal);
        resultCache.put(order.idempotencyKey(), replay);
        return replay;
    }
}
