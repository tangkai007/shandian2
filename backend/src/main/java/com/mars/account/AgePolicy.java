package com.mars.account;

import java.time.LocalDate;

/**
 * 年龄判定（S1a v0.4：`is_minor` 由出生日期推导；OP-6 实名年龄判断）。
 */
public final class AgePolicy {

    /** 成年年龄门槛。 */
    public static final int ADULT_AGE = 18;

    private AgePolicy() {
    }

    /**
     * @param birthDate 实名返回的出生日期
     * @param asOf      判定基准日（UTC+8 自然日）
     * @return 未满 {@link #ADULT_AGE} 周岁为 {@code true}
     */
    public static boolean isMinor(LocalDate birthDate, LocalDate asOf) {
        if (birthDate == null) {
            throw new IllegalArgumentException("birthDate is required");
        }
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required");
        }
        return birthDate.plusYears(ADULT_AGE).isAfter(asOf);
    }
}
