package com.mars.asset.iap;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;

import java.util.Locale;

/**
 * IAP 回执校验占位实现（<b>不做真实 JWS 验签、不调 App Store Server API</b>）。
 *
 * <p>通过条件：商品在售 + 回执非空。环境由回执前缀推断（{@code prod*} → {@code PROD}，
 * 其余 {@code SANDBOX}），使双环境链路可被区分。真实接入须替换本类并保留 {@code 40345} 语义。
 */
public final class PlaceholderIapReceiptVerifier implements IapReceiptVerifier {

    public static final String ENV_PROD = "PROD";
    public static final String ENV_SANDBOX = "SANDBOX";

    private final IapProductCatalog catalog;

    public PlaceholderIapReceiptVerifier(IapProductCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String verify(String productId, String receipt) {
        if (productId == null || productId.isBlank()) {
            throw new BizException(ErrorCode.IAP_VERIFY_FAILED, "缺少商品标识", "product_id", "blank");
        }
        if (receipt == null || receipt.isBlank()) {
            throw new BizException(ErrorCode.IAP_VERIFY_FAILED, "IAP 回执缺失", "receipt", "blank");
        }
        IapProductCatalog.IapProduct product = catalog.find(productId.trim())
                .orElseThrow(() -> new BizException(ErrorCode.IAP_VERIFY_FAILED,
                        "IAP 商品不存在", "product_id", "not_found"));
        if (!product.onSale()) {
            throw new BizException(ErrorCode.IAP_VERIFY_FAILED, "IAP 商品已下架", "product_id", "off_sale");
        }
        return receipt.trim().toLowerCase(Locale.ROOT).startsWith("prod") ? ENV_PROD : ENV_SANDBOX;
    }
}
