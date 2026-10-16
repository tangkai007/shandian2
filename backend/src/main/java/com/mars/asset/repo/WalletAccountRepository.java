package com.mars.asset.repo;

import com.mars.asset.ledger.WalletAccount;

import java.util.Optional;

/** 钱包账户仓储（生产实现 = MyBatis Mapper + 乐观锁 {@code WHERE version = ?}）。 */
public interface WalletAccountRepository {

    Optional<WalletAccount> find(long userId);

    /** 落库；{@code expectedVersion} 不匹配即乐观锁失败（契约 {@code 40900}）。 */
    void save(WalletAccount account, long expectedVersion);
}
