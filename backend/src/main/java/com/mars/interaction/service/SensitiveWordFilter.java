package com.mars.interaction.service;

import java.util.Optional;

/**
 * 敏感词过滤（契约 §4.5 API-INT-02 · <b>AC-4</b>：含敏感词的弹幕不落公屏且有明确反馈）。
 *
 * <p>实现须对「加空格 / 改大小写 / 插符号」等常见绕过做归一化。
 */
public interface SensitiveWordFilter {

    /**
     * @return 命中的词（归一化形式，仅用于服务端留痕/日志，不外泄给客户端）；未命中返回 {@link Optional#empty()}
     */
    Optional<String> firstHit(String text);

    default boolean hit(String text) {
        return firstHit(text).isPresent();
    }

    /** 词条数量（用于自检与运营观测）。 */
    int size();
}
