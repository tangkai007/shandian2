package com.mars.account.web;

import com.mars.account.service.SmsCodeVerifier;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AuthContext;
import com.mars.common.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

/**
 * 账号与认证端点（契约 §4.1）。
 *
 * <p><b>本轮装配范围</b>：手机号验证码登录 → 签发访问令牌（打通鉴权链路）。
 * <p><b>待接入（下个增量）</b>：微信 / Apple 换票与强制补绑手机号（DECISION-3/4）、
 * refresh 轮换与黑名单、第三方云实名核验、设备管理、RBAC 权限矩阵。
 * 未接入前不暴露对应端点，避免出现语义不完整的接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SmsCodeVerifier smsCodeVerifier;
    private final JwtTokenService tokenService;
    private final Clock clock;

    public AuthController(SmsCodeVerifier smsCodeVerifier, JwtTokenService tokenService, Clock clock) {
        this.smsCodeVerifier = smsCodeVerifier;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    public record SendSmsRequest(@NotBlank String phone, @NotBlank String scene) {
    }

    public record DevicePayload(String deviceId, String platform, String appVersion) {
    }

    public record LoginSmsRequest(@NotBlank String phone, @NotBlank String code, DevicePayload device) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expireIn,
                                long userId, boolean needBindPhone) {
    }

    /** 契约 §4.1 API-AUTH-01。 */
    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSms(@Valid @RequestBody SendSmsRequest request) {
        long retryAfter = smsCodeVerifier.send(request.phone(), request.scene());
        return ApiResponse.ok(Map.of("expire_in", 300, "retry_after", retryAfter));
    }

    /** 契约 §4.1 API-AUTH-02：手机号登录（手机号已在，无需补绑）。 */
    @PostMapping("/login/sms")
    public ApiResponse<TokenResponse> loginBySms(@Valid @RequestBody LoginSmsRequest request) {
        if (!smsCodeVerifier.verify(request.phone(), request.code())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码错误或已过期", "code", "invalid");
        }
        // TODO(account): 真实实现须 ①upsert user_identity(PHONE) ②回填 userId ③按 user_auth 决定 roles
        long userId = Math.abs(request.phone().hashCode());
        String token = tokenService.issue(userId, Set.of("VIEWER"), clock.instant());
        TokenResponse response = new TokenResponse(
                token, null, JwtTokenService.ACCESS_TTL.toSeconds(), userId, false);
        return ApiResponse.ok(response);
    }

    /**
     * 契约 §4.1 API-AUTH-11：实名状态查询。
     *
     * <p>脚手架返回「未实名」——<b>fail-safe</b>：在真实实名服务接入前，任何依赖
     * {@code realnameVerified} 的判定都会走「未实名」分支（如开播返回 {@code 40310}）。
     */
    @GetMapping("/realname")
    @com.mars.common.security.RequireRoles({})
    public ApiResponse<Map<String, Object>> realnameStatus() {
        long userId = AuthContext.requireUserId();
        return ApiResponse.ok(Map.of(
                "user_id", userId,
                "verify_status", "UNVERIFIED",
                "is_minor", false,
                "pending", "实名核验服务（OP-5 第三方云实名）待接入"));
    }
}
