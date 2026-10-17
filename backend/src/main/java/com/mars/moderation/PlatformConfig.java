package com.mars.moderation;

import com.mars.moderation.repo.SysConfigRepository;

import java.util.Optional;

/**
 * 平台口径读取端口（契约 §4.9 API-ADM-20 · S1b §11.3）。
 *
 * <p><b>唯一事实来源是 {@code sys_config} 表</b>；构造参数里的默认值只用于「配置缺失」时的兜底
 * （由 Spring 从 {@code application.yml} 注入，<b>不在领域核心写死</b>）。因此：
 * 「申诉时限 = 7 天」不是代码常量 —— 运营在后台把 {@code appeal_days} 改成任意值即立刻生效。
 */
public final class PlatformConfig {

    /** 申诉时限（天）。S1b §11.3 口径键。 */
    public static final String KEY_APPEAL_DAYS = "appeal_days";
    /** 回放留存（天）。 */
    public static final String KEY_REPLAY_RETENTION_DAYS = "replay_retention_days";
    /** 空闲断流阈值（分钟，OP-18）。 */
    public static final String KEY_IDLE_STREAM_MINUTES = "idle_stream_minutes";

    private final SysConfigRepository config;
    private final int appealDaysFallback;
    private final int replayRetentionDaysFallback;

    public PlatformConfig(SysConfigRepository config,
                          int appealDaysFallback,
                          int replayRetentionDaysFallback) {
        this.config = config;
        this.appealDaysFallback = appealDaysFallback;
        this.replayRetentionDaysFallback = replayRetentionDaysFallback;
    }

    /** 申诉时限（天）：先读 {@code sys_config.appeal_days}，缺失才用兜底。 */
    public int appealDays() {
        return intValue(KEY_APPEAL_DAYS, appealDaysFallback);
    }

    /** 回放留存（天）：先读 {@code sys_config.replay_retention_days}，缺失才用兜底。 */
    public int replayRetentionDays() {
        return intValue(KEY_REPLAY_RETENTION_DAYS, replayRetentionDaysFallback);
    }

    public int intValue(String key, int fallback) {
        return config.get(key).flatMap(PlatformConfig::parseInt).orElse(fallback);
    }

    public String stringValue(String key, String fallback) {
        return config.get(key).filter(v -> !v.isBlank()).orElse(fallback);
    }

    public boolean boolValue(String key, boolean fallback) {
        return config.get(key).map(v -> "true".equalsIgnoreCase(v.trim())
                || "1".equals(v.trim())
                || "on".equalsIgnoreCase(v.trim())).orElse(fallback);
    }

    private static Optional<Integer> parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
