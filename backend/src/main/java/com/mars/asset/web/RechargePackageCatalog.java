package com.mars.asset.web;

import java.util.Map;
import java.util.Optional;

/**
 * 充值档位目录（契约 §4.6 API-ASSET-05：客户端只传 {@code package_id}，<b>金额由服务端决定</b>）。
 *
 * <p>正式实现应读取 {@code sys_config} / 运营后台；本增量提供配置默认档位。
 */
public interface RechargePackageCatalog {

    record RechargePackage(long packageId, long amountFen, long coinAmount, String payChannel) {
    }

    Optional<RechargePackage> find(long packageId);

    Map<Long, RechargePackage> all();
}
