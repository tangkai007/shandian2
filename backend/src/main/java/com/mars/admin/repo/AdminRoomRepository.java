package com.mars.admin.repo;

import java.util.List;

/**
 * 后台房间查询端口（表 {@code live_room} ⋈ {@code user} ⋈ {@code live_category}），契约 §4.9 API-ADM-04。
 *
 * <p>只负责「房间基本行」；锁（{@code locked_by}）与告警（{@code alerts}）由
 * {@code AdminRoomService} 汇总 {@code moderation_task} 得到。
 */
public interface AdminRoomRepository {

    /** 全部房间（按房号升序）。 */
    List<RoomRow> listRooms();

    /** 房间基本行。 */
    record RoomRow(long roomId, Long roomNo, String anchorName, String categoryName, String status) {
    }
}
