package com.mars.interaction.service;

import java.util.Collection;

/**
 * 敏感词来源（可多源合并：内置资源包 + 数据库/运营配置 + 后续可接第三方词库）。
 */
public interface SensitiveWordSource {

    Collection<String> load();
}
