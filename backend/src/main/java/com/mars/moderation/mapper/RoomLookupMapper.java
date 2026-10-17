package com.mars.moderation.mapper;

import com.mars.moderation.RoomBrief;
import com.mars.moderation.repo.RoomLookup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 直播间查询 Mapper（表 {@code live_room} ⋈ {@code user}）。
 *
 * <p>用于审核留痕标签「主播（房号 N）」与强制关播定位主播账号 / 房间状态。
 * {@code roomNo} 取 {@code live_room.id}（本系统房号即房间主键）；{@code anchorName} 取主播昵称。
 * {@code user} 为 MySQL 保留字，以反引号规避。
 */
@Mapper
public interface RoomLookupMapper extends RoomLookup {

    @Override
    @Select("SELECT r.id AS roomId, r.id AS roomNo, r.anchor_id AS anchorId, "
            + "u.nickname AS anchorName, r.status AS status "
            + "FROM live_room r LEFT JOIN `user` u ON u.id = r.anchor_id "
            + "WHERE r.id = #{roomId}")
    Optional<RoomBrief> findById(long roomId);
}
