package com.mars.asset.pay.alipay;

import com.mars.asset.pay.PemSupport;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付宝异步通知验签（契约 §4.10 API-CB-06 · 资金安全前置）。
 *
 * <p>算法：**SHA256withRSA（RSA2）**，待签串为「剔除 {@code sign} / {@code sign_type} 与空值后，
 * 按参数名升序，以 {@code k=v} 用 {@code &} 连接」的规范串（支付宝开放平台规范）。
 *
 * <p><b>fail-closed</b>：未配置支付宝公钥时一律拒绝。
 */
public final class AlipaySignatureVerifier {

    private final PublicKey alipayPublicKey;

    public AlipaySignatureVerifier(PublicKey alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    public boolean configured() {
        return alipayPublicKey != null;
    }

    /** 待签规范串（同时也是测试与排查时的可复算依据）。 */
    public static String canonical(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (params != null) {
            params.forEach((key, value) -> {
                if (key == null || "sign".equals(key) || "sign_type".equals(key)) {
                    return;
                }
                if (value == null || value.isEmpty()) {
                    return;
                }
                sorted.put(key, value);
            });
        }
        StringBuilder builder = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(key).append('=').append(value);
        });
        return builder.toString();
    }

    /**
     * 校验异步通知签名。
     *
     * @param params    全部通知参数（含 {@code sign}）
     * @param signature Base64 签名（通常取自 {@code params.get("sign")}）
     */
    public void verify(Map<String, String> params, String signature) {
        if (!configured()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未配置支付宝公钥，回调验签不可用", "sign", "verifier_not_configured");
        }
        if (signature == null || signature.isBlank()) {
            throw reject("missing_signature");
        }
        String canonical = canonical(params);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(alipayPublicKey);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                throw reject("signature_mismatch");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw reject("verify_error");
        }
    }

    /** 支付宝金额为「元」字符串（如 {@code "6.00"}），转成分。 */
    public static long yuanToFen(String totalAmount) {
        if (totalAmount == null || totalAmount.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少支付金额", "total_amount", "missing");
        }
        try {
            return new java.math.BigDecimal(totalAmount.trim())
                    .movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "支付金额格式非法", "total_amount", "invalid");
        }
    }

    private static BizException reject(String reason) {
        return new BizException(ErrorCode.PARAM_INVALID, "支付宝回调验签失败", "sign", reason);
    }
}
