package com.mars.moderation.repo;

import com.mars.moderation.RoomBrief;

import java.util.Optional;

/**
 * 直播间查询端口（表 {@code live_room} ⋈ {@code user}）。
 *
 * <p>用于审核留痕的目标标签（{@code <主播>（房号 <room_no>）}）与强制关播时定位主播账号/房间状态。
 */
public interface RoomLookup {

    Optional<RoomBrief> findById(long roomId);
}
