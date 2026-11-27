package com.sysudate.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.support.MySqlContainerTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class JdbcNotificationOutboxRepositoryTest extends MySqlContainerTest {
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationOutboxRepository repository;

    @BeforeEach
    void createRepository() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        jdbc.update("DELETE FROM notification_outbox");
        repository = new JdbcNotificationOutboxRepository(
            jdbc,
            new DataSourceTransactionManager(dataSource),
            LEASE_DURATION);
    }

    @Test
    void enqueuesLocksAndMarksEventPublished() {
        NotificationEvent event = NotificationEvent.emailVerification(
            "student@sysu.edu.cn",
            "123456",
            "request-1");

        repository.enqueue(event);
        List<NotificationEvent> pending = repository.lockPending(10);

        assertThat(pending)
            .extracting(NotificationEvent::id)
            .containsExactly(event.id());
        assertThat(pending.getFirst().attemptCount()).isEqualTo(1);

        repository.markPublished(event.id());

        assertThat(repository.lockPending(10)).isEmpty();
        assertThat(jdbc.queryForObject(
            "SELECT status FROM notification_outbox WHERE id = ?",
            String.class,
            event.id()))
            .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject(
            "SELECT published_at IS NOT NULL "
                + "FROM notification_outbox WHERE id = ?",
            Boolean.class,
            event.id()))
            .isTrue();
    }

    @Test
    void storesStructuredPayloadWithoutStringConcatenationDamage()
        throws Exception {
        NotificationEvent event = NotificationEvent.emailVerification(
            "o'hara@sysu.edu.cn",
            "12\"3456",
            "request-quote");

        repository.enqueue(event);

        String payload = jdbc.queryForObject(
            "SELECT payload FROM notification_outbox WHERE id = ?",
            String.class,
            event.id());
        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.path("destination").asText())
            .isEqualTo("o'hara@sysu.edu.cn");
        assertThat(json.path("template").asText())
            .isEqualTo("email-verification");
        assertThat(json.path("variables").path("code").asText())
            .isEqualTo("12\"3456");
        assertThat(json.path("variables").path("requestId").asText())
            .isEqualTo("request-quote");
        assertThat(event.toString())
            .doesNotContain("o'hara@sysu.edu.cn")
            .doesNotContain("12\"3456")
            .contains("payload=<redacted>");
    }

    @Test
    void leasePreventsImmediateDuplicateDelivery() {
        NotificationEvent event = NotificationEvent.emailVerification(
            "student@sysu.edu.cn",
            "123456",
            "request-lease");
        repository.enqueue(event);

        assertThat(repository.lockPending(1))
            .extracting(NotificationEvent::id)
            .containsExactly(event.id());
        assertThat(repository.lockPending(1)).isEmpty();

        Instant availableAt = jdbc.queryForObject(
            "SELECT available_at FROM notification_outbox WHERE id = ?",
            (resultSet, rowNumber) ->
                resultSet.getTimestamp(1).toInstant(),
            event.id());
        assertThat(availableAt).isAfter(Instant.now());
    }

    @Test
    void returnsOldestAvailableEventsUpToBatchSize() throws Exception {
        NotificationEvent first = NotificationEvent.emailVerification(
            "first@sysu.edu.cn",
            "111111",
            "request-first");
        NotificationEvent second = NotificationEvent.emailVerification(
            "second@sysu.edu.cn",
            "222222",
            "request-second");
        repository.enqueue(first);
        Thread.sleep(5);
        repository.enqueue(second);

        assertThat(repository.lockPending(1))
            .extracting(NotificationEvent::id)
            .containsExactly(first.id());
        assertThat(repository.lockPending(1))
            .extracting(NotificationEvent::id)
            .containsExactly(second.id());
    }

    @Test
    void ignoresEventsThatAreNotAvailableYet() {
        NotificationEvent event = NotificationEvent.emailVerification(
            "student@sysu.edu.cn",
            "123456",
            "request-future");
        repository.enqueue(event);
        jdbc.update(
            "UPDATE notification_outbox "
                + "SET available_at = TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(3)) "
                + "WHERE id = ?",
            event.id());

        assertThat(repository.lockPending(10)).isEmpty();
    }

    @Test
    void markingPublishedIsIdempotent() {
        NotificationEvent event = NotificationEvent.emailVerification(
            "student@sysu.edu.cn",
            "123456",
            "request-idempotent");
        repository.enqueue(event);

        repository.markPublished(event.id());
        repository.markPublished(event.id());

        assertThat(jdbc.queryForObject(
            "SELECT status FROM notification_outbox WHERE id = ?",
            String.class,
            event.id()))
            .isEqualTo("PUBLISHED");
    }

    @Test
    void validatesRepositoryArguments() {
        NotificationEvent event = NotificationEvent.emailVerification(
            "student@sysu.edu.cn",
            "123456",
            "request-validation");

        assertThatThrownBy(() -> repository.enqueue(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.lockPending(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.markPublished(" "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.id()).isNotBlank();
    }
}
