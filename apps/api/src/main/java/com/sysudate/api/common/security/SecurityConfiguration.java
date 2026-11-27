package com.sysudate.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.identity.AccountRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    @ConditionalOnProperty("spring.datasource.url")
    JwtAuthenticationFilter jwtAuthenticationFilter(
        JwtService jwtService,
        AccountRepository accounts
    ) {
        return new JwtAuthenticationFilter(jwtService, accounts);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ObjectProvider<JwtAuthenticationFilter> jwtFilter,
        ObjectMapper objectMapper
    ) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/system/health",
                    "/actuator/health",
                    "/actuator/prometheus")
                .permitAll()
                .requestMatchers(
                    "/",
                    "/health",
                    "/favicon.ico",
                    "/account/login",
                    "/account/register",
                    "/account/bind",
                    "/account/reset-password",
                    "/account/check-username",
                    "/admin/login",
                    "/api/complaint")
                .permitAll()
                .requestMatchers("/admin/**", "/llm/**")
                .hasRole("ADMIN")
                .requestMatchers("/internal/ai/**")
                .hasRole("SERVICE")
                .anyRequest()
                .authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    objectMapper.writeValue(response.getOutputStream(), Map.of(
                        "code", 401,
                        "msg", "未登录或登录已过期"));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    objectMapper.writeValue(response.getOutputStream(), Map.of(
                        "code", 403,
                        "msg", "无权访问"));
                }))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        JwtAuthenticationFilter filter = jwtFilter.getIfAvailable();
        if (filter != null) {
            http.addFilterBefore(
                filter,
                UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${sysudate.security.cors-allowed-origins:}")
        String configuredOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
            Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Request-ID"));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
