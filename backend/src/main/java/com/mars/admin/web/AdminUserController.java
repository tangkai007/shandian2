package com.mars.admin.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.admin.model.UserAccount;
import com.mars.admin.service.AdminUserService;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.moderation.web.AdminActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户管理（契约 §4.9 API-ADM-01/02/03 · FR-11.1）。
 *
 * <p>角色 {@code OPS}。字段名一律 <b>snake_case</b>（与后台前端 {@code contract.ts} 冻结一致）。
 * 身份取自服务端鉴权上下文（{@link AdminActor}），不信任前端传参。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequireRoles({"OPS"})
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    /** API-ADM-01：用户列表（关键字命中昵称 / 手机号哈希 / 用户 ID）。 */
    @GetMapping
    public ApiResponse<PageResult<AdminUserRow>> list(@RequestParam(defaultValue = "") String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        List<AdminUserRow> rows = users.list(keyword).stream().map(AdminUserController::toRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    /** API-ADM-02：封禁。 */
    @PostMapping("/{userId}/ban")
    public ApiResponse<AdminUserRow> ban(@PathVariable String userId,
                                        @RequestBody BanRequest body,
                                        HttpServletRequest request) {
        UserAccount account = users.ban(parseId(userId), body.reasonCode(), body.remark(),
                AdminActor.operator(request));
        return ApiResponse.ok(toRow(account));
    }

    /** API-ADM-03：解封。 */
    @PostMapping("/{userId}/unban")
    public ApiResponse<AdminUserRow> unban(@PathVariable String userId, HttpServletRequest request) {
        return ApiResponse.ok(toRow(users.unban(parseId(userId), AdminActor.operator(request))));
    }

    static AdminUserRow toRow(UserAccount account) {
        return new AdminUserRow(
                String.valueOf(account.userId()),
                account.nickname(),
                maskPhone(account.phoneHash()),
                account.realnameVerified(),
                account.isMinor(),
                account.status(),
                account.anchorId() == null ? null : String.valueOf(account.anchorId()),
                account.registeredAt(),
                account.banReason());
    }

    /**
     * 手机号脱敏展示。
     *
     * <p>服务端只存 {@code phone_hash}（SHA-256）与不可解密的 {@code phone_enc}，无法还原明文号码，
     * 故此处按「前 3 + **** + 后 4」对哈希做稳定脱敏 —— 已登记为「已知限制」（接入可解密方案后替换）。
     */
    static String maskPhone(String phoneHash) {
        if (phoneHash == null || phoneHash.isBlank()) {
            return "";
        }
        String hex = phoneHash.replaceAll("[^0-9a-fA-F]", "");
        if (hex.length() <= 7) {
            return "****";
        }
        return hex.substring(0, 3) + "****" + hex.substring(hex.length() - 4);
    }

    /** 宽松解析数字 ID：容忍空串与携带前缀的形态（mock 兼容）。 */
    static long parseId(String raw) {
        if (raw == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户 ID 缺失", "user_id", "blank");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户 ID 非法", "user_id", "invalid");
        }
        return Long.parseLong(digits);
    }

    /** 封禁请求体（{@code reason_code} / {@code remark}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BanRequest(String reasonCode, String remark) {
    }

    /** 用户行（S2a §4.9 API-ADM-01 · 前端 {@code AdminUserRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AdminUserRow(String userId,
                               String nickname,
                               String phoneMasked,
                               boolean realnameVerified,
                               boolean isMinor,
                               String status,
                               String anchorId,
                               LocalDateTime registeredAt,
                               String banReason) {
    }
}
