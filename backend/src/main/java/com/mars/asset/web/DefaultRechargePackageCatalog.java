package com.mars.asset.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 充值档位默认实现（<b>占位</b>）：1 元 = 1 币，档位 6 / 30 / 98 / 518 元。
 *
 * <p>仅用于打通「客户端只传档位 → 服务端定价 → 费率快照」的链路；档位与赠送币规则
 * 必须改由运营后台/`sys_config` 下发（下个增量）。生产 profile 下不装配。
 */
@Component
@Profile({"local", "dev", "test", "default"})
public class DefaultRechargePackageCatalog implements RechargePackageCatalog {

    private final Map<Long, RechargePackage> byId = new LinkedHashMap<>();

    public DefaultRechargePackageCatalog() {
        byId.put(1L, new RechargePackage(1L, 600L, 600L, null));
        byId.put(2L, new RechargePackage(2L, 3_000L, 3_000L, null));
        byId.put(3L, new RechargePackage(3L, 9_800L, 9_800L, null));
        byId.put(4L, new RechargePackage(4L, 51_800L, 51_800L, null));
    }

    @Override
    public Optional<RechargePackage> find(long packageId) {
        return Optional.ofNullable(byId.get(packageId));
    }

    @Override
    public Map<Long, RechargePackage> all() {
        return Map.copyOf(byId);
    }
}
