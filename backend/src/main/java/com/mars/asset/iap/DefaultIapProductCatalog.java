package com.mars.asset.iap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IAP 商品档位默认实现（<b>占位</b>）：与充值档位同口径，1 元 = 1 币。
 *
 * <p>商品标识 {@code com.mars.iap.coins.*}；正式实现必须改由运营后台 / App Store Connect 下发。
 * 生产 profile 下不装配。
 */
@Component
@Profile({"local", "dev", "test", "default"})
public class DefaultIapProductCatalog implements IapProductCatalog {

    private final Map<String, IapProduct> byId = new LinkedHashMap<>();

    public DefaultIapProductCatalog() {
        put("com.mars.iap.coins.6", 600L, 600L, "¥6");
        put("com.mars.iap.coins.30", 3_000L, 3_000L, "¥30");
        put("com.mars.iap.coins.98", 9_800L, 9_800L, "¥98");
        put("com.mars.iap.coins.518", 51_800L, 51_800L, "¥518");
    }

    private void put(String productId, long coinAmount, long amountFen, String priceTier) {
        byId.put(productId, new IapProduct(productId, coinAmount, amountFen, priceTier, IapProduct.STATUS_ON));
    }

    @Override
    public Optional<IapProduct> find(String productId) {
        return Optional.ofNullable(byId.get(productId));
    }

    @Override
    public List<IapProduct> all() {
        return List.copyOf(byId.values());
    }
}
