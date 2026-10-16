package com.mars.asset.web;

import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.recharge.RechargeOrder;
import com.mars.asset.recharge.RechargeService;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.asset.repo.WalletLedgerRepository;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.PageResult;
import com.mars.common.security.AuthContext;
import com.mars.common.security.RequireRoles;
import com.mars.common.web.RequireIdempotencyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钱包与充值端点（契约 §4.6 API-ASSET-03…06）。
 *
 * <p>资金写入全部经 {@link RechargeService}（同一本地事务：订单 → 钱包 → 流水 → 币池）。
 */
@RestController
@RequestMapping("/api/v1/wallet")
@RequireRoles({"VIEWER", "ANCHOR"})
public class WalletController {

    private final WalletAccountRepository wallets;
    private final WalletLedgerRepository ledger;
    private final RechargeOrderRepository orders;
    private final RechargeService rechargeService;
    private final RechargePackageCatalog packageCatalog;
    private final Map<String, BigDecimal> channelFeeRates;

    public WalletController(WalletAccountRepository wallets,
                            WalletLedgerRepository ledger,
                            RechargeOrderRepository orders,
                            RechargeService rechargeService,
                            RechargePackageCatalog packageCatalog,
                            @Value("${mars.channel-fee-rate.wechat:0.006}") BigDecimal wechatRate,
                            @Value("${mars.channel-fee-rate.alipay:0.006}") BigDecimal alipayRate,
                            @Value("${mars.channel-fee-rate.iap:0.30}") BigDecimal iapRate) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.orders = orders;
        this.rechargeService = rechargeService;
        this.packageCatalog = packageCatalog;
        this.channelFeeRates = new LinkedHashMap<>();
        this.channelFeeRates.put("WECHAT", wechatRate);
        this.channelFeeRates.put("ALIPAY", alipayRate);
        this.channelFeeRates.put("IAP", iapRate);
    }

    public record RechargeRequest(@Positive long packageId, @NotBlank String payChannel) {
    }

    public record WalletDto(long balance, List<RechargePackageCatalog.RechargePackage> packages) {
    }

    /** 契约 §4.6 API-ASSET-03。 */
    @GetMapping
    public ApiResponse<WalletDto> wallet() {
        long userId = AuthContext.requireUserId();
        long balance = wallets.find(userId).map(WalletAccount::balance).orElse(0L);
        List<RechargePackageCatalog.RechargePackage> packages =
                new ArrayList<>(packageCatalog.all().values());
        return ApiResponse.ok(new WalletDto(balance, packages));
    }

    /** 契约 §4.6 API-ASSET-04（分页）。 */
    @GetMapping("/ledger")
    public ApiResponse<PageResult<WalletLedgerEntry>> ledger(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = AuthContext.requireUserId();
        if (!PageResult.isPageValid(page, size)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分页参数越界", "size", "out_of_range");
        }
        List<WalletLedgerEntry> all = new ArrayList<>(ledger.findByUser(userId));
        all.sort((left, right) -> Long.compare(right.hashCode(), left.hashCode()));
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return ApiResponse.ok(new PageResult<>(all.subList(from, to), all.size(), page, size));
    }

    /**
     * 契约 §4.6 API-ASSET-05：下单（<b>幂等必填</b>；费率<b>下单快照</b>）。
     *
     * <p>iOS 端不得调用本端点（必须走 IAP）；由客户端 platform 参数在前端分流，服务端下方再校验。
     */
    @PostMapping("/recharge")
    @RequireIdempotencyKey
    public ApiResponse<RechargeOrder> recharge(@Valid @RequestBody RechargeRequest request) {
        long userId = AuthContext.requireUserId();
        if ("IAP".equals(request.payChannel())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "iOS 充值必须走 IAP（契约 §4.6）", "pay_channel", "iap_required");
        }
        BigDecimal feeRate = channelFeeRates.get(request.payChannel());
        if (feeRate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的支付渠道", "pay_channel", "unsupported");
        }
        RechargePackageCatalog.RechargePackage pack = packageCatalog.find(request.packageId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.PARAM_INVALID, "充值档位不存在或已下架", "package_id", "not_found"));

        RechargeOrder order = rechargeService.createOrder(
                userId, pack.amountFen(), pack.coinAmount(), request.payChannel(), feeRate);
        return ApiResponse.ok(order);
    }

    /** 契约 §4.6 API-ASSET-06：订单状态查询。 */
    @GetMapping("/orders/{orderNo}")
    public ApiResponse<RechargeOrder> order(@PathVariable String orderNo) {
        long userId = AuthContext.requireUserId();
        RechargeOrder order = orders.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(
                        ErrorCode.PARAM_INVALID, "充值单不存在", "order_no", "not_found"));
        if (order.userId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该订单");
        }
        return ApiResponse.ok(order);
    }
}
