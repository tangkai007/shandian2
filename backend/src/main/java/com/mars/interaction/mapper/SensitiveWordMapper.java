package com.mars.interaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 运营可配置敏感词 Mapper（表 {@code sensitive_word}）。
 *
 * <p>与内置资源词库一起经 {@code CompositeSensitiveWordSource} 合并后进前缀树；
 * 运营改词库无需发版（词表按 {@code mars.interaction.sensitive-word.reload-seconds} 定期重建）。
 */
@Mapper
public interface SensitiveWordMapper {

    @Select("SELECT word FROM sensitive_word WHERE status = 'ON' ORDER BY id")
    List<String> words();
}
