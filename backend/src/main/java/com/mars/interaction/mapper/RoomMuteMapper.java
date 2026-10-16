package com.mars.interaction.mapper;

import com.mars.interaction.repo.RoomMuteRepository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 禁言查询 Mapper（表 {@code room_mute}）。 */
@Mapper
public interface RoomMuteMapper extends RoomMuteRepository {

    @Select("SELECT COUNT(*) FROM room_mute WHERE room_id = #{roomId} AND user_id = #{userId} "
            + "AND (expire_at IS NULL OR expire_at > #{now})")
    long countActiveMute(@Param("roomId") long roomId,
                         @Param("userId") long userId,
                         @Param("now") LocalDateTime now);

    @Override
    default boolean isMuted(long roomId, long userId, LocalDateTime nowUtc) {
        return countActiveMute(roomId, userId, nowUtc) > 0;
    }
}
