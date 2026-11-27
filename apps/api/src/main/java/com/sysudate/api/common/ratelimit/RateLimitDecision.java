package com.sysudate.api.common.ratelimit;

public record RateLimitDecision(
    boolean allowed,
    long remaining,
    long retryAfterSeconds
) {
    public RateLimitDecision {
        if (remaining < 0) {
            throw new IllegalArgumentException("Remaining must not be negative");
        }
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException(
                "Retry delay must not be negative");
        }
        if (allowed && retryAfterSeconds != 0) {
            throw new IllegalArgumentException(
                "Allowed decisions cannot require a retry delay");
        }
    }
}
