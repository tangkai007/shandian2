package com.mars.common.support;

/**
 * 业务单号生成器（生产实现 = 雪花 ID / 号段；测试注入确定性序列，保证可复算）。
 */
@FunctionalInterface
public interface OrderNoGenerator {

    String next(String bizType);
}
