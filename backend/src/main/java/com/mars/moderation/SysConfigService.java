package com.mars.moderation;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.tx.UnitOfWork;
import com.mars.moderation.repo.SysConfigRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台口径配置服务（契约 §4.9 API-ADM-20）。
 *
 * <p><b>改口径不发版</b>：读侧每次即时读取 {@code sys_config}；后台改配置后下一次请求立即生效。
 * <b>键名对齐</b>：对外用点分 API 键（{@code appeal.window_days}），落库用下划线存储键
 * （{@code appeal_days}）—— 映射由 {@link SysConfigCatalog} 统一裁决，避免「两套键名」散落各处。
 */
public final class SysConfigService {

    private final SysConfigRepository config;
    private final UnitOfWork unitOfWork;
    private final AuditRecorder audit;

    public SysConfigService(SysConfigRepository config, UnitOfWork unitOfWork, AuditRecorder audit) {
        this.config = config;
        this.unitOfWork = unitOfWork;
        this.audit = audit;
    }

    /**
     * 全量口径列表（API-ADM-20 GET）。
     *
     * <p>以目录为骨架保证<b>条目齐全、顺序稳定</b>（前端表单不因 DB 缺行而缺项）；DB 有值则以其为准，
     * 无值回落到目录默认值。目录即后台可管理口径的<b>权威面</b>（与 S2a mock 的 17 项一一对应）；
     * 目录之外的存量键不在此列出，避免后台表单出现预期外的行。
     */
    public List<SysConfigEntry> list() {
        Map<String, SysConfigEntry> stored = new LinkedHashMap<>();
        for (SysConfigEntry entry : config.list()) {
            stored.put(entry.key(), entry);
        }
        List<SysConfigEntry> result = new ArrayList<>();
        for (SysConfigCatalog.Meta meta : SysConfigCatalog.all()) {
            SysConfigEntry row = stored.get(meta.storageKey());
            String value = firstNonBlank(row == null ? null : row.value(), meta.defaultValue());
            result.add(new SysConfigEntry(meta.apiKey(), meta.label(), meta.valueType(), value, meta.note()));
        }
        return result;
    }

    /**
     * 修改一条口径（API-ADM-20 PUT）—— 键必须是目录内已知口径，否则 {@code 40330}。
     *
     * <p>写入后返回刷新后的全量列表（与 S2a mock 的 PUT 响应一致）；审计动作 {@code SYS_CONFIG_UPDATE}。
     */
    public List<SysConfigEntry> update(String apiKey, String value, long operatorId, String operatorName,
                                       String operatorRole, String ip) {
        SysConfigCatalog.Meta meta = SysConfigCatalog.byApiKey(apiKey)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_GONE, "未知口径键", "key", "not_found"));
        String normalized = value == null ? "" : value.trim();
        unitOfWork.execute(() -> {
            config.put(meta.storageKey(), normalized, meta.label());
            audit.record(operatorId, operatorName, operatorRole,
                    AuditRecorder.ACTION_SYS_CONFIG_UPDATE, "SYS_CONFIG", meta.apiKey(),
                    meta.apiKey(), null, null, null, ip);
        });
        return list();
    }

    /** 单值读取（供其它服务按存储键取值）。 */
    public Optional<String> value(String storageKey) {
        return config.get(storageKey);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }
}
