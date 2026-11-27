package com.sysudate.api.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcNotificationDeliveryRepository
    implements NotificationDeliveryRepository {
    private final JdbcTemplate jdbc;

    public JdbcNotificationDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean begin(String eventId, String destinationHash) {
        jdbc.update(
            """
                INSERT IGNORE INTO notification_delivery (
                    id, idempotency_key, channel, destination_hash, status
                ) VALUES (?, ?, 'EMAIL', ?, 'PENDING')
                """,
            eventId,
            eventId,
            destinationHash);
        String status = jdbc.queryForObject(
            "SELECT status FROM notification_delivery WHERE id = ?",
            String.class,
            eventId);
        return !"DELIVERED".equals(status);
    }

    @Override
    public void delivered(String eventId) {
        jdbc.update(
            """
                UPDATE notification_delivery
                SET status = 'DELIVERED', failure_reason = NULL,
                    delivered_at = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """,
            eventId);
    }

    @Override
    public void failed(String eventId, String failureCategory) {
        jdbc.update(
            """
                UPDATE notification_delivery
                SET status = 'FAILED', failure_reason = ?
                WHERE id = ?
                """,
            failureCategory,
            eventId);
    }
}
