package com.mars.moderation.repo;

import com.mars.moderation.SysConfigEntry;

import java.util.List;
import java.util.Optional;

/**
 * 平台口径配置仓储（表 {@code sys_config}，契约 §4.9 API-ADM-20）。
 *
 * <p><b>改口径不发版</b>：读侧每次取值即时读取，运营改配置后立即生效。
 *
 * <p>结构上只持久化「键 → 覆盖值 + 备注」；口径的<b>类型 / 标签 / 说明由
 * {@code SysConfigCatalog} 目录统一提供</b>，故 {@link #put} 不再重复落这几列。
 */
public interface SysConfigRepository {

    /** 按存储键取覆盖值（无覆盖 → {@code empty}，由调用方回落到目录默认值）。 */
    Optional<String> get(String key);

    /** 幂等写入（存在即更新）；{@code description} 供人工查库辨识。 */
    void put(String key, String value, String description);

    /** 全量口径行（供后台列表一次性读取，避免逐键 N 次查询）。 */
    List<SysConfigEntry> list();
}
