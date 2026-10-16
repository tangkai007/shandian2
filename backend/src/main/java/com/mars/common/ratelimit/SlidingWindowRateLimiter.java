package com.mars.common.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 滑动窗口限流器（内存实现，供单测与单机降级；生产以 Redis 为准，S1a §6.1）。
 *
 * <p>弹幕频控的唯一口径由 S1a 定义：<b>1 条/秒</b>（S2a v1.2 §2.8）。UI 不另立、不缓存。
 */
public final class SlidingWindowRateLimiter {

    private final int maxPermits;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hitsByKey = new HashMap<>();

    public SlidingWindowRateLimiter(int maxPermits, long windowMillis) {
        if (maxPermits <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("maxPermits and windowMillis must be positive");
        }
        this.maxPermits = maxPermits;
        this.windowMillis = windowMillis;
    }

    /**
     * 尝试获取一次许可。
     *
     * @return {@code true} 表示放行并已记账；{@code false} 表示窗口内已达上限
     */
    public boolean tryAcquire(String key, long nowMillis) {
        Deque<Long> hits = hitsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        evictExpired(hits, nowMillis);
        if (hits.size() >= maxPermits) {
            return false;
        }
        hits.addLast(nowMillis);
        return true;
    }

    public int remaining(String key, long nowMillis) {
        Deque<Long> hits = hitsByKey.get(key);
        if (hits == null) {
            return maxPermits;
        }
        evictExpired(hits, nowMillis);
        return Math.max(0, maxPermits - hits.size());
    }

    private void evictExpired(Deque<Long> hits, long nowMillis) {
        while (!hits.isEmpty() && nowMillis - hits.peekFirst() >= windowMillis) {
            hits.pollFirst();
        }
    }
}
