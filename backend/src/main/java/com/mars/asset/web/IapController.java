package com.mars.asset.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.asset.iap.IapProductCatalog;
import com.mars.asset.iap.IapReceiptVerifier;
import com.mars.asset.iap.IapTransaction;
import com.mars.asset.iap.IapVerifyCommand;
import com.mars.asset.iap.IapVerifyService;
import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AuthContext;
import com.mars.common.security.RequireRoles;
import com.mars.common.web.RequireIdempotencyKey;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * IAP 端点（契约 §4.6 API-ASSET-07/08 · iOS 专用 · OP-11 · RISK-7）。
 *
 * <p>加币经 {@link IapVerifyService}（同一事务：钱包 → 流水 → 币池 → {@code iap_transaction}），
 * 幂等键为 {@code transaction_id}（同一交易只加币一次）。
 *
 * <p><b>验签偏差（已登记限制）</b>：契约 §4.6 要求 JWS 验签 + 调 App Store Server API；
 * 本期装配 {@link IapReceiptVerifier} 占位实现，<b>不验签</b>，故 {@code product_id} /
 * {@code transaction_id} 由客户端显式提供（真实实现应从 JWS 解出）。
 */
@RestController
@RequestMapping("/api/v1/wallet/iap")
@RequireRoles({"VIEWER", "ANCHOR"})
public class IapController {

    private final IapProductCatalog products;
    private final IapReceiptVerifier verifier;
    private final IapVerifyService verifyService;
    private final WalletAccountRepository wallets;
    private final BigDecimal iapFeeRate;

    public IapController(IapProductCatalog products,
                         IapReceiptVerifier verifier,
                         IapVerifyService verifyService,
                         WalletAccountRepository wallets,
                         @Value("${mars.channel-fee-rate.iap:0.30}") BigDecimal iapFeeRate) {
        this.products = products;
        this.verifier = verifier;
        this.verifyService = verifyService;
        this.wallets = wallets;
        this.iapFeeRate = iapFeeRate;
    }

    /** API-ASSET-07：IAP 商品档位（消耗型）。 */
    @GetMapping("/products")
    public ApiResponse<ProductList> products() {
        List<Product> list = products.all().stream()
                .filter(IapProductCatalog.IapProduct::onSale)
                .map(product -> new Product(product.productId(), product.coinAmount(),
                        product.priceTier(), product.status()))
                .toList();
        return ApiResponse.ok(new ProductList(list));
    }

    /** API-ASSET-08：校验回执并加币（幂等必填）。 */
    @PostMapping("/verify")
    @RequireIdempotencyKey
    public ApiResponse<VerifyAck> verify(@Valid @RequestBody VerifyRequest request) {
        long userId = AuthContext.requireUserId();
        IapProductCatalog.IapProduct product = products.find(request.productId())
                .orElseThrow(() -> new BizException(ErrorCode.IAP_VERIFY_FAILED,
                        "IAP 商品不存在", "product_id", "not_found"));

        String env = verifier.verify(product.productId(), request.signedTransactionInfo());

        IapTransaction transaction = verifyService.verify(new IapVerifyCommand(
                request.transactionId(), userId, product.productId(), product.amountFen(),
                product.coinAmount(), iapFeeRate, env));

        long balance = wallets.find(userId).map(WalletAccount::balance).orElse(0L);
        return ApiResponse.ok(new VerifyAck(balance, transaction.coinAmount(),
                transaction.transactionId()));
    }

    /** 校验请求体（{@code product_id}/{@code transaction_id} 为占位实现显式位，见类注释）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VerifyRequest(String signedTransactionInfo, String productId, String transactionId) {
    }

    /** 校验应答（契约 §4.6 API-ASSET-08：{@code balance / coin_amount / transaction_id}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VerifyAck(long balance, long coinAmount, String transactionId) {
    }

    /** 商品档位（前端/客户端消耗型商品）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Product(String productId, long coinAmount, String priceTier, String status) {
    }

    /** 商品列表。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProductList(List<Product> list) {
    }
}
