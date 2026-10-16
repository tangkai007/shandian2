package com.mars.spec;

import com.mars.asset.pay.PemSupport;
import com.mars.asset.pay.alipay.AlipaySignatureVerifier;
import com.mars.asset.pay.wechat.WechatPayNotification;
import com.mars.asset.pay.wechat.WechatPaySignatureVerifier;
import com.mars.common.BizException;
import com.mars.testkit.Registry;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付回调验签 spec（契约 §4.10 API-CB-05/06 · 资金安全 · <b>AC-5 的资金入口</b>）。
 *
 * <p>用真实 RSA 密钥对签名/验签（JDK 内置密码学，无 mock）：正向通过、篡改拒绝、
 * 时间窗拒绝、未配置 fail-closed、AES-GCM 解密回环。
 */
public final class PayVerifierSpecs {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final byte[] API_V3_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private PayVerifierSpecs() {
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sign(PrivateKey key, String message) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String wechatSigningInput(String timestamp, String nonce, String body) {
        return timestamp + "\n" + nonce + "\n" + body + "\n";
    }

    public static void register(Registry registry) {

        registry.add("AC-5/资金安全 · 微信回调验签：正例通过、篡改/过期/缺头一律拒绝", a -> {
            KeyPair pair = keyPair();
            WechatPaySignatureVerifier verifier =
                    new WechatPaySignatureVerifier(pair.getPublic(), API_V3_KEY);

            String timestamp = String.valueOf(NOW.getEpochSecond());
            String nonce = "abc123";
            String body = "{\"event_type\":\"TRANSACTION.SUCCESS\"}";
            String signature = sign(pair.getPrivate(), wechatSigningInput(timestamp, nonce, body));

            a.isTrue(verifier.configured(), "已配置平台公钥");
            verifier.verify(timestamp, nonce, body, signature, NOW);
            a.isTrue(true, "合法签名通过（未抛异常）");

            a.throwsType(BizException.class,
                    () -> verifier.verify(timestamp, nonce, body + " ", signature, NOW),
                    "请求体被篡改 → 拒绝");
            a.throwsType(BizException.class,
                    () -> verifier.verify(String.valueOf(NOW.minusSeconds(600).getEpochSecond()),
                            nonce, body, signature, NOW),
                    "时间戳超出时间窗（重放） → 拒绝");
            a.throwsType(BizException.class, () -> verifier.verify(null, nonce, body, signature, NOW),
                    "缺少时间戳头 → 拒绝");
            a.throwsType(BizException.class, () -> verifier.verify(timestamp, nonce, body, "!!!not-base64!!!", NOW),
                    "非法签名串 → 拒绝");

            // 另一把密钥签的签名同样必须被拒
            KeyPair attacker = keyPair();
            String forged = sign(attacker.getPrivate(), wechatSigningInput(timestamp, nonce, body));
            a.throwsType(BizException.class, () -> verifier.verify(timestamp, nonce, body, forged, NOW),
                    "他人密钥伪造签名 → 拒绝");
        });

        registry.add("AC-5/资金安全 · 未配置密钥时必须 fail-closed（绝不「验不了就放行」）", a -> {
            WechatPaySignatureVerifier unconfigured = new WechatPaySignatureVerifier(null, null);
            a.isFalse(unconfigured.configured(), "未配置");
            a.throwsType(BizException.class,
                    () -> unconfigured.verify("1", "n", "{}", "sig", NOW), "微信：未配置 → 拒绝");
            a.throwsType(BizException.class,
                    () -> unconfigured.decryptResource("x", "y", "z"), "微信：无 APIv3 密钥 → 拒绝解密");

            AlipaySignatureVerifier alipayUnconfigured = new AlipaySignatureVerifier(null);
            a.isFalse(alipayUnconfigured.configured(), "支付宝未配置");
            a.throwsType(BizException.class,
                    () -> alipayUnconfigured.verify(Map.of("out_trade_no", "R1"), "sig"),
                    "支付宝：未配置 → 拒绝");
        });

        registry.add("AC-5/资金安全 · 微信 resource 解密（AES-256-GCM）回环", a -> {
            KeyPair pair = keyPair();
            WechatPaySignatureVerifier verifier =
                    new WechatPaySignatureVerifier(pair.getPublic(), API_V3_KEY);

            String plain = "{\"out_trade_no\":\"RECHARGE-1\",\"transaction_id\":\"WX-1\","
                    + "\"trade_state\":\"SUCCESS\",\"amount\":{\"total\":600}}";
            String nonce = "nonce0000000";
            String associatedData = "transaction";
            String ciphertext = encrypt(plain, nonce, associatedData);

            String decrypted = verifier.decryptResource(ciphertext, nonce, associatedData);
            a.eq(decrypted, plain, "解密回环一致");

            WechatPayNotification notification = WechatPayNotification.parse(decrypted);
            a.eq(notification.outTradeNo(), "RECHARGE-1", "商户订单号");
            a.eq(notification.transactionId(), "WX-1", "微信订单号");
            a.eqLong(notification.amountFen(), 600L, "实付金额（分）");
            a.isTrue(notification.success(), "交易状态 SUCCESS");
        });

        registry.add("AC-5 · 微信交易通知解析：缺字段即拒绝（不得默认成 0 元加币）", a -> {
            a.throwsType(BizException.class,
                    () -> WechatPayNotification.parse("{\"out_trade_no\":\"R1\"}"),
                    "缺 amount.total → 拒绝");
            a.throwsType(BizException.class,
                    () -> WechatPayNotification.parse("{\"amount\":{\"total\":600}}"),
                    "缺 out_trade_no → 拒绝");
            a.isFalse(WechatPayNotification.parse(
                    "{\"out_trade_no\":\"R1\",\"trade_state\":\"NOTPAY\",\"amount\":{\"total\":600}}").success(),
                    "未支付状态不算成功");
        });

        registry.add("AC-5/资金安全 · 支付宝：规范串 + RSA2 验签 + 金额换算", a -> {
            KeyPair pair = keyPair();
            AlipaySignatureVerifier verifier = new AlipaySignatureVerifier(pair.getPublic());

            Map<String, String> params = new LinkedHashMap<>();
            params.put("out_trade_no", "RECHARGE-2");
            params.put("total_amount", "6.00");
            params.put("trade_status", "TRADE_SUCCESS");
            params.put("sign_type", "RSA2");
            params.put("sign", "toBeReplaced");

            a.eq(AlipaySignatureVerifier.canonical(params),
                    "out_trade_no=RECHARGE-2&total_amount=6.00&trade_status=TRADE_SUCCESS",
                    "规范串：剔除 sign/sign_type、按参数名升序");

            String signature = sign(pair.getPrivate(), AlipaySignatureVerifier.canonical(params));
            verifier.verify(params, signature);
            a.isTrue(true, "合法签名通过（未抛异常）");

            Map<String, String> tampered = new LinkedHashMap<>(params);
            tampered.put("total_amount", "600.00");
            a.throwsType(BizException.class, () -> verifier.verify(tampered, signature),
                    "改动金额 → 验签失败");

            a.throwsType(BizException.class, () -> verifier.verify(params, null), "缺少签名 → 拒绝");
            a.throwsType(BizException.class, () -> verifier.verify(params, "###"), "非法签名串 → 拒绝");

            a.eqLong(AlipaySignatureVerifier.yuanToFen("6.00"), 600L, "6.00 元 = 600 分");
            a.eqLong(AlipaySignatureVerifier.yuanToFen("0.01"), 1L, "0.01 元 = 1 分");
            a.eqLong(AlipaySignatureVerifier.yuanToFen("51.80"), 5_180L, "51.80 元 = 5180 分");
            a.throwsType(BizException.class, () -> AlipaySignatureVerifier.yuanToFen("abc"),
                    "非法金额 → 拒绝");
            a.throwsType(BizException.class, () -> AlipaySignatureVerifier.yuanToFen(""),
                    "空金额 → 拒绝");
        });

        registry.add("资金安全 · PEM 解析：公钥 PEM 回环", a -> {
            KeyPair pair = keyPair();
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                    .encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";

            a.eq(PemSupport.readPublicKey(pem), pair.getPublic(), "公钥 PEM 解析一致");
            a.isNull(PemSupport.readAnyPublicKey(""), "空 PEM → null（交由调用方 fail-closed）");
            a.throwsType(IllegalArgumentException.class,
                    () -> PemSupport.readPublicKey("not-a-pem"), "非法 PEM → 明确异常");
        });
    }

    private static String encrypt(String plain, String nonce, String associatedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(API_V3_KEY, "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
