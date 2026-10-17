package com.mars.moderation.repo;

import java.util.Optional;

/**
 * 操作者昵称查询端口（闭合实现说明「已知限制 9」：审计 {@code operator_name} 的昵称来源）。
 *
 * <p>由 {@code user} 表按用户 ID 解析昵称；缺失时调用方回落为用户 ID 字符串。
 * 定义在审核域内，使 {@code AuditRecorder} 不依赖后台模块（避免环形依赖）。
 */
public interface OperatorLookup {

    /** @return 该用户的昵称；用户不存在或昵称为空时返回 {@link Optional#empty()}。 */
    Optional<String> nicknameOf(long userId);
}
