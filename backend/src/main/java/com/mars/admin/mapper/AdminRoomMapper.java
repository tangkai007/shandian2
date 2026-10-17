package com.mars.admin.mapper;

import com.mars.admin.repo.AdminRoomRepository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 后台房间查询 Mapper（{@code live_room} ⋈ {@code user} ⋈ {@code live_category}）。
 *
 * <p>契约 §4.9 API-ADM-04。只取「房间基本行」；锁与告警由 {@code AdminRoomService} 另行汇总。
 */
@Mapper
public interface AdminRoomMapper extends AdminRoomRepository {

    @Override
    @Select("SELECT r.id AS roomId, r.id AS roomNo, COALESCE(u.nickname, '') AS anchorName, "
            + "COALESCE(c.name, '') AS categoryName, r.status AS status "
            + "FROM `live_room` r "
            + "LEFT JOIN `user` u ON u.id = r.anchor_id "
            + "LEFT JOIN `live_category` c ON c.id = r.category_id "
            + "ORDER BY r.id ASC")
    List<RoomRow> listRooms();
}
