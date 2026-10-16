package com.mars.spec;

import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.PageResult;
import com.mars.common.idempotency.IdempotencyRegistry;
import com.mars.common.money.ProportionalAllocator;
import com.mars.testkit.Registry;

/**
 * 通用约定 spec：错误码表（契约 §2.7）、响应封装、分页、幂等、按权重分配。
 */
public final class CommonSpecs {

    private CommonSpecs() {
    }

    public static void register(Registry registry) {
        registry.add("契约 §2.7 · 错误码 ↔ HTTP 映射逐项", a -> {
            a.eqLong(ErrorCode.OK.code(), 0L, "0 OK");
            a.eqLong(ErrorCode.OK.httpStatus(), 200L, "0 → 200");
            a.eqLong(ErrorCode.PARAM_INVALID.code(), 40001L, "40001");
            a.eqLong(ErrorCode.PARAM_INVALID.httpStatus(), 400L, "40001 → 400");
            a.eqLong(ErrorCode.UNAUTHENTICATED.code(), 40100L, "40100");
            a.eqLong(ErrorCode.UNAUTHENTICATED.httpStatus(), 401L, "40100 → 401");
            a.eqLong(ErrorCode.FORBIDDEN.code(), 40300L, "40300");
            a.eqLong(ErrorCode.FORBIDDEN.httpStatus(), 403L, "40300 → 403");
            a.eqLong(ErrorCode.REALNAME_REQUIRED.code(), 40310L, "40310");
            a.eqLong(ErrorCode.REALNAME_REQUIRED.httpStatus(), 403L, "40310 → 403");
            a.eqLong(ErrorCode.MINOR_LIMIT_EXCEEDED.code(), 40320L, "40320");
            a.eqLong(ErrorCode.PHONE_NOT_BOUND.code(), 40325L, "40325");
            a.eqLong(ErrorCode.PHONE_NOT_BOUND.httpStatus(), 403L, "40325 → 403");
            a.eqLong(ErrorCode.RESOURCE_GONE.code(), 40330L, "40330");
            a.eqLong(ErrorCode.RESOURCE_GONE.httpStatus(), 404L, "40330 → 404");
            a.eqLong(ErrorCode.WITHDRAW_BELOW_MIN.code(), 40340L, "40340");
            a.eqLong(ErrorCode.IAP_VERIFY_FAILED.code(), 40345L, "40345");
            a.eqLong(ErrorCode.REALNAME_REQUIRED_FOR_TIP.code(), 40350L, "40350");
            a.eqLong(ErrorCode.CONFLICT.code(), 40900L, "40900");
            a.eqLong(ErrorCode.CONFLICT.httpStatus(), 409L, "40900 → 409");
            a.eqLong(ErrorCode.RATE_LIMITED.code(), 42900L, "42900");
            a.eqLong(ErrorCode.RATE_LIMITED.httpStatus(), 429L, "42900 → 429");
            a.eqLong(ErrorCode.SYSTEM_ERROR.code(), 50000L, "50000");
            a.eqLong(ErrorCode.SYSTEM_ERROR.httpStatus(), 500L, "50000 → 500");
        });

        registry.add("契约 §2.7 · 40100 与 40325 边界（未登录不得复用 40325）", a -> {
            a.eq(ErrorCode.fromCode(40100), ErrorCode.UNAUTHENTICATED, "40100 反查");
            a.eq(ErrorCode.fromCode(40325), ErrorCode.PHONE_NOT_BOUND, "40325 反查");
            a.eqLong(ErrorCode.UNAUTHENTICATED.httpStatus(), 401L, "未登录 → HTTP 401");
            a.eqLong(ErrorCode.PHONE_NOT_BOUND.httpStatus(), 403L, "已登录未绑手机号 → HTTP 403");
            a.isNull(ErrorCode.fromCode(12345), "未收录码反查返回 null（走兜底，不抛异常）");
        });

        registry.add("契约 §2.2 · 统一响应封装", a -> {
            ApiResponse<String> ok = ApiResponse.ok("x", "trace-1");
            a.eqLong(ok.code(), 0L, "成功 code=0");
            a.isTrue(ok.success(), "成功标志");
            a.eq(ok.data(), "x", "成功 data");
            a.eq(ok.traceId(), "trace-1", "traceId 透传");

            ApiResponse<String> failed = ApiResponse.fail(ErrorCode.RATE_LIMITED);
            a.eqLong(failed.code(), 42900L, "失败 code");
            a.eq(failed.message(), ErrorCode.RATE_LIMITED.defaultMessage(), "失败默认文案");
            a.isNull(failed.data(), "失败 data 为 null");
            a.isFalse(failed.success(), "失败标志");

            ApiResponse<String> custom = ApiResponse.fail(ErrorCode.PARAM_INVALID, "content exceeds 50 chars");
            a.eq(custom.message(), "content exceeds 50 chars", "自定义 message 覆盖默认");
        });

        registry.add("契约 §2.4 · 分页参数边界（page≥1，1≤size≤100）", a -> {
            a.isTrue(PageResult.isPageValid(1, 1), "1/1 合法");
            a.isTrue(PageResult.isPageValid(1, 20), "1/20 合法");
            a.isTrue(PageResult.isPageValid(1, 100), "1/100 合法（上限）");
            a.isFalse(PageResult.isPageValid(0, 20), "page=0 非法");
            a.isFalse(PageResult.isPageValid(1, 0), "size=0 非法");
            a.isFalse(PageResult.isPageValid(1, 101), "size=101 非法");
            a.eqLong(PageResult.DEFAULT_SIZE, 20L, "默认 size");
            a.eqLong(PageResult.MAX_SIZE, 100L, "最大 size");
        });

        registry.add("契约 §2.6 · 幂等：首次执行、重放回吐、缺键拒绝", a -> {
            IdempotencyRegistry registry1 = new IdempotencyRegistry();
            a.isTrue(registry1.markIfAbsent("gift-order-1"), "首次 → 应执行");
            a.isFalse(registry1.markIfAbsent("gift-order-1"), "重放 → 不重复执行");
            registry1.bindResult("gift-order-1", "order_no=G123");
            a.eq(registry1.resultOf("gift-order-1").orElse(null), "order_no=G123", "重放回吐首次结果");

            a.throwsType(BizException.class, () -> registry1.markIfAbsent("  "), "空键应拒绝");
            try {
                registry1.markIfAbsent(null);
                a.fail("null 键应拒绝");
            } catch (BizException e) {
                a.eq(e.errorCode(), ErrorCode.PARAM_INVALID, "空键错误码 = 40001");
                a.eq(e.detailField(), "Idempotency-Key", "空键 field 定位");
            }
        });

        registry.add("契约 §3.4.6 ③ · 按权重整数分配（Σ 恒等于总量）", a -> {
            long[] coins = ProportionalAllocator.allocate(400_000L, new long[]{800_000L, 500_000L, 300_000L});
            a.eqLong(coins[0], 200_000L, "微信 50% 分摊");
            a.eqLong(coins[1], 125_000L, "支付宝 31.25% 分摊");
            a.eqLong(coins[2], 75_000L, "IAP 18.75% 分摊");
            a.eqLong(coins[0] + coins[1] + coins[2], 400_000L, "Σ == 总量");

            long[] thirds = ProportionalAllocator.allocate(100L, new long[]{1L, 1L, 1L});
            a.eqLong(thirds[0] + thirds[1] + thirds[2], 100L, "余数按最大余数法补齐，Σ == 100");

            long[] equal = ProportionalAllocator.allocate(3L, new long[]{10L, 10L, 10L});
            a.eqLong(equal[0], 1L, "3/3 等分第一份");
            a.eqLong(equal[1], 1L, "3/3 等分第二份");
            a.eqLong(equal[2], 1L, "3/3 等分第三份");

            long[] zeroTotal = ProportionalAllocator.allocate(0L, new long[]{1L, 1L});
            a.eqLong(zeroTotal[0], 0L, "总量 0 → 全 0");
            long[] zeroWeight = ProportionalAllocator.allocate(10L, new long[]{0L, 0L});
            a.eqLong(zeroWeight[0], 0L, "权重全 0 → 全 0（不除零）");
        });
    }
}
