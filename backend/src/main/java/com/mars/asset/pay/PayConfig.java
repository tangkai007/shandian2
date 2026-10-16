package com.mars.asset.pay;

import com.mars.asset.pay.alipay.AlipaySignatureVerifier;
import com.mars.asset.pay.wechat.WechatPaySignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付验签装配。
 *
 * <p>密钥/证书一律经环境变量注入（{@code mars.pay.*}），<b>不入仓</b>。
 * 未配置时构造出的验证器处于 <b>fail-closed</b> 状态：能启动，但任何回调都会被拒绝 ——
 * 这样既不会让本地开发起不来，也绝不会出现「验不了就放行」。
 */
@Configuration
public class PayConfig {

    @Bean
    public WechatPaySignatureVerifier wechatPaySignatureVerifier(
            @Value("${mars.pay.wechat.platform-cert-pem:}") String platformCertPem,
            @Value("${mars.pay.wechat.api-v3-key:}") String apiV3Key) {
        return WechatPaySignatureVerifier.fromCertificatePem(platformCertPem, apiV3Key);
    }

    @Bean
    public AlipaySignatureVerifier alipaySignatureVerifier(
            @Value("${mars.pay.alipay.public-key-pem:}") String alipayPublicKeyPem) {
        return new AlipaySignatureVerifier(PemSupport.readAnyPublicKey(alipayPublicKeyPem));
    }
}
