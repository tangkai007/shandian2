package com.mars.common.tx;

/**
 * 可快照 / 可回滚的存储（内存实现用于无 DB 环境下验证「全有或全无」）。
 */
public interface Snapshotable {

    /** 取得当前状态快照（与后续写入无关）。 */
    Object snapshot();

    /** 回滚到快照。 */
    void restore(Object snapshot);
}
