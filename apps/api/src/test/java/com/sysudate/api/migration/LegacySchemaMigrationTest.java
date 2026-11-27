package com.sysudate.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sysudate.api.support.MySqlContainerTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class LegacySchemaMigrationTest extends MySqlContainerTest {
    @Test
    void upgradesLegacyTablesWithoutLosingSurveyData() {
        jdbc.execute("""
            CREATE TABLE accounts (
                id INT AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(255) NOT NULL UNIQUE,
                username VARCHAR(50) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL
            )
            """);
        jdbc.execute("""
            CREATE TABLE users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                serial_number VARCHAR(50) NOT NULL UNIQUE,
                email VARCHAR(255),
                nickname VARCHAR(50)
            )
            """);
        jdbc.update(
            """
                INSERT INTO users (serial_number, email, nickname)
                VALUES (?, ?, ?)
                """,
            "SYSU-550E8400-E29B-41D4-A716-446655440000",
            "legacy@sysu.edu.cn",
            "legacy-user");

        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();

        flyway.migrate();

        assertThat(existingTables()).contains(
            "account_preferences",
            "complaints",
            "friend_survey",
            "match_history",
            "request_idempotency");
        assertThat(columnNames("accounts")).contains(
            "email_verified",
            "role",
            "token_version",
            "created_at",
            "last_login_at");
        assertThat(columnNames("users")).contains(
            "account_id",
            "contact_email",
            "display_self",
            "survey_type",
            "created_at",
            "updated_at");
        assertThat(jdbc.queryForObject(
            "SELECT nickname FROM users WHERE serial_number = ?",
            String.class,
            "SYSU-550E8400-E29B-41D4-A716-446655440000"))
            .isEqualTo("legacy-user");
    }

    private List<String> existingTables() {
        return jdbc.queryForList(
            """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                """,
            String.class);
    }

    private List<String> columnNames(String tableName) {
        return jdbc.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
            String.class,
            tableName);
    }
}
