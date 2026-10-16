package com.mars.asset.gift;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 幂等「首次结果」缓存（生产实现 = Redis；DB 唯一键仍是最终保证，本缓存只避免重复计算）。
 */
public final class GiftResultCache {

    private final Map<String, GiftSendResult> byKey = new HashMap<>();

    public Optional<GiftSendResult> get(String idempotencyKey) {
        return Optional.ofNullable(byKey.get(idempotencyKey));
    }

    public void put(String idempotencyKey, GiftSendResult result) {
        byKey.put(idempotencyKey, result);
    }
}
