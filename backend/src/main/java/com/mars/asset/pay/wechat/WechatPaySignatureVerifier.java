package com.mars.asset.pay.wechat;

import com.mars.asset.pay.PemSupport;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 微信支付 V3 回调验签与报文解密（契约 §4.10 API-CB-05 · 资金安全前置）。
 *
 * <p>验签算法：平台证书公钥 + **SHA256withRSA**，待签串严格为
 * {@code timestamp + "\n" + nonce + "\n" + body + "\n"}（V3 规范）。
 *
 * <p>防重放：校验 {@code Wechatpay-Timestamp} 与本地时间之差不超过
 * {@link #MAX_CLOCK_SKEW}（默认 5 分钟）。
 *
 * <p><b>fail-closed</b>：未配置平台公钥时，任何验签请求一律拒绝（绝不「验不了就放行」）。
 */
public final class WechatPaySignatureVerifier {

    /** 允许的时间偏差（防重放）。 */
    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final PublicKey platformPublicKey;
    private final byte[] apiV3Key;

    public WechatPaySignatureVerifier(PublicKey platformPublicKey) {
        this(platformPublicKey, null);
    }

    public WechatPaySignatureVerifier(PublicKey platformPublicKey, byte[] apiV3Key) {
        this.platformPublicKey = platformPublicKey;
        this.apiV3Key = apiV3Key == null ? null : apiV3Key.clone();
    }

    /** 从平台证书 PEM 构造（生产用）。 */
    public static WechatPaySignatureVerifier fromCertificatePem(String certificatePem, String apiV3Key) {
        return new WechatPaySignatureVerifier(
                PemSupport.readAnyPublicKey(certificatePem),
                apiV3Key == null || apiV3Key.isBlank() ? null : apiV3Key.getBytes(StandardCharsets.UTF_8));
    }

    public boolean configured() {
        return platformPublicKey != null;
    }

    /**
     * 校验回调签名。
     *
     * @param timestamp 请求头 {@code Wechatpay-Timestamp}（秒）
     * @param nonce     请求头 {@code Wechatpay-Nonce}
     * @param body      原始请求体（<b>必须是未经任何改写的字节串</b>）
     * @param signature 请求头 {@code Wechatpay-Signature}（Base64）
     */
    public void verify(String timestamp, String nonce, String body, String signature, Instant now) {
        if (!configured()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未配置微信支付平台公钥，回调验签不可用", "signature", "verifier_not_configured");
        }
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(body) || isBlank(signature)) {
            throw reject("missing_headers");
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            throw reject("bad_timestamp");
        }
        Duration skew = Duration.between(Instant.ofEpochSecond(epochSeconds), now).abs();
        if (skew.compareTo(MAX_CLOCK_SKEW) > 0) {
            throw reject("stale_timestamp");
        }

        String signingInput = timestamp.trim() + "\n" + nonce.trim() + "\n" + body + "\n";
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(platformPublicKey);
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                throw reject("signature_mismatch");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw reject("verify_error");
        }
    }

    /**
     * 解密回调 {@code resource}（AES-256-GCM，APIv3 密钥取自配置）。
     *
     * @param ciphertextBase64 {@code resource.ciphertext}
     * @param nonce            {@code resource.nonce}
     * @param associatedData   {@code resource.associated_data}
     */
    public String decryptResource(String ciphertextBase64, String nonce, String associatedData) {
        if (apiV3Key == null || apiV3Key.length != 32) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未配置微信支付 APIv3 密钥（须 32 字节）", "resource", "key_not_configured");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiV3Key, "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "微信支付回调报文解密失败", "resource", "decrypt_error");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BizException reject(String reason) {
        // 统一对外语义：验签失败一律 40001（不向调用方区分失败原因，避免成为 oracle）
        return new BizException(ErrorCode.PARAM_INVALID, "微信支付回调验签失败", "signature", reason);
    }
}
