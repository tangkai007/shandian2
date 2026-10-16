package com.mars.interaction.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 多源合并：把资源词库、运营配置、第三方词库统一成一份词表。 */
public final class CompositeSensitiveWordSource implements SensitiveWordSource {

    private final List<SensitiveWordSource> sources;

    public CompositeSensitiveWordSource(List<SensitiveWordSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public Collection<String> load() {
        List<String> merged = new ArrayList<>();
        for (SensitiveWordSource source : sources) {
            Collection<String> words = source.load();
            if (words != null) {
                merged.addAll(words);
            }
        }
        return merged;
    }
}
