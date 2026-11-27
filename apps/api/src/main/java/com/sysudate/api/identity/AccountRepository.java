package com.sysudate.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByLogin(String login);

    Optional<Account> findById(int accountId);

    Account create(String email, String username, String passwordHash);

    boolean usernameExists(String username);

    void updateLastLogin(int accountId);

    void updatePasswordAndRevokeTokens(int accountId, String passwordHash);

    void updateUsername(int accountId, String username);

    boolean hasTokenVersion(int accountId, int tokenVersion);

    List<String> serials(int accountId);

    String contactEmail(int accountId);

    void updateContactEmail(int accountId, String contactEmail);

    Preferences preferences(int accountId);

    void setPreference(int accountId, String field, boolean value);

    Optional<SurveyBinding> findSurveyBinding(String serialNumber);

    void bindSurvey(int accountId, SurveyBinding binding);

    void deleteAccount(int accountId);

    record Account(
        int id,
        String email,
        String username,
        String passwordHash,
        String role,
        int tokenVersion,
        Instant createdAt,
        Instant lastLoginAt
    ) {
    }

    record Preferences(
        boolean joinMatching,
        boolean joinFriendMatching
    ) {
    }

    record SurveyBinding(
        String table,
        String serialNumber,
        String email,
        String contactEmail,
        Integer accountId
    ) {
    }
}
