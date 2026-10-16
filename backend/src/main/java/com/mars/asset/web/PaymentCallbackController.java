package com.mars.asset.web;

import com.mars.asset.recharge.RechargeOrder;
import com.mars.asset.recharge.RechargeService;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付回调（契约 §4.10 API-CB-05/06 · AC-5）。
 *
 * <p>已实现：<b>金额复核</b>（回调金额必须等于订单金额）+ 回调<b>幂等</b>（已 PAID 不重复加币）
 * + 同事务加币与币池增量。
 *
 * <p><b>已知装配缺口（见实现说明）</b>：三方<b>验签未接入</b>（需微信/支付宝商户密钥）。
 * 因此当前实现要求请求头携带 {@code X-Signature}，缺省即拒（{@code 40001}）；
 * 在验签实现接入前，本端点<b>不得对公网开放</b>（否则可伪造回调加币）。
 */
@RestController
@RequestMapping("/webhook/pay")
public class PaymentCallbackController {

    private final RechargeService rechargeService;
    private final RechargeOrderRepository orders;

    public PaymentCallbackController(RechargeService rechargeService, RechargeOrderRepository orders) {
        this.rechargeService = rechargeService;
        this.orders = orders;
    }

    public record CallbackPayload(String orderNo, long amountFen) {
    }

    @PostMapping("/wechat")
    public ApiResponse<Map<String, Object>> wechat(@RequestBody CallbackPayload payload,
                                                  @org.springframework.web.bind.annotation.RequestHeader(
                                                          value = "X-Signature", required = false) String signature) {
        return handle(payload, signature, "WECHAT");
    }

    @PostMapping("/alipay")
    public ApiResponse<Map<String, Object>> alipay(@RequestBody CallbackPayload payload,
                                                  @org.springframework.web.bind.annotation.RequestHeader(
                                                          value = "X-Signature", required = false) String signature) {
        return handle(payload, signature, "ALIPAY");
    }

    private ApiResponse<Map<String, Object>> handle(CallbackPayload payload, String signature, String channel) {
        if (signature == null || signature.isBlank()) {
            // TODO(asset): 接入三方验签后再开放；缺签名一律拒绝
            throw new BizException(ErrorCode.PARAM_INVALID, "回调验签未通过", "signature", "missing");
        }
        RechargeOrder order = orders.findByOrderNo(payload.orderNo())
                .orElseThrow(() -> new BizException(
                        ErrorCode.PARAM_INVALID, "充值单不存在", "order_no", "not_found"));
        if (!channel.equals(order.payChannel())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "渠道与订单不符", "channel", "mismatch");
        }
        if (order.amountFen() != payload.amountFen()) {
            // 金额复核：与订单不一致即拒绝（不得按回调金额加币）
            throw new BizException(ErrorCode.PARAM_INVALID, "回调金额与订单不一致", "amount_fen", "mismatch");
        }
        RechargeOrder paid = rechargeService.onPaid(order.orderNo());
        return ApiResponse.ok(Map.of("order_no", paid.orderNo(), "status", paid.status()));
    }
}
