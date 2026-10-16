package com.mars.interaction.web;

import com.mars.interaction.DanmakuPolicy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 每直播间一个 {@link DanmakuPolicy} 实例（限流 1 条/秒、5s 去重需要按房间/用户记忆状态）。
 *
 * <p>单机内存实现；多实例部署时须把窗口状态外移到 Redis（契约 §2.8），本类仅作为装配位。
 */
@Component
public class RoomDanmakuPolicies {

    private final ConcurrentMap<Long, DanmakuPolicy> byRoom = new ConcurrentHashMap<>();

    public DanmakuPolicy forRoom(long roomId) {
        return byRoom.computeIfAbsent(roomId, key -> new DanmakuPolicy());
    }
}
