package com.sysudate.api.identity.verification;

import java.time.Duration;

public interface VerificationCodeStore {
    void save(String email, String code, Duration ttl);

    boolean matches(String email, String code);

    boolean consume(String email, String code);
}
