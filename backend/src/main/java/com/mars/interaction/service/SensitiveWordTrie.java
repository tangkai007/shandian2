package com.mars.interaction.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 敏感词过滤器（<b>零依赖 Aho–Corasick 风格前缀树</b>实现）。
 *
 * <p>归一化：统一小写并**剔除所有非字母/数字字符**（含空格、标点、常见全角符号），
 * 因此「敏　感 词」「敏-感-词」与「敏感词」等价 —— 这是应对绕过的基本手段。
 * CJK 汉字在 {@link Character#isLetterOrDigit(char)} 下为 letter，故不被剔除。
 *
 * <p>复杂度：构建 O(Σ|词|)，匹配 O(|文本| × maxWordLen)。
 */
public final class SensitiveWordTrie implements SensitiveWordFilter {

    private static final class Node {
        private final Map<Character, Node> next = new HashMap<>();
        private boolean terminal;
    }

    private final Node root = new Node();
    private int maxLength;

    public SensitiveWordTrie(Collection<String> words) {
        if (words != null) {
            for (String word : words) {
                String normalized = normalize(word);
                if (!normalized.isEmpty()) {
                    insert(normalized);
                    maxLength = Math.max(maxLength, normalized.length());
                }
            }
        }
    }

    private void insert(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.next.computeIfAbsent(word.charAt(i), key -> new Node());
        }
        node.terminal = true;
    }

    @Override
    public Optional<String> firstHit(String text) {
        if (text == null || text.isEmpty() || maxLength == 0) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        for (int start = 0; start < normalized.length(); start++) {
            Node node = root;
            int limit = Math.min(normalized.length(), start + maxLength);
            for (int index = start; index < limit; index++) {
                node = node.next.get(normalized.charAt(index));
                if (node == null) {
                    break;
                }
                if (node.terminal) {
                    return Optional.of(normalized.substring(start, index + 1));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int size() {
        return count(root);
    }

    private int count(Node node) {
        int total = node.terminal ? 1 : 0;
        for (Node child : node.next.values()) {
            total += count(child);
        }
        return total;
    }

    /** 归一化：小写 + 仅保留字母/数字。 */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
