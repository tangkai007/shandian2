package com.mars.live;

import com.mars.common.ErrorCode;

import java.util.List;

/**
 * 开播前置校验结果（AC-1 / BR-1 / BR-2）。
 *
 * <p>逐项返回，前端按 {@code errorCode} 渲染（未实名时「开始直播」按钮禁用）；
 * {@link #pass()} 为全部项通过。
 *
 * @param pass  是否全部通过
 * @param items 逐项结果（固定顺序，便于前端稳定渲染）
 */
public record PrecheckResult(boolean pass, List<PrecheckItem> items) {

    public PrecheckResult {
        items = List.copyOf(items);
    }

    /** 首个未通过项的错误码；全部通过返回 {@code null}。 */
    public ErrorCode primaryErrorCode() {
        for (PrecheckItem item : items) {
            if (!item.pass()) {
                return item.errorCode();
            }
        }
        return null;
    }
}
