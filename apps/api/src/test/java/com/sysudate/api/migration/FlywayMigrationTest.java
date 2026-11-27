package com.sysudate.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sysudate.api.support.MySqlContainerTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest extends MySqlContainerTest {
    private static final List<String> INFRASTRUCTURE_TABLES = List.of(
        "audit_log",
        "notification_delivery",
        "notification_outbox");

    @Test
    void appliesVersionedInfrastructureSchemaIdempotently() {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load();

        MigrateResult firstMigration = flyway.migrate();

        assertThat(firstMigration.success).isTrue();
        assertThat(successfulMigrationCount("1")).isEqualTo(1);
        assertThat(existingInfrastructureTables())
            .containsExactlyElementsOf(INFRASTRUCTURE_TABLES);
        assertThat(columnNames("notification_outbox")).containsExactly(
            "id",
            "event_type",
            "aggregate_id",
            "payload",
            "status",
            "attempt_count",
            "available_at",
            "created_at",
            "published_at");
        assertThat(columnNames("notification_delivery")).containsExactly(
            "id",
            "idempotency_key",
            "channel",
            "destination_hash",
            "status",
            "failure_reason",
            "created_at",
            "delivered_at");
        assertThat(columnNames("audit_log")).containsExactly(
            "id",
            "actor_type",
            "actor_id",
            "action",
            "resource_type",
            "resource_id",
            "request_id",
            "metadata",
            "created_at");
        assertColumnType("notification_outbox", "payload", "json");
        assertColumnType("audit_log", "metadata", "json");
        assertTimestampPrecision("notification_outbox", "available_at", 3);
        assertTimestampPrecision("notification_outbox", "created_at", 3);
        assertTimestampPrecision("notification_outbox", "published_at", 3);
        assertTimestampPrecision("notification_delivery", "created_at", 3);
        assertTimestampPrecision("notification_delivery", "delivered_at", 3);
        assertTimestampPrecision("audit_log", "created_at", 3);
        assertThat(indexColumns("notification_outbox", "idx_outbox_pending"))
            .containsExactly(
                new IndexColumn("idx_outbox_pending", "status", 1, true),
                new IndexColumn("idx_outbox_pending", "available_at", 2, true));
        assertThat(indexColumns("notification_delivery", "idempotency_key"))
            .containsExactly(
                new IndexColumn("idempotency_key", "idempotency_key", 1, false));
        assertThat(indexColumns("audit_log", "idx_audit_actor_created"))
            .containsExactly(
                new IndexColumn(
                    "idx_audit_actor_created",
                    "actor_id",
                    1,
                    true),
                new IndexColumn(
                    "idx_audit_actor_created",
                    "created_at",
                    2,
                    true));
        assertThat(indexColumns("audit_log", "idx_audit_action_created"))
            .containsExactly(
                new IndexColumn(
                    "idx_audit_action_created",
                    "action",
                    1,
                    true),
                new IndexColumn(
                    "idx_audit_action_created",
                    "created_at",
                    2,
                    true));
        assertCheckConstraints();
        assertThat(indexColumns("audit_log", "idx_audit_request_id"))
            .containsExactly(
                new IndexColumn("idx_audit_request_id", "request_id", 1, true));

        MigrateResult secondMigration = flyway.migrate();

        assertThat(secondMigration.success).isTrue();
        assertThat(secondMigration.migrationsExecuted).isZero();
        assertThat(existingInfrastructureTables())
            .containsExactlyElementsOf(INFRASTRUCTURE_TABLES);
    }

    private List<String> existingInfrastructureTables() {
        return jdbc.queryForList(
            """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (?, ?, ?)
                ORDER BY table_name
                """,
            String.class,
            INFRASTRUCTURE_TABLES.toArray());
    }

    private Integer successfulMigrationCount(String version) {
        return jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = ?
                  AND success = 1
                """,
            Integer.class,
            version);
    }

    private List<String> columnNames(String tableName) {
        return jdbc.queryForList(
            """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                ORDER BY ordinal_position
                """,
            String.class,
            tableName);
    }

    private void assertColumnType(
        String tableName,
        String columnName,
        String expectedType
    ) {
        String actualType = jdbc.queryForObject(
            """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
            String.class,
            tableName,
            columnName);

        assertThat(actualType).isEqualTo(expectedType);
    }

    private void assertTimestampPrecision(
        String tableName,
        String columnName,
        int expectedPrecision
    ) {
        TimestampColumn actualColumn = jdbc.queryForObject(
            """
                SELECT data_type, datetime_precision
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
            (resultSet, rowNumber) -> new TimestampColumn(
                resultSet.getString("data_type"),
                resultSet.getInt("datetime_precision")),
            tableName,
            columnName);

        assertThat(actualColumn)
            .isEqualTo(new TimestampColumn("timestamp", expectedPrecision));
    }

    private List<IndexColumn> indexColumns(String tableName, String indexName) {
        return jdbc.query(
            """
                SELECT index_name, column_name, seq_in_index, non_unique
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                ORDER BY seq_in_index
                """,
            (resultSet, rowNumber) -> new IndexColumn(
                resultSet.getString("index_name"),
                resultSet.getString("column_name"),
                resultSet.getInt("seq_in_index"),
                resultSet.getBoolean("non_unique")),
            tableName,
            indexName);
    }

    private void assertCheckConstraints() {
        List<CheckConstraint> outboxConstraints =
            checkConstraints("notification_outbox");
        assertThat(outboxConstraints)
            .extracting(CheckConstraint::constraintName)
            .containsExactly(
                "chk_outbox_attempt_count_non_negative",
                "chk_outbox_status");
        assertThat(outboxConstraints)
            .filteredOn(constraint -> constraint.constraintName().equals(
                "chk_outbox_attempt_count_non_negative"))
            .singleElement()
            .satisfies(constraint -> assertThat(constraint.checkClause())
                .containsPattern(
                    "(?i).*[`]?attempt_count[`]?\\s*>=\\s*0.*"));
        assertThat(outboxConstraints)
            .filteredOn(constraint -> constraint.constraintName().equals(
                "chk_outbox_status"))
            .singleElement()
            .satisfies(constraint -> assertThat(
                normalizedCheckClause(constraint))
                .containsPattern("(?i).*[`]?status[`]?\\s+in\\s*\\(.*")
                .contains("'PENDING'", "'PUBLISHED'", "'FAILED'"));

        List<CheckConstraint> deliveryConstraints =
            checkConstraints("notification_delivery");
        assertThat(deliveryConstraints)
            .extracting(CheckConstraint::constraintName)
            .containsExactly("chk_delivery_status");
        assertThat(deliveryConstraints)
            .singleElement()
            .satisfies(constraint -> assertThat(
                normalizedCheckClause(constraint))
                .containsPattern("(?i).*[`]?status[`]?\\s+in\\s*\\(.*")
                .contains("'PENDING'", "'DELIVERED'", "'FAILED'"));
    }

    private String normalizedCheckClause(CheckConstraint constraint) {
        return constraint.checkClause().replace("\\'", "'");
    }

    private List<CheckConstraint> checkConstraints(String tableName) {
        return jdbc.query(
            """
                SELECT checks.constraint_name, checks.check_clause
                FROM information_schema.check_constraints checks
                JOIN information_schema.table_constraints tables
                  ON tables.constraint_schema = checks.constraint_schema
                 AND tables.constraint_name = checks.constraint_name
                WHERE tables.table_schema = DATABASE()
                  AND tables.table_name = ?
                  AND tables.constraint_type = 'CHECK'
                ORDER BY checks.constraint_name
                """,
            (resultSet, rowNumber) -> new CheckConstraint(
                resultSet.getString("constraint_name"),
                resultSet.getString("check_clause")),
            tableName);
    }

    private record IndexColumn(
        String indexName,
        String columnName,
        int sequence,
        boolean nonUnique
    ) {
    }

    private record TimestampColumn(String dataType, int precision) {
    }

    private record CheckConstraint(
        String constraintName,
        String checkClause
    ) {
    }
}
