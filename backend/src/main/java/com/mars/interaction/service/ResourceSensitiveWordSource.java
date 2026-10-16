package com.mars.interaction.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置资源词库（classpath {@code sensitive-words.txt}，每行一词，{@code #} 开头为注释）。
 *
 * <p><b>说明</b>：内置词条仅为<b>基线占位</b>，用于打通「命中 → 拒绝 + 反馈」链路；
 * 线上合规词库必须由运营/供应商词库通过 {@link SensitiveWordSource} 扩展注入（无需改代码）。
 */
public class ResourceSensitiveWordSource implements SensitiveWordSource {

    private static final Logger log = LoggerFactory.getLogger(ResourceSensitiveWordSource.class);
    private static final String DEFAULT_RESOURCE = "sensitive-words.txt";

    private final String resourcePath;

    public ResourceSensitiveWordSource() {
        this(DEFAULT_RESOURCE);
    }

    public ResourceSensitiveWordSource(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public Collection<String> load() {
        List<String> words = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.warn("sensitive word resource not found: {}", resourcePath);
                return words;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim();
                    if (!word.isEmpty() && !word.startsWith("#")) {
                        words.add(word);
                    }
                }
            }
        } catch (IOException ex) {
            log.error("failed to load sensitive words from {}", resourcePath, ex);
        }
        return words;
    }
}
