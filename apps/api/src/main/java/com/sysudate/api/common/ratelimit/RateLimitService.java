package com.sysudate.api.common.ratelimit;

import java.time.Duration;

public interface RateLimitService {
    RateLimitDecision check(String bucket, int limit, Duration window);
}
