package com.sysudate.api.identity;

import com.sysudate.api.identity.AccountRepository.Account;
import com.sysudate.api.identity.AccountRepository.Preferences;
import com.sysudate.api.identity.AccountRepository.SurveyBinding;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcAccountRepository implements AccountRepository {
    private final JdbcTemplate jdbc;

    public JdbcAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findByLogin(String login) {
        return jdbc.query(
            """
                SELECT id, email, username, password_hash, role, token_version,
                       created_at, last_login_at
                FROM accounts
                WHERE LOWER(email) = LOWER(?) OR username = ?
                LIMIT 1
                """,
            this::mapAccount,
            login,
            login).stream().findFirst();
    }

    @Override
    public Optional<Account> findById(int accountId) {
        return jdbc.query(
            """
                SELECT id, email, username, password_hash, role, token_version,
                       created_at, last_login_at
                FROM accounts
                WHERE id = ?
                """,
            this::mapAccount,
            accountId).stream().findFirst();
    }

    @Override
    public Account create(
        String email,
        String username,
        String passwordHash
    ) {
        jdbc.update(
            """
                INSERT INTO accounts (
                    email, username, password_hash, email_verified
                ) VALUES (?, ?, ?, TRUE)
                """,
            email,
            username,
            passwordHash);
        return findByLogin(email).orElseThrow();
    }

    @Override
    public boolean usernameExists(String username) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE username = ?",
            Integer.class,
            username);
        return count != null && count > 0;
    }

    @Override
    public void updateLastLogin(int accountId) {
        jdbc.update(
            "UPDATE accounts SET last_login_at = CURRENT_TIMESTAMP(3) "
                + "WHERE id = ?",
            accountId);
    }

    @Override
    public void updatePasswordAndRevokeTokens(
        int accountId,
        String passwordHash
    ) {
        jdbc.update(
            """
                UPDATE accounts
                SET password_hash = ?, token_version = token_version + 1
                WHERE id = ?
                """,
            passwordHash,
            accountId);
    }

    @Override
    public void updateUsername(int accountId, String username) {
        jdbc.update(
            "UPDATE accounts SET username = ? WHERE id = ?",
            username,
            accountId);
    }

    @Override
    public boolean hasTokenVersion(int accountId, int tokenVersion) {
        Integer count = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM accounts
                WHERE id = ? AND token_version = ?
                """,
            Integer.class,
            accountId,
            tokenVersion);
        return count != null && count == 1;
    }

    @Override
    public List<String> serials(int accountId) {
        return jdbc.queryForList(
            """
                SELECT serial_number
                FROM users
                WHERE account_id = ?
                UNION ALL
                SELECT serial_number
                FROM friend_survey
                WHERE account_id = ?
                """,
            String.class,
            accountId,
            accountId);
    }

    @Override
    public String contactEmail(int accountId) {
        List<String> emails = jdbc.query(
            """
                SELECT contact_email
                FROM (
                    SELECT contact_email, 1 AS priority
                    FROM users
                    WHERE account_id = ?
                    UNION ALL
                    SELECT contact_email, 2 AS priority
                    FROM friend_survey
                    WHERE account_id = ?
                ) contacts
                WHERE contact_email IS NOT NULL
                ORDER BY priority
                LIMIT 1
                """,
            (resultSet, rowNumber) -> resultSet.getString(1),
            accountId,
            accountId);
        return emails.isEmpty() ? null : emails.getFirst();
    }

    @Override
    public void updateContactEmail(int accountId, String contactEmail) {
        jdbc.update(
            "UPDATE users SET contact_email = ? WHERE account_id = ?",
            contactEmail,
            accountId);
        jdbc.update(
            "UPDATE friend_survey SET contact_email = ? WHERE account_id = ?",
            contactEmail,
            accountId);
    }

    @Override
    public Preferences preferences(int accountId) {
        return jdbc.query(
            """
                SELECT join_matching, join_friend_matching
                FROM account_preferences
                WHERE account_id = ?
                """,
            (resultSet, rowNumber) -> new Preferences(
                resultSet.getBoolean("join_matching"),
                resultSet.getBoolean("join_friend_matching")),
            accountId).stream().findFirst().orElse(new Preferences(true, true));
    }

    @Override
    public void setPreference(int accountId, String field, boolean value) {
        if (!List.of(
            "join_matching",
            "join_friend_matching").contains(field)) {
            throw new IllegalArgumentException("Unsupported preference");
        }
        jdbc.update(
            """
                INSERT INTO account_preferences (account_id, %s)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE %s = VALUES(%s)
                """.formatted(field, field, field),
            accountId,
            value);
    }

    @Override
    public Optional<SurveyBinding> findSurveyBinding(String serialNumber) {
        List<SurveyBinding> bindings = jdbc.query(
            """
                SELECT source_table, serial_number, email, contact_email,
                       account_id
                FROM (
                    SELECT 'users' AS source_table, serial_number, email,
                           contact_email, account_id
                    FROM users
                    WHERE serial_number = ?
                    UNION ALL
                    SELECT 'friend_survey' AS source_table, serial_number,
                           email, contact_email, account_id
                    FROM friend_survey
                    WHERE serial_number = ?
                ) surveys
                LIMIT 1
                """,
            (resultSet, rowNumber) -> new SurveyBinding(
                resultSet.getString("source_table"),
                resultSet.getString("serial_number"),
                resultSet.getString("email"),
                resultSet.getString("contact_email"),
                (Integer) resultSet.getObject("account_id")),
            serialNumber,
            serialNumber);
        return bindings.stream().findFirst();
    }

    @Override
    public void bindSurvey(int accountId, SurveyBinding binding) {
        String table = switch (binding.table()) {
            case "users" -> "users";
            case "friend_survey" -> "friend_survey";
            default -> throw new IllegalArgumentException(
                "Unsupported survey table");
        };
        jdbc.update(
            "UPDATE " + table
                + " SET account_id = ? WHERE serial_number = ? "
                + "AND account_id IS NULL",
            accountId,
            binding.serialNumber());
    }

    @Override
    public void deleteAccount(int accountId) {
        jdbc.update(
            "DELETE FROM account_preferences WHERE account_id = ?",
            accountId);
        jdbc.update(
            "DELETE FROM match_history "
                + "WHERE account_id = ? OR match_account_id = ?",
            accountId,
            accountId);
        jdbc.update(
            "UPDATE users SET account_id = NULL WHERE account_id = ?",
            accountId);
        jdbc.update(
            "UPDATE friend_survey SET account_id = NULL WHERE account_id = ?",
            accountId);
        jdbc.update("DELETE FROM accounts WHERE id = ?", accountId);
    }

    private Account mapAccount(ResultSet resultSet, int rowNumber)
        throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp lastLoginAt = resultSet.getTimestamp("last_login_at");
        return new Account(
            resultSet.getInt("id"),
            resultSet.getString("email"),
            resultSet.getString("username"),
            resultSet.getString("password_hash"),
            resultSet.getString("role"),
            resultSet.getInt("token_version"),
            createdAt == null ? null : createdAt.toInstant(),
            lastLoginAt == null ? null : lastLoginAt.toInstant());
    }
}
