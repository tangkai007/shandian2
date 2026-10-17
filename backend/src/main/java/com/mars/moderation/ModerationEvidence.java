package com.mars.moderation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处置证据快照（契约 §4.9 API-ADM-09 · 不可删除，AC-7）。
 *
 * <p>与处置一一对应（{@code action_id} 唯一）；证据 URL 存 COS。
 */
public record ModerationEvidence(
        String evidenceId,
        String actionId,
        LocalDateTime snapshotAt,
        String ruleId,
        int confidence,
        String operatorName,
        List<String> evidenceUrls) {

    public ModerationEvidence {
        evidenceUrls = evidenceUrls == null ? List.of() : List.copyOf(evidenceUrls);
    }
}
