package com.mars.moderation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 平台口径目录（S2a §4.9 API-ADM-20 覆盖 §11.3 全部口径）。
 *
 * <p>后台 UI 用的是<b>点分 API 键</b>（{@code appeal.window_days} 等，S1b D-ADM-000 冻结），
 * 而 {@code sys_config} 的种子键是下划线式（{@code appeal_days}）。本目录把二者对齐：
 * 对外一律用 API 键，落库用 {@code storageKey}。
 *
 * <p><b>别名只在「单位与格式都一致」时才建立</b>：例如 {@code appeal.window_days ↔ appeal_days}
 * （均为整数天）可以直连；而分成比 / 税率 / 结算周期等财务口径，V1 种子存的是小数或英文枚举
 * （{@code 0.5} / {@code MONTHLY}），与后台以百分比 / JSON 列表呈现的口径<b>单位不同</b>，
 * 若强连会把 {@code 0.5} 当 {@code 50%} 显示。故这类键暂以 API 键自身落库（不别名），
 * 待「财务后台 ADM-21…28」增量按正确单位接管其持久化。
 *
 * <p>{@code defaultValue} 为「DB 无该键」时的兜底文本；{@code list} / {@code percent} 等类型
 * 以字符串承载，读侧按 {@code valueType} 解析。
 */
public final class SysConfigCatalog {

    /** 单条口径元数据：API 键 / 落库键 / 中文标签 / 值类型 / 兜底值 / 备注。 */
    public record Meta(String apiKey,
                       String storageKey,
                       String label,
                       String valueType,
                       String defaultValue,
                       String note) {
    }

    private static final List<Meta> METAS = List.of(
            new Meta("settlement.share_rate", "settlement.share_rate",
                    "分成比（主播 50% / 平台 50%）", "percent", "50", "OP-15"),
            new Meta("settlement.platform_tax_rate", "settlement.platform_tax_rate",
                    "平台流转税率", "percent", "5", "OP-15 先扣后分"),
            new Meta("settlement.deduction_order", "settlement.deduction_order",
                    "扣减顺序", "list", "[\"渠道费\",\"平台流转税\",\"分成\",\"个税\"]", "OP-15"),
            new Meta("settlement.period", "settlement.period",
                    "结算周期", "list", "[\"月结\"]", "OP-15"),
            new Meta("withdraw.min_amount_fen", "withdraw_min_fen",
                    "起提金额", "fen", "10000", "OP-16（￥100.00）"),
            new Meta("anchor.tax_mode", "anchor.tax_mode",
                    "个税扣缴时点", "list", "[\"SETTLE\"]", "BR-6 / OP-16（SETTLE | WITHDRAW）"),
            new Meta("anchor.tax_rate", "anchor.tax_rate",
                    "主播个税率", "percent", "20", "BR-6"),
            new Meta("minor.single_limit_coin", "minor.single_limit_coin",
                    "未成年单笔打赏限额", "int", "5000", "OP-6 / BR-5"),
            new Meta("minor.daily_limit_coin", "minor.daily_limit_coin",
                    "未成年单日打赏限额", "int", "5000", "OP-6 / BR-5"),
            new Meta("category.list", "category.list",
                    "直播类目（一期 4 类）", "list", "[\"才艺\",\"游戏\",\"闲聊\",\"生活\"]", "OP-7"),
            new Meta("category.restricted", "category.restricted",
                    "受限类目黑名单", "list", "[\"电商\",\"教育\",\"医疗\",\"金融\"]", "OP-7 / BR-2"),
            new Meta("payment.cashier_channels", "payment.cashier_channels",
                    "现金支付渠道开关", "list", "[\"wechat\",\"alipay\"]", "OP-11"),
            new Meta("payment.iap_mode", "payment.iap_mode",
                    "IAP 与档位价格", "list", "[\"商品档位由 Apple 配置下发\"]", "OP-11"),
            new Meta("appeal.window_days", "appeal_days",
                    "申诉时限", "days", "7", "OP-17（BR-8）"),
            new Meta("live.idle_cutoff_minutes", "idle_stream_minutes",
                    "空闲断流阈值", "int", "30", "OP-18"),
            new Meta("live.idle_remind_minutes", "idle_remind_minutes",
                    "断流提醒时点", "int", "28", "OP-18"),
            new Meta("replay.retention_days", "replay_retention_days",
                    "回放留存", "days", "30", "OP-13"));

    private static final Map<String, Meta> BY_API_KEY = index(Meta::apiKey);
    private static final Map<String, Meta> BY_STORAGE_KEY = index(Meta::storageKey);

    public static List<Meta> all() {
        return METAS;
    }

    public static Optional<Meta> byApiKey(String apiKey) {
        return Optional.ofNullable(apiKey == null ? null : BY_API_KEY.get(apiKey.trim()));
    }

    public static Optional<Meta> byStorageKey(String storageKey) {
        return Optional.ofNullable(storageKey == null ? null : BY_STORAGE_KEY.get(storageKey.trim()));
    }

    private static Map<String, Meta> index(Function<Meta, String> keyFn) {
        Map<String, Meta> map = new LinkedHashMap<>();
        for (Meta meta : METAS) {
            map.put(keyFn.apply(meta), meta);
        }
        return Map.copyOf(map);
    }

    private SysConfigCatalog() {
    }
}
