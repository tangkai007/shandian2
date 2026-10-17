package com.mars.asset.iap;

import java.util.List;
import java.util.Optional;

/**
 * IAP 商品目录（契约 §4.6 API-ASSET-07/08 · OP-11）。
 *
 * <p>客户端只传 {@code product_id}，<b>币数与金额由服务端决定</b>（与充值档位同口径）；
 * 正式实现应读取 App Store Connect / {@code sys_config}，本期提供配置默认档位。
 */
public interface IapProductCatalog {

    /**
     * IAP 商品档位。
     *
     * @param productId Apple 商品标识
     * @param coinAmount 到账币数
     * @param amountFen  记账金额（分）
     * @param priceTier  展示价档（如 {@code ¥6}）
     * @param status     {@code ON} 在售 / {@code OFF} 下架
     */
    record IapProduct(String productId, long coinAmount, long amountFen, String priceTier, String status) {

        public static final String STATUS_ON = "ON";
        public static final String STATUS_OFF = "OFF";

        public boolean onSale() {
            return STATUS_ON.equals(status);
        }
    }

    Optional<IapProduct> find(String productId);

    List<IapProduct> all();
}
