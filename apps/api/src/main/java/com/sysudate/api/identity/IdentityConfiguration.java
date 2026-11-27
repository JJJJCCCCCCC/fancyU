package com.sysudate.api.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.common.ratelimit.RateLimitService;
import com.sysudate.api.common.ratelimit.RedisRateLimitService;
import com.sysudate.api.common.security.JwtService;
import com.sysudate.api.identity.verification.RedisVerificationCodeStore;
import com.sysudate.api.identity.verification.VerificationCodeStore;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtService jwtService(
        ObjectMapper objectMapper,
        @Value("${sysudate.security.jwt-secret}") String secret,
        @Value("${sysudate.security.service-jwt-secret}")
        String serviceSecret,
        @Value("${sysudate.security.access-token-ttl:30d}") Duration lifetime
    ) {
        return new JwtService(
            objectMapper,
            secret,
            serviceSecret,
            lifetime,
            Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty("spring.data.redis.host")
    VerificationCodeStore verificationCodeStore(
        StringRedisTemplate redis,
        @Value("${sysudate.security.verification-hmac-secret}")
        String hmacSecret
    ) {
        return new RedisVerificationCodeStore(redis, hmacSecret);
    }

    @Bean
    @ConditionalOnProperty("spring.data.redis.host")
    RateLimitService rateLimitService(StringRedisTemplate redis) {
        return new RedisRateLimitService(redis);
    }
}
