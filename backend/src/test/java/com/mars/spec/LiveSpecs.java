package com.mars.spec;

import com.mars.common.ErrorCode;
import com.mars.live.PrecheckItem;
import com.mars.live.PrecheckPolicy;
import com.mars.live.PrecheckResult;
import com.mars.testkit.Registry;

/**
 * 开播 spec（AC-1 / BR-1 / BR-2）：前置校验逐项返回。
 */
public final class LiveSpecs {

    private LiveSpecs() {
    }

    public static void register(Registry registry) {

        registry.add("AC-1 · 全部通过：pass=true 且四项均无错误码", a -> {
            PrecheckResult result = new PrecheckPolicy().check(true, true, true, true);
            a.isTrue(result.pass(), "全部通过");
            a.eqLong((long) result.items().size(), 4L, "固定四项");
            a.isNull(result.primaryErrorCode(), "无主错误码");
            for (PrecheckItem item : result.items()) {
                a.isNull(item.errorCode(), "通过项无错误码：" + item.key());
            }
        });

        registry.add("AC-1 · 未实名 → 主错误码 40310（开播按钮禁用）", a -> {
            PrecheckResult result = new PrecheckPolicy().check(false, true, true, true);
            a.isFalse(result.pass(), "未实名不通过");
            a.eq(result.primaryErrorCode(), ErrorCode.REALNAME_REQUIRED, "主错误码 = 40310");
            a.eqLong(result.primaryErrorCode().code(), 40310L, "40310 码值");
            a.eq(result.items().get(0).key(), PrecheckPolicy.KEY_REALNAME, "首项为实名校验");
            a.isFalse(result.items().get(0).pass(), "实名项未通过");
        });

        registry.add("BR-2 · 类目白名单与标题/封面机审逐项返回", a -> {
            PrecheckResult category = new PrecheckPolicy().check(true, false, true, true);
            a.isFalse(category.pass(), "类目不在白名单 → 不通过");
            a.eq(category.primaryErrorCode(), ErrorCode.PARAM_INVALID, "类目项 → 40001");
            a.isTrue(category.items().get(0).pass(), "实名项仍为通过（逐项独立）");

            PrecheckResult title = new PrecheckPolicy().check(true, true, false, true);
            a.eq(title.primaryErrorCode(), ErrorCode.PARAM_INVALID, "标题机审未过 → 40001");

            PrecheckResult cover = new PrecheckPolicy().check(true, true, true, false);
            a.eq(cover.primaryErrorCode(), ErrorCode.PARAM_INVALID, "封面机审未过 → 40001");

            a.eq(PrecheckPolicy.KEY_CATEGORY_ALLOWED, "CATEGORY_ALLOWED", "类目项标识");
            a.eq(PrecheckPolicy.KEY_TITLE_AUDIT, "TITLE_AUDIT", "标题项标识");
            a.eq(PrecheckPolicy.KEY_COVER_AUDIT, "COVER_AUDIT", "封面项标识");
        });
    }
}
