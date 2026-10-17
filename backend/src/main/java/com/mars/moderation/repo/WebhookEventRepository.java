package com.mars.moderation.repo;

/**
 * 回调事件幂等仓储（表 {@code webhook_event}，§4.10）。
 *
 * <p>{@link #register} 以主键唯一约束实现「先落库后处理」：返回 {@code true} 表示首次收到，
 * {@code false} 表示重复投递（调用方应直接返回成功、不重复处理）。
 */
public interface WebhookEventRepository {

    boolean register(String eventId, String source);
}
