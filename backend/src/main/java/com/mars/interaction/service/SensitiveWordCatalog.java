package com.mars.interaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词目录：持有可重建的前缀树，按固定间隔从各来源刷新。
 *
 * <p>刷新而非每次查库：匹配是热路径（每条弹幕都要过），必须走内存；
 * 改词库的生效延迟 = {@code mars.interaction.sensitive-word.reload-seconds}（默认 300s，可配）。
 */
@Component
public class SensitiveWordCatalog {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordCatalog.class);

    private final SensitiveWordSource source;
    private final long reloadSeconds;

    private volatile SensitiveWordFilter filter;
    private volatile Instant loadedAt = Instant.EPOCH;

    public SensitiveWordCatalog(SensitiveWordSource source,
                                @Value("${mars.interaction.sensitive-word.reload-seconds:300}") long reloadSeconds) {
        this.source = source;
        this.reloadSeconds = Math.max(1L, reloadSeconds);
    }

    /** 取得当前过滤器（必要时重建）。 */
    public SensitiveWordFilter filter() {
        SensitiveWordFilter current = filter;
        if (current == null || Instant.now().isAfter(loadedAt.plusSeconds(reloadSeconds))) {
            return rebuild();
        }
        return current;
    }

    /** 强制重建（运营改词后可主动调用，无需等 TTL）。 */
    public synchronized SensitiveWordFilter rebuild() {
        List<String> words = new ArrayList<>(source.load());
        SensitiveWordFilter rebuilt = new SensitiveWordTrie(words);
        this.filter = rebuilt;
        this.loadedAt = Instant.now();
        log.info("sensitive word catalog rebuilt: {} entries", rebuilt.size());
        return rebuilt;
    }
}
