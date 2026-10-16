package com.mars.asset.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mars.asset.pay.alipay.AlipaySignatureVerifier;
import com.mars.asset.pay.wechat.WechatPayNotification;
import com.mars.asset.pay.wechat.WechatPaySignatureVerifier;
import com.mars.asset.recharge.RechargeOrder;
import com.mars.asset.recharge.RechargeService;
import com.mars.asset.repo.RechargeOrderRepository;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/**
 * 三方支付回调（契约 §4.10 API-CB-05/06 · AC-5 · 资金安全）。
 *
 * <p>时序（两端点一致）：**验签 → 状态判定 → 解密/取参 → 金额复核 → 幂等加币**。
 * 任何一步不通过一律拒绝，<b>绝不按未验证的回调金额加币</b>。
 *
 * <ul>
 *   <li>微信：V3 平台证书 RSA-SHA256 验签（含 5 分钟时间窗防重放）+ AES-256-GCM 解密 resource；</li>
 *   <li>支付宝：RSA2 规范串验签 + {@code total_amount}（元）换算成分后复核。</li>
 * </ul>
 *
 * <p>幂等由 {@link RechargeService#onPaid(String)} 的状态机保证（已 PAID 不重复加币），
 * 与三方「重投直到收到成功响应」的语义配套。
 */
@RestController
@RequestMapping("/webhook/pay")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    /** 微信支付成功事件类型。 */
    private static final String WECHAT_EVENT_SUCCESS = "TRANSACTION.SUCCESS";

    private final RechargeService rechargeService;
    private final RechargeOrderRepository orders;
    private final WechatPaySignatureVerifier wechatVerifier;
    private final AlipaySignatureVerifier alipayVerifier;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentCallbackController(RechargeService rechargeService,
                                     RechargeOrderRepository orders,
                                     WechatPaySignatureVerifier wechatVerifier,
                                     AlipaySignatureVerifier alipayVerifier,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.rechargeService = rechargeService;
        this.orders = orders;
        this.wechatVerifier = wechatVerifier;
        this.alipayVerifier = alipayVerifier;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 微信支付 V3 通知（API-CB-05）。 */
    @PostMapping("/wechat")
    public ApiResponse<Map<String, Object>> wechat(
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestBody String rawBody) {

        // 1) 验签（含时间窗防重放）；失败即抛 40001
        wechatVerifier.verify(timestamp, nonce, rawBody, signature, clock.instant());

        // 2) 解析事件外壳
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调报文非法", "body", "parse_error");
        }
        String eventType = root.path("event_type").asText("");
        if (!WECHAT_EVENT_SUCCESS.equals(eventType)) {
            // 非成功事件（如退款）由后续增量处理；此处确认收到即可，避免三方重投
            log.info("wechat callback ignored: event_type={} serial={}", eventType, serial);
            return ApiResponse.ok(Map.of("result", "ignored", "event_type", eventType));
        }

        // 3) 解密 resource → 交易明细
        JsonNode resource = root.path("resource");
        String plain = wechatVerifier.decryptResource(
                resource.path("ciphertext").asText(""),
                resource.path("nonce").asText(""),
                resource.path("associated_data").asText(""));
        WechatPayNotification notification = WechatPayNotification.parse(plain);
        if (!notification.success()) {
            return ApiResponse.ok(Map.of("result", "ignored", "trade_state", notification.tradeState()));
        }

        // 4) 金额复核 + 幂等加币
        settle(notification.outTradeNo(), notification.amountFen(), "WECHAT");
        return ApiResponse.ok(Map.of("result", "ok", "order_no", notification.outTradeNo()));
    }

    /**
     * 支付宝异步通知（API-CB-06）。
     *
     * <p>响应体必须为纯文本 {@code success}（支付宝规范），故本端点不套统一响应封装。
     */
    @PostMapping(value = "/alipay", produces = MediaType.TEXT_PLAIN_VALUE)
    public String alipay(@RequestParam Map<String, String> params) {
        alipayVerifier.verify(params, params.get("sign"));

        String tradeStatus = params.getOrDefault("trade_status", "");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("alipay callback ignored: trade_status={}", tradeStatus);
            return "success";
        }

        long amountFen = AlipaySignatureVerifier.yuanToFen(params.get("total_amount"));
        settle(params.get("out_trade_no"), amountFen, "ALIPAY");
        return "success";
    }

    /** 金额复核 → 幂等加币（同事务由 {@code RechargeService} 保证）。 */
    private void settle(String orderNo, long amountFen, String channel) {
        RechargeOrder order = orders.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(
                        ErrorCode.PARAM_INVALID, "充值单不存在", "order_no", "not_found"));
        if (!channel.equals(order.payChannel())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调渠道与订单不符", "channel", "mismatch");
        }
        if (order.amountFen() != amountFen) {
            // 金额复核：不一致一律拒绝（不得按回调金额加币）
            throw new BizException(ErrorCode.PARAM_INVALID, "回调金额与订单不一致", "amount_fen", "mismatch");
        }
        rechargeService.onPaid(orderNo);
    }
}
