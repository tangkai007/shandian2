package com.mars.moderation.web;

import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.PageResult;

import java.util.List;

/** 后台列表统一分页（契约 §2.4：{@code page} 从 1 起，{@code 1 ≤ size ≤ 100}）。 */
final class AdminPaging {

    private AdminPaging() {
    }

    /** 内存切片分页（仓储已返回全量列表，一期数据量小，不做 SQL 级分页）。 */
    static <T> PageResult<T> of(List<T> all, int page, int size) {
        if (!PageResult.isPageValid(page, size)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分页参数非法", "page", "invalid");
        }
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), page, size);
    }
}
