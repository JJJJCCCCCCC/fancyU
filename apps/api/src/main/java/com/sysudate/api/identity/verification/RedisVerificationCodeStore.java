package com.sysudate.api.identity.verification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisVerificationCodeStore
    implements VerificationCodeStore {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String KEY_PREFIX = "verification-code:";
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT =
        new DefaultRedisScript<>(
            """
                local stored_digest = redis.call('GET', KEYS[1])
                if stored_digest and stored_digest == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """,
            Long.class);
    private static final DefaultRedisScript<Long> MATCH_SCRIPT =
        new DefaultRedisScript<>(
            """
                local stored_digest = redis.call('GET', KEYS[1])
                if stored_digest and stored_digest == ARGV[1] then
                    return 1
                end
                return 0
                """,
            Long.class);

    private final StringRedisTemplate redis;
    private final byte[] hmacSecret;

    public RedisVerificationCodeStore(
        StringRedisTemplate redis,
        String hmacSecret
    ) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException(
                "HMAC secret must not be blank");
        }

        byte[] secretBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                "HMAC secret must be at least 32 bytes");
        }
        this.hmacSecret = secretBytes.clone();
    }

    @Override
    public void save(String email, String code, Duration ttl) {
        String normalizedEmail = normalizeEmail(email);
        validateCode(code);
        validateTtl(ttl);

        redis.opsForValue().set(
            keyFor(normalizedEmail),
            digest("verification-value", normalizedEmail, code),
            ttl);
    }

    @Override
    public boolean matches(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        validateCode(code);

        Long matched = redis.execute(
            MATCH_SCRIPT,
            List.of(keyFor(normalizedEmail)),
            digest("verification-value", normalizedEmail, code));
        return Long.valueOf(1L).equals(matched);
    }

    @Override
    public boolean consume(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        validateCode(code);

        Long deleted = redis.execute(
            CONSUME_SCRIPT,
            List.of(keyFor(normalizedEmail)),
            digest("verification-value", normalizedEmail, code));
        return Long.valueOf(1L).equals(deleted);
    }

    private String keyFor(String normalizedEmail) {
        return KEY_PREFIX + digest("verification-key", normalizedEmail);
    }

    private String digest(String domain, String... fields) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            updateField(mac, domain);
            for (String field : fields) {
                updateField(mac, field);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(
                "Unable to calculate verification code digest",
                exception);
        }
    }

    private void updateField(Mac mac, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        mac.update(ByteBuffer.allocate(Integer.BYTES)
            .putInt(bytes.length)
            .array());
        mac.update(bytes);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                "Verification code must not be blank");
        }
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
    }
}
