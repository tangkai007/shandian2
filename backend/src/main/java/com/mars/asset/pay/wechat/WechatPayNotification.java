package com.mars.asset.pay.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;

/**
 * 微信支付 V3 回调解密后的交易通知（只取加币所需的字段）。
 *
 * @param outTradeNo    商户订单号（对应 {@code recharge_order.order_no}）
 * @param transactionId 微信支付订单号
 * @param amountFen     实付金额（分，{@code amount.total}）
 * @param tradeState    交易状态（{@code SUCCESS} 等）
 */
public record WechatPayNotification(
        String outTradeNo,
        String transactionId,
        long amountFen,
        String tradeState) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean success() {
        return "SUCCESS".equalsIgnoreCase(tradeState);
    }

    /** 解析解密后的 resource 明文 JSON。 */
    public static WechatPayNotification parse(String decryptedJson) {
        try {
            JsonNode root = MAPPER.readTree(decryptedJson);
            JsonNode amount = root.path("amount");
            if (!amount.has("total")) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "微信支付回调缺少金额字段", "amount", "missing");
            }
            String outTradeNo = root.path("out_trade_no").asText(null);
            if (outTradeNo == null || outTradeNo.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "微信支付回调缺少商户订单号", "out_trade_no", "missing");
            }
            return new WechatPayNotification(
                    outTradeNo,
                    root.path("transaction_id").asText(""),
                    amount.path("total").asLong(),
                    root.path("trade_state").asText(""));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "微信支付回调报文解析失败", "resource", "parse_error");
        }
    }
}
