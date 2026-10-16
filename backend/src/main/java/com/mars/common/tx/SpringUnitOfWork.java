package com.mars.common.tx;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 基于 Spring 事务管理器的 {@link UnitOfWork}（生产实现）。
 *
 * <p>所有资金写入落在<b>同一本地事务</b>（默认传播 REQUIRED）；抛出运行时异常即回滚，
 * 与内存实现 {@code InMemoryUnitOfWork} 的语义一致 —— 领域层对两者无感。
 */
public final class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate template;

    public SpringUnitOfWork(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
