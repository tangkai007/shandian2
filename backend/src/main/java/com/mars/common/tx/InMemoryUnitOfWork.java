package com.mars.common.tx;

import java.util.List;
import java.util.function.Supplier;

/**
 * 内存事务管理器：进入时对全部参与者取快照，异常时整体回滚。
 *
 * <p>语义与 {@code @Transactional} 一致（同库、单事务、异常即回滚），用于在无 MySQL 的环境下
 * <b>真实验证</b>资金链路「全有或全无」——例如余额不足时不得留下任何流水或收益记录。
 */
public final class InMemoryUnitOfWork implements UnitOfWork {

    private final List<Snapshotable> participants;

    public InMemoryUnitOfWork(Snapshotable... participants) {
        this.participants = List.of(participants);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        Object[] snapshots = new Object[participants.size()];
        for (int i = 0; i < participants.size(); i++) {
            snapshots[i] = participants.get(i).snapshot();
        }
        try {
            return work.get();
        } catch (RuntimeException ex) {
            for (int i = 0; i < participants.size(); i++) {
                participants.get(i).restore(snapshots[i]);
            }
            throw ex;
        }
    }
}
