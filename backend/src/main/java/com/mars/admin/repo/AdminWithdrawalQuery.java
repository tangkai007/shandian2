package com.mars.admin.repo;

import com.mars.admin.model.AdminWithdrawalView;

import java.util.List;
import java.util.Optional;

/** 财务提现行查询端口（表 {@code withdrawal} ⋈ {@code user} ⋈ {@code user_auth}），契约 §4.9 API-ADM-24/25/26。 */
public interface AdminWithdrawalQuery {

    /** 按账期筛选（{@code period} 为空取全部）。 */
    List<AdminWithdrawalView> list(String period);

    Optional<AdminWithdrawalView> find(String orderNo);
}
