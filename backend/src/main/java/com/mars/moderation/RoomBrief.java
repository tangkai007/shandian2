package com.mars.moderation;

/**
 * 直播间摘要（审核标签与强制关播定位用；由 {@code live_room} + {@code user} 联表取得）。
 *
 * <p>{@code roomNo} 取 {@code live_room.id}（本系统房号即房间主键）；{@code anchorName} 取主播昵称。
 */
public record RoomBrief(
        long roomId,
        Long roomNo,
        Long anchorId,
        String anchorName,
        String status) {

    /** {@code <主播>（房号 <room_no>）}——与 S2a §4.9 审计标签口径一致。 */
    public String targetLabel() {
        String name = (anchorName == null || anchorName.isBlank()) ? "主播" : anchorName;
        String no = roomNo == null ? String.valueOf(roomId) : String.valueOf(roomNo);
        return name + "（房号 " + no + "）";
    }
}
