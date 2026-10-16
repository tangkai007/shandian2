package com.mars.live;

import com.mars.common.ErrorCode;

import java.util.List;

/**
 * 开播前置校验（FR-2.5 · BR-1/BR-2 · AC-1 · 契约 §4.3 API-LIVE-03）。
 *
 * <p>校验项固定四项，逐项返回：实名（未实名 → {@code 40310}）、类目白名单（OP-7 四类目外
 * → {@code 40001}）、标题机审、封面机审。任一不通过则 {@code pass=false}，且「开始直播」保持禁用。
 */
public final class PrecheckPolicy {

    public static final String KEY_REALNAME = "REALNAME";
    public static final String KEY_CATEGORY_ALLOWED = "CATEGORY_ALLOWED";
    public static final String KEY_TITLE_AUDIT = "TITLE_AUDIT";
    public static final String KEY_COVER_AUDIT = "COVER_AUDIT";

    public PrecheckResult check(boolean realnameVerified,
                               boolean categoryAllowed,
                               boolean titleAuditPass,
                               boolean coverAuditPass) {
        List<PrecheckItem> items = List.of(
                new PrecheckItem(KEY_REALNAME, realnameVerified,
                        realnameVerified ? null : ErrorCode.REALNAME_REQUIRED),
                new PrecheckItem(KEY_CATEGORY_ALLOWED, categoryAllowed,
                        categoryAllowed ? null : ErrorCode.PARAM_INVALID),
                new PrecheckItem(KEY_TITLE_AUDIT, titleAuditPass,
                        titleAuditPass ? null : ErrorCode.PARAM_INVALID),
                new PrecheckItem(KEY_COVER_AUDIT, coverAuditPass,
                        coverAuditPass ? null : ErrorCode.PARAM_INVALID));

        boolean pass = realnameVerified && categoryAllowed && titleAuditPass && coverAuditPass;
        return new PrecheckResult(pass, items);
    }
}
