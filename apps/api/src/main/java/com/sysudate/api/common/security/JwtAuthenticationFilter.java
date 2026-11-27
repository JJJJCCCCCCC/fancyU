package com.sysudate.api.common.security;

import com.sysudate.api.identity.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AccountRepository accounts;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        AccountRepository accounts
    ) {
        this.jwtService = jwtService;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            CurrentAccount principal = jwtService.verify(
                authorization.substring(7));
            if (principal != null && tokenIsActive(principal)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(
                        "ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(
                    authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean tokenIsActive(CurrentAccount principal) {
        if ("ADMIN".equals(principal.role())
            || "SERVICE".equals(principal.role())) {
            return principal.accountId() == 0;
        }
        return accounts.hasTokenVersion(
            principal.accountId(),
            principal.tokenVersion());
    }
}
