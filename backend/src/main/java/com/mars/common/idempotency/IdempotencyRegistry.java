package com.mars.common.idempotency;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 幂等登记（内存实现，供单测；生产以业务唯一键落库 + Redis 锁为准，S1a §6.3）。
 *
 * <p>语义（S2a v1.2 §2.6）：同一 {@code Idempotency-Key} 的重放<b>返回 200 + 首次结果</b>，
 * <b>绝不重复执行</b>资金动作 —— 因此调用方必须先 {@link #markIfAbsent(String)}
 * 判断是否首次，命中重放时直接取 {@link #resultOf(String)} 回吐。
 */
public final class IdempotencyRegistry {

    private final Map<String, String> resultRefByKey = new HashMap<>();

    /**
     * 标记该键首次出现。
     *
     * @return {@code true} 表示首次（应执行）；{@code false} 表示重放（应取首次结果）
     */
    public boolean markIfAbsent(String key) {
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 Idempotency-Key",
                    "Idempotency-Key", "required");
        }
        if (resultRefByKey.containsKey(key)) {
            return false;
        }
        resultRefByKey.put(key, "");
        return true;
    }

    /** 绑定首次执行结果，供重放回吐。 */
    public void bindResult(String key, String resultRef) {
        resultRefByKey.put(key, resultRef);
    }

    public Optional<String> resultOf(String key) {
        return Optional.ofNullable(resultRefByKey.get(key));
    }
}
