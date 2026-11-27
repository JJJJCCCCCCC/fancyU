package com.sysudate.api.identity.verification;

import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.ratelimit.RateLimitDecision;
import com.sysudate.api.common.ratelimit.RateLimitService;
import com.sysudate.api.identity.AccountService;
import com.sysudate.api.notification.NotificationEvent;
import com.sysudate.api.notification.NotificationOutboxRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty({
    "spring.datasource.url",
    "spring.data.redis.host"
})
public class VerificationCodeService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);

    private final VerificationCodeStore codes;
    private final NotificationOutboxRepository outbox;
    private final RateLimitService rateLimits;
    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(
        VerificationCodeStore codes,
        NotificationOutboxRepository outbox,
        RateLimitService rateLimits
    ) {
        this.codes = codes;
        this.outbox = outbox;
        this.rateLimits = rateLimits;
    }

    @Transactional
    public void send(String rawEmail) {
        String email = AccountService.normalizeAndValidateEmail(rawEmail);
        RateLimitDecision decision = rateLimits.check(
            "verification-send:" + email,
            5,
            SEND_WINDOW);
        if (!decision.allowed()) {
            throw new LegacyApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                429,
                "验证码发送过于频繁，请稍后再试");
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        codes.save(email, code, CODE_TTL);
        outbox.enqueue(NotificationEvent.emailVerification(
            email,
            code,
            requestId()));
    }

    public boolean matches(String rawEmail, String code) {
        return codes.matches(normalize(rawEmail), code);
    }

    public boolean consume(String rawEmail, String code) {
        return codes.consume(normalize(rawEmail), code);
    }

    private String normalize(String rawEmail) {
        if (rawEmail == null
            || !rawEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new LegacyApiException(
                HttpStatus.BAD_REQUEST,
                400,
                "邮箱格式不正确");
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? UUID.randomUUID().toString() : value;
    }
}
