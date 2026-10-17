package com.mars.admin.model;

/**
 * 直播类目（契约 §4.9 API-ADM-17/18 · 前端 {@code CategoryRow}）。
 *
 * <p>DB 以 {@code status='ENABLED'|'DISABLED'} 存储，对外暴露 {@code enabled} 布尔（S2a 前端口径）。
 *
 * @param requireQualification 是否要求资质（OP-7）
 * @param restricted           是否限制未成年人观看（展示位，服务端一期不强制）
 */
public record Category(
        long id,
        String name,
        boolean requireQualification,
        boolean restricted,
        int sort,
        boolean enabled) {

    public String statusValue() {
        return enabled ? "ENABLED" : "DISABLED";
    }

    public Category withEditable(Boolean nextEnabled, Integer nextSort, String nextName) {
        return new Category(id, nextName == null ? name : nextName, requireQualification, restricted,
                nextSort == null ? sort : nextSort, nextEnabled == null ? enabled : nextEnabled);
    }
}
