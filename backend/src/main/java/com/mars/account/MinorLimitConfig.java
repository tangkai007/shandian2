package com.mars.account;

/**
 * 未成年打赏限额配置（表 {@code minor_limit_config}，OP-6 / BR-5 / OP-21）。
 *
 * @param singleLimitCoin   单笔限额（币）
 * @param dailyLimitCoin    单日累计限额（币）
 * @param requiresRealname  OP-21 开关 {@code minor.limit_requires_realname}：打赏/充值前是否要求
 *                          已完成实名或年龄声明；默认 {@code true}
 */
public record MinorLimitConfig(long singleLimitCoin, long dailyLimitCoin, boolean requiresRealname) {

    public MinorLimitConfig {
        if (singleLimitCoin < 0L || dailyLimitCoin < 0L) {
            throw new IllegalArgumentException("limits must be non-negative");
        }
    }
}
