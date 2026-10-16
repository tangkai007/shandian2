package com.mars.asset.mapper;

import com.mars.asset.ledger.WalletAccount;
import com.mars.asset.repo.WalletAccountRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 钱包账户 Mapper（表 {@code wallet_account}）。
 *
 * <p>乐观锁：{@code UPDATE ... WHERE user_id = ? AND version = ?}；影响行数为 0 且插入失败即版本冲突
 * （契约 {@code 40900}）。
 */
@Mapper
public interface WalletAccountMapper extends WalletAccountRepository {

    @Override
    @Select("SELECT user_id, balance, version, frozen FROM wallet_account WHERE user_id = #{userId}")
    Optional<WalletAccount> find(long userId);

    @Update("UPDATE wallet_account SET balance = #{account.balance}, version = #{account.version}, "
            + "frozen = #{account.frozen} WHERE user_id = #{account.userId} AND version = #{expectedVersion}")
    int updateByVersion(@Param("account") WalletAccount account, @Param("expectedVersion") long expectedVersion);

    @Insert("INSERT INTO wallet_account (user_id, balance, version, frozen) "
            + "VALUES (#{userId}, #{balance}, #{version}, #{frozen})")
    int insert(WalletAccount account);

    @Override
    default void save(WalletAccount account, long expectedVersion) {
        if (updateByVersion(account, expectedVersion) == 1) {
            return;
        }
        try {
            if (insert(account) == 1) {
                return;
            }
        } catch (RuntimeException duplicate) {
            throw new BizException(ErrorCode.CONFLICT, "钱包并发写入冲突", "version", "stale");
        }
        throw new BizException(ErrorCode.CONFLICT, "钱包版本冲突", "version", "stale");
    }
}
