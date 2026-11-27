package com.sysudate.api.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.identity.AccountRepository.Account;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtService {
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final byte[] serviceSecret;
    private final Duration lifetime;
    private final Clock clock;

    public JwtService(
        ObjectMapper objectMapper,
        String secret,
        String serviceSecret,
        Duration lifetime,
        Clock clock
    ) {
        validateSecret(secret, "JWT");
        validateSecret(serviceSecret, "Service JWT");
        if (MessageDigest.isEqual(
            secret.getBytes(StandardCharsets.UTF_8),
            serviceSecret.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException(
                "User and service JWT secrets must be different");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
        this.serviceSecret =
            serviceSecret.getBytes(StandardCharsets.UTF_8).clone();
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public String issue(Account account) {
        Instant now = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", Integer.toString(account.id()));
        claims.put("email", account.email());
        claims.put("username", account.username());
        claims.put("role", account.role());
        claims.put("tokenVersion", account.tokenVersion());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(lifetime).getEpochSecond());
        return encode(claims);
    }

    public String issueAdmin(String username) {
        Instant now = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "0");
        claims.put("email", "");
        claims.put("username", username);
        claims.put("role", "ADMIN");
        claims.put("tokenVersion", 0);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(Duration.ofHours(24)).getEpochSecond());
        return encode(claims);
    }

    public String issueService(String serviceName) {
        Instant now = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "0");
        claims.put("email", "");
        claims.put("username", serviceName);
        claims.put("role", "SERVICE");
        claims.put("aud", "sysudate-ai");
        claims.put("tokenVersion", 0);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(Duration.ofMinutes(2)).getEpochSecond());
        return encode(claims, serviceSecret);
    }

    public CurrentAccount verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            Map<String, Object> claims = objectMapper.readValue(
                URL_DECODER.decode(parts[1]),
                new TypeReference<>() {
                });
            String role = (String) claims.get("role");
            byte[] signingSecret = "SERVICE".equals(role)
                ? serviceSecret
                : secret;
            byte[] expected = sign(
                parts[0] + "." + parts[1],
                signingSecret);
            byte[] supplied = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, supplied)) {
                return null;
            }
            if ("SERVICE".equals(role)
                && !"sysudate-ai".equals(claims.get("aud"))) {
                return null;
            }
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (clock.instant().getEpochSecond() >= expiresAt) {
                return null;
            }
            return new CurrentAccount(
                Integer.parseInt((String) claims.get("sub")),
                (String) claims.get("email"),
                (String) claims.get("username"),
                role,
                ((Number) claims.get("tokenVersion")).intValue());
        } catch (RuntimeException | IOException exception) {
            return null;
        }
    }

    private String encode(Map<String, Object> claims) {
        return encode(claims, secret);
    }

    private String encode(
        Map<String, Object> claims,
        byte[] signingSecret
    ) {
        try {
            String header = URL_ENCODER.encodeToString(
                objectMapper.writeValueAsBytes(Map.of(
                    "alg", "HS256",
                    "typ", "JWT")));
            String payload = URL_ENCODER.encodeToString(
                objectMapper.writeValueAsBytes(claims));
            String input = header + "." + payload;
            return input + "." + URL_ENCODER.encodeToString(
                sign(input, signingSecret));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode JWT", exception);
        }
    }

    private byte[] sign(String input, byte[] signingSecret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, ALGORITHM));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private void validateSecret(String value, String name) {
        if (value == null
            || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                name + " secret must contain at least 32 bytes");
        }
    }
}
