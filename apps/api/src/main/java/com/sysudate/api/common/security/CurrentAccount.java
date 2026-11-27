package com.sysudate.api.common.security;

public record CurrentAccount(
    int accountId,
    String email,
    String username,
    String role,
    int tokenVersion
) {
}
