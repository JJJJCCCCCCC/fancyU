package com.sysudate.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String USER_SECRET =
        "test-user-jwt-secret-that-is-at-least-32-bytes";
    private static final String SERVICE_SECRET =
        "test-service-jwt-secret-that-is-at-least-32-bytes";

    @Test
    void issuesUserAndServiceTokensWithSeparateTrustBoundaries() {
        JwtService service = jwtService(SERVICE_SECRET);

        CurrentAccount admin = service.verify(service.issueAdmin("admin"));
        CurrentAccount internal =
            service.verify(service.issueService("sysudate-api"));

        assertThat(admin.role()).isEqualTo("ADMIN");
        assertThat(internal.role()).isEqualTo("SERVICE");
        assertThat(jwtService(
            "another-service-secret-that-is-at-least-32-bytes"
        ).verify(service.issueService("sysudate-api"))).isNull();
    }

    @Test
    void rejectsSharedUserAndServiceSecret() {
        assertThatThrownBy(() -> jwtService(USER_SECRET))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be different");
    }

    private JwtService jwtService(String serviceSecret) {
        return new JwtService(
            new ObjectMapper(),
            USER_SECRET,
            serviceSecret,
            Duration.ofDays(30),
            Clock.systemUTC());
    }
}
