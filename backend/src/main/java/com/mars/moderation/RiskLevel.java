package com.mars.moderation;

/**
 * 风险等级（机审回调分流 + 审核队列排序，契约 §3.9 API-ADM-06 / §4.10）。
 *
 * <p><b>分流口径</b>：由机审置信度映射 —— ≥80 高、≥50 中、其余低；队列按 {@link #rank()}
 * 升序（高在前），同级按入队时间先到先审。前端 `HIGH/MEDIUM/LOW` 字符串即本枚举名。
 */
public enum RiskLevel {

    HIGH(0),
    MEDIUM(1),
    LOW(2);

    private final int rank;

    RiskLevel(int rank) {
        this.rank = rank;
    }

    /** 排序权重：数值越小越优先。 */
    public int rank() {
        return rank;
    }

    /** 置信度 0–100 → 风险等级（阈值是本域唯一口径，后台不可改；改口径须发版）。 */
    public static RiskLevel fromConfidence(int confidence) {
        if (confidence >= 80) {
            return HIGH;
        }
        if (confidence >= 50) {
            return MEDIUM;
        }
        return LOW;
    }

    /** 宽松解析（未知/空 → {@code LOW}，绝不因脏数据抛错阻断回调落库）。 */
    public static RiskLevel fromName(String name) {
        if (name == null || name.isBlank()) {
            return LOW;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return LOW;
        }
    }
}
