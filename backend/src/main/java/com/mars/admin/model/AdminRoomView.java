package com.mars.admin.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台房间行（契约 §4.9 API-ADM-04 · 前端 {@code AdminRoomRow}）。
 *
 * <p>{@code lockedBy} / {@code lockedByName}：该房间当前被哪位审核员「任务级认领」持有
 * （取自 {@code moderation_task} 处于 CLAIMED 的记录）；无认领则为 {@code null}。
 * 与前端 S2a mock 的「房间级锁」语义对齐 —— Leader 已确认聚合到任务级。
 *
 * @param viewerCnt 当前在线人数（披露自场次峰值，无运行时来源时取 0）
 * @param riskScore 风险分（取该房间未关闭审核任务的最高置信度，无则 0）
 * @param alerts    命中告警（按置信度倒序）
 */
public record AdminRoomView(
        long roomId,
        Long roomNo,
        String anchorName,
        String categoryName,
        String status,
        int viewerCnt,
        int riskScore,
        Long lockedBy,
        String lockedByName,
        List<Alert> alerts) {

    public AdminRoomView {
        alerts = alerts == null ? List.of() : List.copyOf(alerts);
    }

    /** 房间告警条目（前端 {@code ModerationAlert}）。 */
    public record Alert(
            String alertId,
            String type,
            String summary,
            int confidence,
            String ruleId,
            LocalDateTime occurredAt,
            String snapshotUrl) {
    }
}
