package com.mars.moderation.mapper;

import com.mars.moderation.SysConfigEntry;
import com.mars.moderation.repo.SysConfigRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 平台口径配置 Mapper（表 {@code sys_config}，契约 §4.9 API-ADM-20）。
 *
 * <p>表只存 {@code config_key / config_value / description}；口径的类型 / 标签 / 说明由
 * {@code SysConfigCatalog} 目录提供，故 {@link #list()} 取「键 → 覆盖值」中间行再交目录补齐。
 * {@code key} 在 MySQL 为保留字，一律以反引号或别名规避。
 */
@Mapper
public interface SysConfigMapper extends SysConfigRepository {

    /** 读路径中间行（真实列名 {@code config_key} 是保留字，别名化为 {@code configKey}）。 */
    record ConfigRow(String configKey, String configValue, String description) {
    }

    @Override
    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key}")
    Optional<String> get(String key);

    @Insert("INSERT INTO sys_config (config_key, config_value, description) "
            + "VALUES (#{key}, #{value}, #{description}) "
            + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), "
            + "description = VALUES(description)")
    int upsert(@Param("key") String key,
               @Param("value") String value,
               @Param("description") String description);

    @Override
    default void put(String key, String value, String description) {
        upsert(key, value, description);
    }

    /** 内部查询：全量中间行（非端口方法，故无 {@code @Override}）。 */
    @Select("SELECT config_key AS configKey, config_value AS configValue, "
            + "description AS description FROM sys_config")
    List<ConfigRow> selectAll();

    @Override
    default List<SysConfigEntry> list() {
        return selectAll().stream()
                .map(row -> new SysConfigEntry(row.configKey(), null, null, row.configValue(),
                        row.description()))
                .toList();
    }
}
