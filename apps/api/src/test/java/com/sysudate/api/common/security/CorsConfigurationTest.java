package com.sysudate.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigurationTest {
    @Test
    void usesOnlyConfiguredOrigins() {
        CorsConfigurationSource source = new SecurityConfiguration()
            .corsConfigurationSource(
                "https://site.example, https://admin.example");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/account/me");

        CorsConfiguration configuration =
            source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(
            "https://site.example",
            "https://admin.example");
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowedHeaders()).contains(
            "Authorization",
            "Content-Type");
    }
}
