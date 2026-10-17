package com.mars.admin.service;

import com.mars.admin.model.AdminRoomView;
import com.mars.admin.repo.AdminRoomRepository;
import com.mars.moderation.ModerationTask;
import com.mars.moderation.TaskStatus;
import com.mars.moderation.repo.ModerationTaskRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台房间列表（契约 §4.9 API-ADM-04 · 前端 {@code AdminRoomRow}）。
 *
 * <p>「房间级锁」聚合为「任务级认领」：{@code locked_by} 取该房间处于 {@code CLAIMED} 的
 * 审核任务的认领人（Leader 已确认口径）。{@code risk_score} 取未关闭任务的最高置信度；
 * {@code alerts} 为未关闭任务（按置信度倒序）。
 */
public final class AdminRoomService {

    private final AdminRoomRepository rooms;
    private final ModerationTaskRepository tasks;

    public AdminRoomService(AdminRoomRepository rooms, ModerationTaskRepository tasks) {
        this.rooms = rooms;
        this.tasks = tasks;
    }

    public List<AdminRoomView> list() {
        List<ModerationTask> all = tasks.listByStatus(null);
        Map<Long, List<ModerationTask>> byRoom = new LinkedHashMap<>();
        for (ModerationTask task : all) {
            if (task.roomId() != null) {
                byRoom.computeIfAbsent(task.roomId(), k -> new ArrayList<>()).add(task);
            }
        }

        List<AdminRoomView> views = new ArrayList<>();
        for (AdminRoomRepository.RoomRow room : rooms.listRooms()) {
            List<ModerationTask> roomTasks = byRoom.getOrDefault(room.roomId(), List.of());
            Long lockedBy = null;
            String lockedByName = null;
            int riskScore = 0;
            List<AdminRoomView.Alert> alerts = new ArrayList<>();
            for (ModerationTask task : roomTasks) {
                if (task.status() == TaskStatus.DONE) {
                    continue;
                }
                riskScore = Math.max(riskScore, task.confidence());
                alerts.add(new AdminRoomView.Alert(task.taskId(), task.alertType().name(),
                        task.reasonSummary(), task.confidence(), task.ruleId(), task.createdAt(),
                        task.snapshotUrl()));
                if (lockedBy == null && task.status() == TaskStatus.CLAIMED) {
                    lockedBy = task.claimedBy();
                    lockedByName = task.claimedByName();
                }
            }
            alerts.sort(Comparator.comparingInt(AdminRoomView.Alert::confidence).reversed());
            views.add(new AdminRoomView(room.roomId(), room.roomNo(), room.anchorName(),
                    room.categoryName(), room.status(), 0, riskScore, lockedBy, lockedByName, alerts));
        }
        return views;
    }
}
