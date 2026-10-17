package com.mars.moderation;

/**
 * 审核处置动作（契约 §4.9 API-ADM-08 + S1b D-ADM-002 处置区）。
 *
 * <p>契约 §4.9 列 {@code WARN|CUT|BAN|REPORT}；UI 处置区另有 {@code MARK_OK}（标记无风险，
 * 仅关单、<b>不产生处置留痕</b>）。差异以 S1b 定稿为准，登记于实现说明。
 *
 * <ul>
 *   <li>{@code WARN} 警告主播 —— 不中断直播，留痕；</li>
 *   <li>{@code CUT} 断流 —— <b>不可逆</b>，留痕；</li>
 *   <li>{@code BAN} 封禁账号 —— <b>不可逆</b>，留痕；</li>
 *   <li>{@code REPORT} 上报监管 —— 生成报送记录 + 留痕；</li>
 *   <li>{@code MARK_OK} 标记无风险 —— 关单，<b>不留痕</b>。</li>
 * </ul>
 */
public enum ModerationActionType {

    WARN(false, true),
    CUT(true, true),
    BAN(true, true),
    REPORT(false, true),
    MARK_OK(false, false);

    private final boolean irreversible;
    private final boolean disposition;

    ModerationActionType(boolean irreversible, boolean disposition) {
        this.irreversible = irreversible;
        this.disposition = disposition;
    }

    /** 不可逆（高危，UI 需二次确认 + 明示后果）。 */
    public boolean irreversible() {
        return irreversible;
    }

    /** 是否为「处置」动作（{@code MARK_OK} 不是 —— 不写 {@code moderation_action}/证据）。 */
    public boolean disposition() {
        return disposition;
    }

    /** 是否必填原因码（编码进 <b>动作语义</b>，不在前端硬编码）。 */
    public boolean requiresReasonCode() {
        return disposition;
    }

    /** 解析（非处置动作或未知 → {@code null}，由调用方按 {@code 40001} 处理）。 */
    public static ModerationActionType fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
