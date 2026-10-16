package com.mars.interaction.service;

import com.mars.interaction.DanmakuDecision;
import com.mars.interaction.DanmakuRequest;
import com.mars.interaction.repo.RoomMuteRepository;
import com.mars.interaction.web.RoomDanmakuPolicies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * 弹幕守卫：把<b>基础设施事实</b>（是否被禁言、是否命中敏感词、是否已绑手机号）汇成
 * {@link DanmakuRequest}，再交给纯业务规则 {@code DanmakuPolicy} 判定。
 *
 * <p>分层理由：限流/去重/门槛是**业务规则**（可纯单测），敏感词与禁言是**外部事实**（需查词库/DB）；
 * 两者分离后，规则层保持零依赖，事实层可独立替换（词库换供应商、禁言换存储）。
 *
 * <p>留痕：命中敏感词时只记录**归一化后的命中词**，<b>不记录原始弹幕全文</b>（避免在日志里二次扩散违规内容）。
 */
@Component
public class DanmakuGuard {

    private static final Logger log = LoggerFactory.getLogger(DanmakuGuard.class);

    private final SensitiveWordCatalog sensitiveWords;
    private final RoomMuteRepository roomMutes;
    private final RoomDanmakuPolicies policies;
    private final Clock clock;

    public DanmakuGuard(SensitiveWordCatalog sensitiveWords,
                        RoomMuteRepository roomMutes,
                        RoomDanmakuPolicies policies,
                        Clock clock) {
        this.sensitiveWords = sensitiveWords;
        this.roomMutes = roomMutes;
        this.policies = policies;
        this.clock = clock;
    }

    public DanmakuDecision check(long roomId, long userId, boolean phoneBound, String content) {
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        boolean muted = roomMutes.isMuted(roomId, userId, nowUtc);
        Optional<String> hit = sensitiveWords.filter().firstHit(content);

        DanmakuRequest request = new DanmakuRequest(
                String.valueOf(userId), content, phoneBound, muted, hit.isPresent(), clock.millis());
        DanmakuDecision decision = policies.forRoom(roomId).check(request);

        if (decision.outcome() == DanmakuDecision.Outcome.REJECTED && hit.isPresent()) {
            log.info("danmaku rejected by sensitive word: roomId={} userId={} word={}",
                    roomId, userId, hit.get());
        }
        return decision;
    }
}
