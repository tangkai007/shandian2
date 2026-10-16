package com.mars.interaction.repo;

import java.time.LocalDateTime;

/**
 * 房间禁言查询（表 {@code room_mute}，BR-7：房管仅可管理本直播间）。
 */
public interface RoomMuteRepository {

    /**
     * @param nowUtc 当前时间（UTC，与 {@code room_mute.expire_at} 同口径）
     * @return 存在未过期禁言记录则为 {@code true}（{@code expire_at IS NULL} 视为永久禁言）
     */
    boolean isMuted(long roomId, long userId, LocalDateTime nowUtc);
}
