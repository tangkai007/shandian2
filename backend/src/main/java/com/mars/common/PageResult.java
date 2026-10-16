package com.mars.common;

import java.util.List;

/**
 * 统一分页负载（S2a v1.2 §2.4）：{@code { list, total, page, size }}。
 *
 * <p>{@code page} 从 1 起；{@code size} 默认 20、最大 100（越界由调用方返回
 * {@link ErrorCode#PARAM_INVALID}）。
 */
public final class PageResult<T> {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final List<T> list;
    private final long total;
    private final int page;
    private final int size;

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = List.copyOf(list);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> list() {
        return list;
    }

    public long total() {
        return total;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    /** 分页参数校验：page ≥ 1，1 ≤ size ≤ 100。 */
    public static boolean isPageValid(int page, int size) {
        return page >= 1 && size >= 1 && size <= MAX_SIZE;
    }
}
