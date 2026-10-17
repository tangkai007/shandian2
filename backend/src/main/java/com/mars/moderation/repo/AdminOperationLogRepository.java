package com.mars.moderation.repo;

import com.mars.moderation.AdminOperationLog;

import java.util.List;

/** 后台操作审计仓储（表 {@code admin_operation_log}，仅追加）。 */
public interface AdminOperationLogRepository {

    void append(AdminOperationLog log);

    /** 审计列表：按时间倒序；{@code keyword} 为可空模糊过滤（操作者 / 动作 / 对象）。 */
    List<AdminOperationLog> list(String keyword);
}
