package com.mars.admin.repo;

import com.mars.admin.model.UserAccount;
import com.mars.moderation.repo.OperatorLookup;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储（表 {@code user} ⋈ {@code user_auth} ⋈ {@code live_room}），契约 §4.9 API-ADM-01/02/03。
 *
 * <p>同时实现 {@link OperatorLookup}，供审计日志解析操作者昵称（闭合「已知限制 9」）。
 */
public interface UserAccountRepository extends OperatorLookup {

    /** 关键字命中昵称 / 手机号哈希 / 用户 ID；{@code null} 或空串取全部（按注册时间倒序）。 */
    List<UserAccount> search(String keyword);

    Optional<UserAccount> findById(long userId);

    void save(UserAccount account);

    /** 渠道首次登录建号（{@code user} 表；{@code phone_hash} 可为空）。返回自增用户 ID。 */
    long insertUser(String nickname, String phoneHash);
}
