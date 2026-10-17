package com.mars.moderation.mapper;

import com.mars.moderation.repo.WebhookEventRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 回调事件幂等 Mapper（表 {@code webhook_event}，§4.10）。
 *
 * <p>{@code INSERT IGNORE} + 主键唯一约束实现「先落库后处理」：影响行数 1 → 首次收到，
 * 0 → 重复投递（调用方直接返回成功、不重复处理）。
 */
@Mapper
public interface WebhookEventMapper extends WebhookEventRepository {

    @Insert("INSERT IGNORE INTO webhook_event (event_id, source) VALUES (#{eventId}, #{source})")
    int insertIgnore(@Param("eventId") String eventId, @Param("source") String source);

    @Override
    default boolean register(String eventId, String source) {
        return insertIgnore(eventId, source) > 0;
    }
}
