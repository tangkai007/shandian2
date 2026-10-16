package com.mars.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 访问令牌签发与校验（HS256，契约 §2.3：Bearer JWT，access 2h）。
 *
 * <p>不引入额外依赖（JDK 的 {@code javax.crypto} + Jackson）。生产可平滑替换为
 * 非对称签名（RS256）或统一网关签发 —— 只要 {@link JwtClaims} 语义不变即可。
 */
public final class JwtTokenService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    /** access 令牌有效期（契约 §2.3）。 */
    public static final Duration ACCESS_TTL = Duration.ofHours(2);

    private final byte[] secret;

    public JwtTokenService(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("jwt secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(long userId, Set<String> roles, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("roles", String.join(",", roles));
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(ACCESS_TTL).getEpochSecond());

        String signingInput = b64(HEADER_JSON) + "." + b64(toJson(payload));
        return signingInput + "." + b64(hmac(signingInput));
    }

    /** 验签 + 过期校验；任何异常一律返回 {@link Optional#empty()}（不向调用方泄露失败原因）。 */
    public Optional<JwtClaims> verify(String token, Instant now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = MAPPER.readValue(
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), Map.class);
            long userId = Long.parseLong(String.valueOf(payload.get("sub")));
            Set<String> roles = new LinkedHashSet<>();
            String rawRoles = String.valueOf(payload.getOrDefault("roles", ""));
            for (String role : rawRoles.split(",")) {
                if (!role.isBlank()) {
                    roles.add(role.trim());
                }
            }
            Instant issuedAt = Instant.ofEpochSecond(((Number) payload.get("iat")).longValue());
            Instant expiresAt = Instant.ofEpochSecond(((Number) payload.get("exp")).longValue());
            JwtClaims claims = new JwtClaims(userId, roles, issuedAt, expiresAt);
            return claims.expiredAt(now) ? Optional.empty() : Optional.of(claims);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    private static String b64(String raw) {
        return b64(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize jwt payload", ex);
        }
    }
}
