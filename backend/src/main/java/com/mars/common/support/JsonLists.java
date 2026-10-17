package com.mars.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 字符串数组 ↔ JSON 文本的小工具（用于 {@code evidence_urls} / {@code images} / 列表型口径
 * 这类以 JSON 数组落库的列）。
 *
 * <p>解析是<b>容错</b>的：空/非法内容一律返回空列表，绝不因脏数据抛错阻断读路径。
 */
public final class JsonLists {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    public static String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception impossible) {
            return "";
        }
    }

    public static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) {
            return List.of(trimmed);
        }
        try {
            List<String> parsed = MAPPER.readValue(trimmed, STRING_LIST);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception malformed) {
            return List.of();
        }
    }

    private JsonLists() {
    }
}
