package com.mars.asset.pay;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * PEM 解析（零依赖，JDK 自带）。
 *
 * <p>用于从配置读取三方支付平台的**公钥 / 证书**：微信支付用平台证书，支付宝用支付宝公钥。
 * 密钥只经环境变量/配置注入，<b>不入仓</b>（S1a §P0.3）。
 */
public final class PemSupport {

    private PemSupport() {
    }

    /** 解析 X.509 证书（{@code -----BEGIN CERTIFICATE-----}）。 */
    public static X509Certificate readCertificate(String pem) {
        try {
            byte[] der = decodeBody(pem, "CERTIFICATE");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
        } catch (Exception ex) {
            throw new IllegalArgumentException("无效的 X.509 证书 PEM", ex);
        }
    }

    /** 解析 X.509 公钥（{@code -----BEGIN PUBLIC KEY-----}，SubjectPublicKeyInfo）。 */
    public static PublicKey readPublicKey(String pem) {
        try {
            byte[] der = decodeBody(pem, "PUBLIC KEY");
            return java.security.KeyFactory.getInstance("RSA")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalArgumentException("无效的 RSA 公钥 PEM", ex);
        }
    }

    /** 从证书或公钥 PEM 中取出 RSA 公钥（配置可能给任一形式）。 */
    public static PublicKey readAnyPublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        return pem.contains("BEGIN CERTIFICATE") ? readCertificate(pem).getPublicKey() : readPublicKey(pem);
    }

    static byte[] decodeBody(String pem, String type) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM 内容为空");
        }
        String normalized = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
    }
}
