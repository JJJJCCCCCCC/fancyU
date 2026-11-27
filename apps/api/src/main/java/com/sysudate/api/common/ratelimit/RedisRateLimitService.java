package com.sysudate.api.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisRateLimitService implements RateLimitService {
    private static final String KEY_PREFIX = "rate-limit:";
    private static final DefaultRedisScript<List> CHECK_SCRIPT =
        new DefaultRedisScript<>(
            """
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then
                    redis.call('PEXPIRE', KEYS[1], ARGV[1])
                end
                local ttl = redis.call('PTTL', KEYS[1])
                return {count, ttl}
                """,
            List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitService(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    @Override
    public RateLimitDecision check(
        String bucket,
        int limit,
        Duration window
    ) {
        String normalizedBucket = normalizeBucket(bucket);
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        long windowMillis = windowMillis(window);

        List<?> result = redis.execute(
            CHECK_SCRIPT,
            List.of(keyFor(normalizedBucket)),
            Long.toString(windowMillis));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException(
                "Redis returned an invalid rate-limit result");
        }

        long count = numberAt(result, 0);
        long ttlMillis = numberAt(result, 1);
        if (count <= 0 || ttlMillis < 0) {
            throw new IllegalStateException(
                "Redis returned an invalid rate-limit state");
        }

        boolean allowed = count <= limit;
        long remaining = count >= limit ? 0 : limit - count;
        long retryAfterSeconds = allowed ? 0 : retrySeconds(ttlMillis);
        return new RateLimitDecision(
            allowed,
            remaining,
            retryAfterSeconds);
    }

    private String normalizeBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket must not be blank");
        }
        return bucket.trim();
    }

    private long windowMillis(Duration window) {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Window must be positive");
        }
        try {
            long millis = window.toMillis();
            if (millis <= 0) {
                throw new IllegalArgumentException(
                    "Window must be at least one millisecond");
            }
            return millis;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Window is too large", exception);
        }
    }

    private String keyFor(String bucket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                bucket.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "Unable to hash rate-limit bucket",
                exception);
        }
    }

    private long numberAt(List<?> result, int index) {
        Object value = result.get(index);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException(
                "Redis returned a non-numeric rate-limit result");
        }
        return number.longValue();
    }

    private long retrySeconds(long ttlMillis) {
        long seconds = ttlMillis / 1_000;
        if (ttlMillis % 1_000 != 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }
}
