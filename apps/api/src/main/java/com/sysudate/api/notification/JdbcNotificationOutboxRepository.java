package com.sysudate.api.notification;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcNotificationOutboxRepository
    implements NotificationOutboxRepository {
    private static final String LOCK_PENDING_SQL = """
        SELECT id, event_type, aggregate_id, payload, attempt_count,
               available_at, created_at
        FROM notification_outbox
        WHERE status = 'PENDING'
          AND available_at <= CURRENT_TIMESTAMP(3)
        ORDER BY created_at, id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Duration leaseDuration;
    private final long leaseMicroseconds;

    public JdbcNotificationOutboxRepository(
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager,
        Duration leaseDuration
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
            transactionManager,
            "transactionManager must not be null"));
        this.leaseDuration = leaseDuration;
        this.leaseMicroseconds = leaseMicroseconds(leaseDuration);
    }

    @Override
    public void enqueue(NotificationEvent event) {
        NotificationEvent validated =
            Objects.requireNonNull(event, "event must not be null");
        jdbc.update(
            """
                INSERT INTO notification_outbox (
                    id, event_type, aggregate_id, payload, status,
                    attempt_count, available_at, created_at
                ) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """,
            validated.id(),
            validated.eventType(),
            validated.aggregateId(),
            validated.payload(),
            validated.attemptCount(),
            Timestamp.from(validated.availableAt()),
            Timestamp.from(validated.createdAt()));
    }

    @Override
    public List<NotificationEvent> lockPending(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        List<NotificationEvent> events = transaction.execute(status -> {
            List<NotificationEvent> selected = jdbc.query(
                LOCK_PENDING_SQL,
                (resultSet, rowNumber) -> new NotificationEvent(
                    resultSet.getString("id"),
                    resultSet.getString("event_type"),
                    resultSet.getString("aggregate_id"),
                    resultSet.getString("payload"),
                    resultSet.getInt("attempt_count"),
                    resultSet.getTimestamp("available_at").toInstant(),
                    resultSet.getTimestamp("created_at").toInstant()),
                batchSize);
            lease(selected);
            return selected.stream()
                .map(this::withLeaseAttempt)
                .toList();
        });
        return Objects.requireNonNull(
            events,
            "Transaction returned no outbox result");
    }

    @Override
    public void markPublished(String eventId) {
        String validatedId = requireText(eventId, "eventId");
        jdbc.update(
            """
                UPDATE notification_outbox
                SET status = 'PUBLISHED',
                    published_at = COALESCE(
                        published_at,
                        CURRENT_TIMESTAMP(3))
                WHERE id = ?
                  AND status IN ('PENDING', 'PUBLISHED')
                """,
            validatedId);
    }

    private void lease(List<NotificationEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
            """
                UPDATE notification_outbox
                SET attempt_count = attempt_count + 1,
                    available_at = TIMESTAMPADD(
                        MICROSECOND,
                        ?,
                        CURRENT_TIMESTAMP(3))
                WHERE id = ?
                  AND status = 'PENDING'
                """,
            events,
            events.size(),
            (PreparedStatement statement, NotificationEvent event) -> {
                statement.setLong(1, leaseMicroseconds);
                statement.setString(2, event.id());
            });
    }

    private NotificationEvent withLeaseAttempt(NotificationEvent event) {
        return new NotificationEvent(
            event.id(),
            event.eventType(),
            event.aggregateId(),
            event.payload(),
            event.attemptCount() + 1,
            Instant.now().plus(leaseDuration),
            event.createdAt());
    }

    private long leaseMicroseconds(Duration leaseDuration) {
        if (leaseDuration == null
            || leaseDuration.isZero()
            || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                "leaseDuration must be positive");
        }
        try {
            long millis = leaseDuration.toMillis();
            if (millis <= 0) {
                throw new IllegalArgumentException(
                    "leaseDuration must be at least one millisecond");
            }
            return Math.multiplyExact(millis, 1_000);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                "leaseDuration is too large",
                exception);
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank");
        }
        return value.trim();
    }
}
