package com.mars.common;

/**
 * 平台业务错误码。
 *
 * <p>单一事实来源：S1a v0.4 §7.1（语义）+ S2a v1.2 §2.7（码值 ↔ HTTP ↔ 文案归属）。
 * <b>码值与语义不得在本仓库另立</b>：前端按 {@code code} 分支，文案由 S1b §09 回填在客户端。
 */
public enum ErrorCode {

    OK(0, 200, "ok"),
    /** 参数/内容校验失败（附 field / reason 定位）。敏感词命中亦复用本码，见契约 §2.7 备注。 */
    PARAM_INVALID(40001, 400, "参数校验失败"),
    /** 未登录或令牌失效（前端触发刷新或跳登录）。 */
    UNAUTHENTICATED(40100, 401, "未登录或令牌失效"),
    FORBIDDEN(40300, 403, "无权限"),
    /** 未完成实名认证；开播前置校验失败（BR-1 / AC-1）。 */
    REALNAME_REQUIRED(40310, 403, "未完成实名认证"),
    /** 未成年账号打赏超出限额（AC-6）。 */
    MINOR_LIMIT_EXCEEDED(40320, 403, "未成年账号打赏超出限额"),
    /** <b>已登录但</b>未绑定手机号；<b>未登录不使用本码</b>（用 {@link #UNAUTHENTICATED}）。 */
    PHONE_NOT_BOUND(40325, 403, "未绑定手机号"),
    /** 房间/资源已结束或不存在。 */
    RESOURCE_GONE(40330, 404, "房间或资源已结束或不存在"),
    /** 提现未达起提金额或账户不可提现（OP-16）。 */
    WITHDRAW_BELOW_MIN(40340, 403, "提现未达起提金额"),
    /** IAP 交易校验失败（验签 / 商品不匹配 / 环境不符）。 */
    IAP_VERIFY_FAILED(40345, 403, "IAP 交易校验失败"),
    /** 未实名打赏前置未满足（OP-21 开关开启时）。 */
    REALNAME_REQUIRED_FOR_TIP(40350, 403, "未实名打赏前置未满足"),
    /** 状态/幂等冲突；<b>幂等重放返回 200 + 首次结果，不用本码</b>。 */
    CONFLICT(40900, 409, "状态冲突"),
    RATE_LIMITED(42900, 429, "触发限流"),
    SYSTEM_ERROR(50000, 500, "系统错误");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** 按码值反查；未知码返回 {@code null}（调用方按“未收录码兜底”处理，不得抛异常）。 */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
