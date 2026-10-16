package com.mars.earning;

import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.ledger.EarningLedgerEntry;
import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.repo.EarningAccountRepository;
import com.mars.asset.repo.EarningLedgerRepository;
import com.mars.asset.repo.WithdrawalOrderRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.support.OrderNoGenerator;
import com.mars.common.tx.UnitOfWork;

import java.util.Optional;

/**
 * 提现（契约 §4.7 API-EARN-06/07 · BR-6 / OP-16 / S1a §3.4.8）。
 *
 * <p>流程：申请（校验 + 可提现 → 提现中）→ 审核通过打款（提现中扣减 + 收益流水 OUT）
 * 或驳回（提现中退回可提现）。个税按 {@link TaxMode} 两形态计算到账。
 *
 * <p>提现单持久化经 {@link WithdrawalOrderRepository}（DB 唯一 {@code order_no}），
 * 状态变更与收益账户变更在<b>同一事务</b>内提交。
 */
public final class WithdrawalService {

    public static final String BIZ_TYPE = "WITHDRAW";

    private final WithdrawalPolicy policy;
    private final EarningAccountRepository earningAccounts;
    private final EarningLedgerRepository earningLedger;
    private final WithdrawalOrderRepository withdrawalOrders;
    private final UnitOfWork unitOfWork;
    private final OrderNoGenerator orderNoGenerator;

    public WithdrawalService(WithdrawalPolicy policy,
                             EarningAccountRepository earningAccounts,
                             EarningLedgerRepository earningLedger,
                             WithdrawalOrderRepository withdrawalOrders,
                             UnitOfWork unitOfWork,
                             OrderNoGenerator orderNoGenerator) {
        this.policy = policy;
        this.earningAccounts = earningAccounts;
        this.earningLedger = earningLedger;
        this.withdrawalOrders = withdrawalOrders;
        this.unitOfWork = unitOfWork;
        this.orderNoGenerator = orderNoGenerator;
    }

    /** 提现申请：校验 → 锁定可提现余额 → 落单 PENDING。 */
    public WithdrawalOrder request(WithdrawalCommand command) {
        return unitOfWork.execute(() -> {
            EarningAccount account = earningAccounts.find(command.anchorId())
                    .orElseThrow(() -> new BizException(
                            ErrorCode.WITHDRAW_BELOW_MIN, "无可提现余额", "available", "insufficient"));

            ErrorCode error = policy.check(command.anchorType(), account.available(),
                    command.amountFen(), command.bankNameMatchesRealname());
            if (error != null) {
                throw new BizException(error, "提现校验未通过", "amount", error.name());
            }

            EarningAccount locked = account.lockWithdraw(command.amountFen());
            earningAccounts.save(locked, account.version());

            long netAmount = policy.netAmount(command.taxMode(), command.amountFen(), command.taxWithheldFen());
            WithdrawalOrder order = new WithdrawalOrder(orderNoGenerator.next(BIZ_TYPE), command.anchorId(),
                    command.amountFen(), command.taxWithheldFen(), netAmount, command.taxMode(),
                    WithdrawalOrder.STATUS_PENDING, null);
            withdrawalOrders.save(order);
            return order;
        });
    }

    /** 审核通过并打款：提现中扣减 + 收益流水 OUT。 */
    public WithdrawalOrder approve(String orderNo) {
        return unitOfWork.execute(() -> {
            WithdrawalOrder order = require(orderNo);
            if (!WithdrawalOrder.STATUS_PENDING.equals(order.status())) {
                return order; // 幂等
            }
            EarningAccount account = earningAccounts.find(order.anchorId())
                    .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "收益账户不存在"));
            EarningAccount after = account.completeWithdraw(order.amountFen());
            earningAccounts.save(after, account.version());
            earningLedger.append(new EarningLedgerEntry(order.anchorId(), BIZ_TYPE, order.orderNo(),
                    LedgerDirection.OUT, order.amountFen(), after.available()));

            WithdrawalOrder paid = order.withStatus(WithdrawalOrder.STATUS_PAID, null);
            withdrawalOrders.save(paid);
            return paid;
        });
    }

    /** 驳回：提现中退回可提现。 */
    public WithdrawalOrder reject(String orderNo, String reason) {
        return unitOfWork.execute(() -> {
            WithdrawalOrder order = require(orderNo);
            if (!WithdrawalOrder.STATUS_PENDING.equals(order.status())) {
                return order; // 幂等
            }
            EarningAccount account = earningAccounts.find(order.anchorId())
                    .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "收益账户不存在"));
            EarningAccount after = account.unlockWithdraw(order.amountFen());
            earningAccounts.save(after, account.version());

            WithdrawalOrder rejected = order.withStatus(WithdrawalOrder.STATUS_REJECTED, reason);
            withdrawalOrders.save(rejected);
            return rejected;
        });
    }

    public Optional<WithdrawalOrder> find(String orderNo) {
        return withdrawalOrders.findByOrderNo(orderNo);
    }

    private WithdrawalOrder require(String orderNo) {
        return find(orderNo).orElseThrow(() -> new BizException(
                ErrorCode.PARAM_INVALID, "提现单不存在", "order_no", "not_found"));
    }
}
