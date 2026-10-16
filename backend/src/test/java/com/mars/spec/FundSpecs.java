package com.mars.spec;

import com.mars.asset.gift.GiftSendCommand;
import com.mars.asset.gift.GiftSendResult;
import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.recharge.RechargeOrder;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.earning.AnchorType;
import com.mars.earning.TaxMode;
import com.mars.earning.WithdrawalCommand;
import com.mars.earning.WithdrawalOrder;
import com.mars.support.FundFixture;
import com.mars.testkit.Registry;

import java.math.BigDecimal;

/**
 * 资金链路 spec（<b>AC-5</b> / BR-4 / BR-6 · 契约 §2.6 / §4.6 / §4.7 · S1a §3.4）。
 *
 * <p>断言目标：余额 / 流水 / 收益三者一致、异常原子回滚、幂等重放不重复扣费、
 * 充值 → 币池 → 加币同事务、退款逆向与冻结、提现起提与两模式到账。
 */
public final class FundSpecs {

    private FundSpecs() {
    }

    private static GiftSendCommand gift(String key, long sender, long anchor, long giftId, int count) {
        return new GiftSendCommand(key, 9001L, sender, anchor, giftId, count, false, true, 0L);
    }

    public static void register(Registry registry) {

        registry.add("AC-5 · 赠送成功：余额 / 流水 / 收益三者一致", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1001L, 5_000L);
            f.seedGift(1L, 10L, true);

            GiftSendResult result = f.giftSend.send(gift("k-ok", 1001L, 2001L, 1L, 3));

            a.eqLong(result.totalCoin(), 30L, "消费币 = 单价 10 × 3");
            a.eqLong(result.balanceAfter(), 4_970L, "扣费后余额");
            a.isFalse(result.replay(), "首次非重放");
            a.eqLong(f.balanceOf(1001L), 4_970L, "钱包余额已扣减");

            a.eqLong((long) f.walletLedgers.findByUser(1001L).size(), 1L, "钱包流水恰好 1 条");
            WalletLedgerEntry entry = f.walletLedgers.findByUser(1001L).get(0);
            a.eq(entry.direction(), LedgerDirection.OUT, "流水方向 OUT");
            a.eqLong(entry.amount(), 30L, "流水金额 = 消费币");
            a.eqLong(entry.balanceAfter(), 4_970L, "流水 balance_after 与钱包一致");
            a.eq(entry.bizNo(), result.orderNo(), "流水 biz_no = 礼物单号");

            a.eqLong(f.totalEarnedOf(2001L), 30L, "主播 total_earned 增加 30");
            a.eqLong(f.availableOf(2001L), 0L, "打赏不即时可提现（available 仍为 0）");
            a.eqLong((long) f.earningLedgers.findByAnchor(2001L).size(), 1L, "收益流水恰好 1 条");
            a.isTrue(f.giftOrders.findByIdempotencyKey("k-ok").isPresent(), "礼物订单已落库");
        });

        registry.add("AC-5 · 幂等重放：返回首次结果且不重复扣费", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1001L, 5_000L);
            f.seedGift(1L, 10L, true);

            GiftSendResult first = f.giftSend.send(gift("k-replay", 1001L, 2001L, 1L, 3));
            GiftSendResult second = f.giftSend.send(gift("k-replay", 1001L, 2001L, 1L, 3));

            a.isTrue(second.replay(), "第二次标记为重放");
            a.eq(second.orderNo(), first.orderNo(), "重放返回同一单号");
            a.eqLong(second.balanceAfter(), first.balanceAfter(), "重放返回首次余额快照");
            a.eqLong(f.balanceOf(1001L), 4_970L, "余额只被扣一次");
            a.eqLong(f.walletLedgers.countByBizNo(first.orderNo()), 1L, "同一 biz_no 仅 1 条流水");
            a.eqLong(f.totalEarnedOf(2001L), 30L, "主播收益只累计一次");
            a.eqLong(f.earningLedgers.countByBizNo(first.orderNo()), 1L, "收益流水仅 1 条");
            a.eqLong((long) f.giftOrders.findBySender(1001L).size(), 1L, "只生成 1 张礼物订单");
        });

        registry.add("AC-5 · 余额不足：拒绝且无任何部分写入（原子性）", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1002L, 20L);
            f.seedGift(2L, 10L, true);

            ErrorCode code = null;
            String field = null;
            try {
                f.giftSend.send(gift("k-insufficient", 1002L, 3001L, 2L, 3));
            } catch (BizException ex) {
                code = ex.errorCode();
                field = ex.detailField();
            }

            a.eq(code, ErrorCode.PARAM_INVALID, "余额不足 → 40001");
            a.eq(field, "balance", "定位字段 field=balance");
            a.eqLong(f.balanceOf(1002L), 20L, "余额未被扣减");
            a.eqLong((long) f.walletLedgers.findByUser(1002L).size(), 0L, "无钱包流水残留");
            a.eqLong(f.totalEarnedOf(3001L), 0L, "主播收益未变");
            a.eqLong((long) f.earningLedgers.findByAnchor(3001L).size(), 0L, "无收益流水残留");
            a.eqLong((long) f.giftOrders.findBySender(1002L).size(), 0L, "无礼物订单残留");
        });

        registry.add("AC-5 · 礼物下架：拒绝且无写入", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1003L, 5_000L);
            f.seedGift(3L, 10L, false);

            ErrorCode code = null;
            try {
                f.giftSend.send(gift("k-offsale", 1003L, 3002L, 3L, 1));
            } catch (BizException ex) {
                code = ex.errorCode();
            }
            a.eq(code, ErrorCode.PARAM_INVALID, "下架礼物 → 40001");
            a.eqLong(f.balanceOf(1003L), 5_000L, "余额未变");
            a.eqLong((long) f.walletLedgers.findByUser(1003L).size(), 0L, "无流水残留");
            a.eqLong((long) f.giftOrders.findBySender(1003L).size(), 0L, "无订单残留");
        });

        registry.add("AC-5 · 未成年超限：40320 且无写入", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1004L, 5_000L);
            f.seedGift(4L, 1_000L, true);

            ErrorCode code = null;
            try {
                f.giftSend.send(new GiftSendCommand("k-minor", 9001L, 1004L, 3003L, 4L,
                        2, true, true, 0L));
            } catch (BizException ex) {
                code = ex.errorCode();
            }
            a.eq(code, ErrorCode.MINOR_LIMIT_EXCEEDED, "未成年单笔超限 → 40320");
            a.eqLong(f.balanceOf(1004L), 5_000L, "余额未变");
            a.eqLong((long) f.walletLedgers.findByUser(1004L).size(), 0L, "无流水残留");
            a.eqLong(f.totalEarnedOf(3003L), 0L, "收益未变");
        });

        registry.add("AC-5 · 乐观锁：版本冲突 → 40900 且首笔效果保留", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1005L, 1_000L);

            // 第一笔正常落库（version 1 → 2）
            f.wallets.save(new WalletAccount(1005L, 900L, 2L, false), 1L);
            a.eqLong(f.balanceOf(1005L), 900L, "首笔已落库");

            // 第二笔持旧版本写入 → 冲突
            ErrorCode code = null;
            try {
                f.wallets.save(new WalletAccount(1005L, 800L, 2L, false), 1L);
            } catch (BizException ex) {
                code = ex.errorCode();
            }
            a.eq(code, ErrorCode.CONFLICT, "陈旧版本 → 40900");
            a.eqLong(f.balanceOf(1005L), 900L, "冲突写入被拒，首笔效果完整");
        });

        registry.add("AC-5 · 充值：费率快照 + 加币与币池同事务 + 回调幂等", a -> {
            FundFixture f = new FundFixture();

            RechargeOrder order = f.recharge.createOrder(1006L, 10_000L, 10_000L,
                    "WECHAT", new BigDecimal("0.006"));
            a.eq(order.status(), RechargeOrder.STATUS_CREATED, "下单为 CREATED");
            a.eq(order.channelFeeRate(), new BigDecimal("0.006"), "费率下单快照");

            RechargeOrder paid = f.recharge.onPaid(order.orderNo());
            a.eq(paid.status(), RechargeOrder.STATUS_PAID, "回调后为 PAID");
            a.eqLong(f.balanceOf(1006L), 10_000L, "钱包加币 10,000");
            a.eqLong(f.walletLedgers.countByBizNo(order.orderNo()), 1L, "充值流水 1 条");
            a.eq(f.walletLedgers.findByUser(1006L).get(0).direction(), LedgerDirection.IN, "流水方向 IN");
            a.eq(f.walletLedgers.findByUser(1006L).get(0).channel(), "WECHAT", "流水记录渠道");

            a.eqLong(f.pools.load().coinPoolTotal(), 10_000L, "币池币数增量");
            a.eqLong(f.pools.load().grossPoolFen(), 10_000L, "币池实付池增量");
            a.eqLong(f.pools.load().feePoolFen(), 60L, "币池渠道费 = round(10,000 × 0.6%) = 60");

            // 回调重放：不重复加币、不重复累计池
            f.recharge.onPaid(order.orderNo());
            a.eqLong(f.balanceOf(1006L), 10_000L, "重复回调不重复加币");
            a.eqLong(f.walletLedgers.countByBizNo(order.orderNo()), 1L, "重复回调不新增流水");
            a.eqLong(f.pools.load().coinPoolTotal(), 10_000L, "重复回调不重复累计币池");
        });

        registry.add("AC-5 · IAP：同一交易只加币一次 + 退款逆向（余额转负则冻结）", a -> {
            FundFixture f = new FundFixture();
            f.seedWallet(1007L, 0L);

            f.iap.verify(new com.mars.asset.iap.IapVerifyCommand(
                    "T-1", 1007L, "coin600", 10_000L, 10_000L, new BigDecimal("0.30"), "PROD"));
            a.eqLong(f.balanceOf(1007L), 10_000L, "IAP 加币 10,000");
            a.eqLong(f.pools.load().coinPoolTotal(), 10_000L, "IAP 计入币池");
            a.eqLong(f.pools.load().feePoolFen(), 3_000L, "IAP 渠道费 = 10,000 × 30%");

            f.iap.verify(new com.mars.asset.iap.IapVerifyCommand(
                    "T-1", 1007L, "coin600", 10_000L, 10_000L, new BigDecimal("0.30"), "PROD"));
            a.eqLong(f.balanceOf(1007L), 10_000L, "同一 transaction_id 不重复加币");

            f.iap.reverse("T-1", false);
            a.eqLong(f.balanceOf(1007L), 0L, "退款逆向扣减");
            a.eqLong(f.pools.load().coinPoolTotal(), 0L, "币池同步冲减");
            a.isFalse(f.frozenOf(1007L), "未转负，不冻结");

            // 已消费后再退款 → 余额转负并冻结打赏能力（§3.4.7）
            FundFixture g = new FundFixture();
            g.seedWallet(1008L, 0L);
            g.seedGift(9L, 100L, true);
            g.iap.verify(new com.mars.asset.iap.IapVerifyCommand(
                    "T-2", 1008L, "coin600", 1_000L, 1_000L, new BigDecimal("0.30"), "PROD"));
            g.giftSend.send(gift("k-after-iap", 1008L, 3004L, 9L, 6));
            a.eqLong(g.balanceOf(1008L), 400L, "消费 600 后余额 400");

            g.iap.reverse("T-2", true);
            a.eqLong(g.balanceOf(1008L), -600L, "撤销后余额转负（不静默吞掉）");
            a.isTrue(g.frozenOf(1008L), "余额转负 → 冻结打赏能力");
        });

        registry.add("AC-5 · 提现：起提 10000 分、锁定 → 打款 / 驳回回退", a -> {
            FundFixture f = new FundFixture();
            f.earnings.seed(new EarningAccount(4001L, 0L, 20_000L, 0L, 1L));

            WithdrawalOrder order = f.withdrawal.request(new WithdrawalCommand(
                    4001L, AnchorType.PERSONAL, 20_000L, true, TaxMode.SETTLE, 1_200L));
            a.eq(order.status(), WithdrawalOrder.STATUS_PENDING, "申请为 PENDING");
            a.eqLong(order.netAmountFen(), 20_000L, "SETTLE 模式到账 = 税前金额（个税已结算代扣）");
            a.eqLong(f.availableOf(4001L), 0L, "可提现被锁定");
            a.eqLong(f.earnings.find(4001L).orElseThrow().withdrawing(), 20_000L, "转入提现中");

            f.withdrawal.approve(order.orderNo());
            a.eqLong(f.earnings.find(4001L).orElseThrow().withdrawing(), 0L, "打款后提现中清零");
            a.eqLong(f.earningLedgers.countByBizNo(order.orderNo()), 1L, "打款写 1 条收益流水");
            a.eq(f.earningLedgers.findByAnchor(4001L).get(0).direction(), LedgerDirection.OUT,
                    "收益流水方向 OUT");

            // 驳回 → 提现中退回可提现
            FundFixture r = new FundFixture();
            r.earnings.seed(new EarningAccount(4002L, 0L, 20_000L, 0L, 1L));
            WithdrawalOrder rejected = r.withdrawal.request(new WithdrawalCommand(
                    4002L, AnchorType.PERSONAL, 15_000L, true, TaxMode.SETTLE, 0L));
            r.withdrawal.reject(rejected.orderNo(), "信息不全");
            a.eq(r.withdrawal.find(rejected.orderNo()).orElseThrow().status(),
                    WithdrawalOrder.STATUS_REJECTED, "驳回状态");
            a.eqLong(r.availableOf(4002L), 20_000L, "驳回后余额退回可提现");
            a.eqLong(r.earnings.find(4002L).orElseThrow().withdrawing(), 0L, "提现中清零");

            // 非个人主体 / 未达起提 / WITHDRAW 模式
            FundFixture x = new FundFixture();
            x.earnings.seed(new EarningAccount(4003L, 0L, 20_000L, 0L, 1L));
            ErrorCode enterprise = null;
            try {
                x.withdrawal.request(new WithdrawalCommand(
                        4003L, AnchorType.ENTERPRISE, 20_000L, true, TaxMode.SETTLE, 0L));
            } catch (BizException ex) {
                enterprise = ex.errorCode();
            }
            a.eq(enterprise, ErrorCode.FORBIDDEN, "非个人主播 → 40300");

            ErrorCode belowMin = null;
            try {
                x.withdrawal.request(new WithdrawalCommand(
                        4003L, AnchorType.PERSONAL, 9_999L, true, TaxMode.SETTLE, 0L));
            } catch (BizException ex) {
                belowMin = ex.errorCode();
            }
            a.eq(belowMin, ErrorCode.WITHDRAW_BELOW_MIN, "未达起提 → 40340");

            WithdrawalOrder withdrawMode = x.withdrawal.request(new WithdrawalCommand(
                    4003L, AnchorType.PERSONAL, 20_000L, true, TaxMode.WITHDRAW, 1_200L));
            a.eqLong(withdrawMode.netAmountFen(), 18_800L, "WITHDRAW 模式到账 = 税前 − 代扣个税");
            a.eqLong(withdrawMode.taxFen(), 1_200L, "记录代扣税额（tax_withheld）");
        });
    }
}
