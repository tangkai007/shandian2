package com.mars.moderation;

import java.util.List;

/**
 * 处置原因码字典（S2a §4.9 API-ADM-08 原因码 + 后台审核工作台下拉）。
 *
 * <p><b>服务端为唯一来源</b>：前端不硬编码原因码，统一由 {@code GET /api/v1/admin/moderation/reason-codes}
 * 拉取（返回 {@code {list, appeal_window_days}}，申诉时限随字典一起下发）。
 */
public final class ModerationReasonCodes {

    /** 原因码项（{@code code} + 展示用 {@code label}）。 */
    public record ReasonCode(String code, String label) {
    }

    private static final List<ReasonCode> CODES = List.of(
            new ReasonCode("R-2071", "R-2071 涉政敏感内容"),
            new ReasonCode("R-1102", "R-1102 涉黄内容"),
            new ReasonCode("R-1103", "R-1103 暴恐 / 违禁品"),
            new ReasonCode("R-1301", "R-1301 辱骂骚扰"),
            new ReasonCode("R-1401", "R-1401 侵权 / 盗播"),
            new ReasonCode("R-9001", "R-9001 其它违规"));

    public static List<ReasonCode> all() {
        return CODES;
    }

    private ModerationReasonCodes() {
    }
}
