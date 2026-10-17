package com.mars.account.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.account.service.SmsCodeVerifier;
import com.mars.admin.model.RealnameRecord;
import com.mars.admin.service.ChannelLoginService;
import com.mars.admin.service.RealnameService;
import com.mars.admin.service.RefreshTokenService;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AuthContext;
import com.mars.common.security.JwtTokenService;
import com.mars.common.security.RequireRoles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * 账号与认证端点（契约 §4.1）。
 *
 * <p><b>本轮装配范围</b>：手机号验证码登录、微信 / Apple 渠道登录（登录后强制补绑手机号）、
 * refresh 轮换与登出、实名提交与状态查询。
 *
 * <p><b>待接入（已登记限制）</b>：微信 {@code code2session} / Apple {@code identityToken}
 * 真实换票与验签（本期占位）、第三方云实名核验（本期占位 {@code PLACEHOLDER}）、
 * 设备管理（API-ACCT-03/04）、身份绑定解绑（API-AUTH-07/08/09）、RBAC 权限矩阵。
 * 未接入的端点不暴露，避免语义不完整的接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Set<String> DEFAULT_ROLES = Set.of("VIEWER");

    private final SmsCodeVerifier smsCodeVerifier;
    private final JwtTokenService tokenService;
    private final RealnameService realnameService;
    private final ChannelLoginService channelLoginService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthController(SmsCodeVerifier smsCodeVerifier,
                          JwtTokenService tokenService,
                          RealnameService realnameService,
                          ChannelLoginService channelLoginService,
                          RefreshTokenService refreshTokenService,
                          Clock clock) {
        this.smsCodeVerifier = smsCodeVerifier;
        this.tokenService = tokenService;
        this.realnameService = realnameService;
        this.channelLoginService = channelLoginService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    public record SendSmsRequest(@NotBlank String phone, @NotBlank String scene) {
    }

    public record DevicePayload(String deviceId, String platform, String appVersion) {
    }

    public record LoginSmsRequest(@NotBlank String phone, @NotBlank String code, DevicePayload device) {
    }

    public record ChannelLoginRequest(@NotBlank String code, DevicePayload device) {
    }

    public record AppleLoginRequest(String identityToken, String authorizationCode,
                                    String fullName, String email, DevicePayload device) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RealnameSubmitRequest(@NotBlank String realName,
                                        @NotBlank String idCardNo,
                                        LocalDate birthDate) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expireIn,
                                long userId, boolean needBindPhone) {
    }

    /** 实名状态（契约 §4.1 API-AUTH-11）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RealnameStatus(String verifyStatus,
                                 boolean isMinor,
                                 LocalDate birthDate,
                                 java.time.LocalDateTime verifiedAt) {
    }

    /** 实名提交应答（契约 §4.1 API-AUTH-10）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RealnameSubmitAck(String verifyStatus,
                                    boolean isMinor,
                                    String verifyChannel,
                                    String failReason) {
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
        RefreshTokenService.TokenPair pair = refreshTokenService.issue(userId, DEFAULT_ROLES);
        return ApiResponse.ok(new TokenResponse(pair.accessToken(), pair.refreshToken(),
                pair.expireIn(), userId, false));
    }

    /** 契约 §4.1 API-AUTH-03：微信登录（服务端换票，登录后强制补绑手机号）。 */
    @PostMapping("/login/wechat")
    public ApiResponse<TokenResponse> loginByWechat(@Valid @RequestBody ChannelLoginRequest request) {
        return ApiResponse.ok(channelLogin(ChannelLoginService.PROVIDER_WECHAT, request.code()));
    }

    /** 契约 §4.1 API-AUTH-04：Apple 登录（与微信同流程，登录后强制补绑手机号）。 */
    @PostMapping("/login/apple")
    public ApiResponse<TokenResponse> loginByApple(@RequestBody AppleLoginRequest request) {
        String code = request.identityToken() == null || request.identityToken().isBlank()
                ? request.authorizationCode() : request.identityToken();
        return ApiResponse.ok(channelLogin(ChannelLoginService.PROVIDER_APPLE, code));
    }

    /** 契约 §4.1 API-AUTH-05：refresh 轮换（旧票立即失效）。 */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        long userId = refreshTokenService.userIdOf(request.refreshToken())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHENTICATED, "refresh 令牌失效"));
        RefreshTokenService.TokenPair pair =
                refreshTokenService.rotate(request.refreshToken(), this::rolesOf);
        return ApiResponse.ok(new TokenResponse(pair.accessToken(), pair.refreshToken(),
                pair.expireIn(), userId, false));
    }

    /** 契约 §4.1 API-AUTH-06：登出（当前 refresh 入黑名单，幂等）。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        refreshTokenService.revoke(request == null ? null : request.refreshToken());
        return ApiResponse.ok(null);
    }

    /** 契约 §4.1 API-AUTH-10：实名提交（连续失败 5 次锁定 → {@code 42900}）。 */
    @PostMapping("/realname/submit")
    @RequireRoles({})
    public ApiResponse<RealnameSubmitAck> submitRealname(@Valid @RequestBody RealnameSubmitRequest request) {
        long userId = AuthContext.requireUserId();
        RealnameRecord record = realnameService.submit(userId, request.realName(),
                request.idCardNo(), request.birthDate());
        return ApiResponse.ok(new RealnameSubmitAck(record.verifyStatus(), record.isMinor(),
                record.verifyChannel(), record.failReason()));
    }

    /** 契约 §4.1 API-AUTH-11：实名状态查询。 */
    @GetMapping("/realname")
    @RequireRoles({})
    public ApiResponse<RealnameStatus> realnameStatus() {
        long userId = AuthContext.requireUserId();
        RealnameRecord record = realnameService.status(userId);
        return ApiResponse.ok(new RealnameStatus(record.verifyStatus(), record.isMinor(),
                record.birthDate(), record.verifiedAt()));
    }

    private TokenResponse channelLogin(String provider, String code) {
        ChannelLoginService.LoginResult result = channelLoginService.login(provider, code, this::rolesOf);
        return new TokenResponse(result.accessToken(), result.refreshToken(),
                result.expireIn(), result.userId(), result.needBindPhone());
    }

    /** 缺省角色 {@code VIEWER}；真实实现按 {@code user_auth} / 主播归属解析（已登记限制）。 */
    private Set<String> rolesOf(long userId) {
        return DEFAULT_ROLES;
    }
}
