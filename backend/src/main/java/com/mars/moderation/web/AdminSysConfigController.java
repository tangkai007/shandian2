package com.mars.moderation.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.common.ApiResponse;
import com.mars.common.ErrorCode;
import com.mars.common.BizException;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.common.support.JsonLists;
import com.mars.moderation.SysConfigCatalog;
import com.mars.moderation.SysConfigEntry;
import com.mars.moderation.SysConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台口径配置（契约 §4.9 · API-ADM-20 · S1b §11.3 全部口径）。
 *
 * <p>角色 {@code OPS}（与 S2a mock 守卫一致）。<b>改口径不发版</b>：读侧每次即时读取，
 * 运营改配置后立即生效。值按 {@code value_type} 还原为前端可用的类型
 * （{@code fen/int/days} → number，{@code percent} → number，{@code bool} → boolean，
 * {@code list} → string[]）。
 */
@RestController
@RequestMapping("/api/v1/admin/sys-config")
@RequireRoles({"OPS"})
public class AdminSysConfigController {

    private final SysConfigService sysConfigService;

    public AdminSysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /** API-ADM-20：全量口径（目录骨架 + DB 覆盖值）。 */
    @GetMapping
    public ApiResponse<PageResult<SysConfigRow>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        List<SysConfigRow> rows = sysConfigService.list().stream().map(SysConfigRow::of).toList();
        return ApiResponse.ok(AdminPaging.of(rows, page, size));
    }

    /** API-ADM-20：改一条口径 → 返回该条刷新后的行（前端 PUT 期望单行）。 */
    @PutMapping("/{key}")
    public ApiResponse<SysConfigRow> update(@PathVariable String key,
                                            @RequestBody UpdateRequest body,
                                            HttpServletRequest request) {
        List<SysConfigEntry> refreshed = sysConfigService.update(key, normalize(body.value()),
                AdminActor.id(), AdminActor.name(), AdminActor.role(), AdminActor.ip(request));
        SysConfigRow row = refreshed.stream()
                .filter(entry -> key.equals(entry.key()))
                .findFirst()
                .map(SysConfigRow::of)
                .orElseThrow(() -> new BizException(
                        ErrorCode.RESOURCE_GONE, "未知口径键", "key", "not_found"));
        return ApiResponse.ok(row);
    }

    /** 前端可能提交数组 / 数字 / 布尔 / 字符串，统一落库为字符串（数组序列化为 JSON）。 */
    static String normalize(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return JsonLists.toJson(list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }

    /** 口径更新请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(Object value) {
    }

    /** 口径行（S1b §11.3 / 前端 {@code SysConfigRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SysConfigRow(String key,
                               String label,
                               String valueType,
                               Object value,
                               String note) {

        static SysConfigRow of(SysConfigEntry entry) {
            return new SysConfigRow(entry.key(), entry.label(), entry.valueType(),
                    coerce(entry.valueType(), entry.value()), entry.note());
        }

        /** 按值类型把落库字符串还原为前端类型。 */
        static Object coerce(String valueType, String raw) {
            String text = raw == null ? "" : raw;
            if (valueType == null) {
                return text;
            }
            return switch (valueType) {
                case "fen", "int", "days" -> parseLong(text);
                case "percent" -> parseDouble(text);
                case "bool" -> Boolean.parseBoolean(text.trim());
                case "list" -> text.trim().startsWith("[") ? JsonLists.parse(text) : text;
                default -> text;
            };
        }

        private static long parseLong(String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException invalid) {
                return 0L;
            }
        }

        private static double parseDouble(String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException invalid) {
                return 0d;
            }
        }
    }
}
