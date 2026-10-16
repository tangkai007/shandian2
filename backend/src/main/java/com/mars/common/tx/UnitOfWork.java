package com.mars.common.tx;

import java.util.function.Supplier;

/**
 * 事务边界抽象（生产实现 = Spring {@code @Transactional} / 同库事务）。
 *
 * <p>S1a v0.4 §6.3：<b>资金变更必须落在同一本地事务</b> + 乐观锁 {@code version} + 唯一
 * {@code biz_no}；<b>禁止跨库/跨服务事务</b>。领域服务只依赖本接口，便于以内存实现做原子性验证。
 */
public interface UnitOfWork {

    /** 在事务内执行；抛异常则整体回滚（资金链路必须全有或全无）。 */
    <T> T execute(Supplier<T> work);

    default void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
