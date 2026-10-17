package com.mars.moderation;

/** 机审告警类型（D-ADM-001 AI 告警时间线，前端 `IMAGE/TEXT/AUDIO`）。 */
public enum AlertType {

    IMAGE,
    TEXT,
    AUDIO;

    /** 宽松解析（未知/空 → {@code IMAGE}）。 */
    public static AlertType fromName(String name) {
        if (name == null || name.isBlank()) {
            return IMAGE;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return IMAGE;
        }
    }
}
