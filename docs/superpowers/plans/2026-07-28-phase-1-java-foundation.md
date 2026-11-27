# Phase 1 Java Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a tested Spring Boot foundation for migrating the existing SYSUDate API without changing current frontend behavior or production data.

**Architecture:** Build a Java 21 Spring Boot modular monolith under `apps/api`. MySQL owns durable state, Redis owns verification and rate-limit state, RabbitMQ carries notification jobs, and Flyway owns every schema change. The legacy `fc-code` backend remains untouched as the behavioral reference during this phase.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Maven, Spring Web/Security/Validation/Actuator, MyBatis, Flyway, Redis, RabbitMQ, MySQL 8.4, JUnit 5, MockMvc, Testcontainers, Docker Compose

---

### Task 1: Bootstrap The Java Service

**Files:**
- Create: `apps/api/pom.xml`
- Create: `apps/api/src/main/java/com/sysudate/api/SysudateApiApplication.java`
- Create: `apps/api/src/main/resources/application.yml`
- Create: `apps/api/src/test/java/com/sysudate/api/SysudateApiApplicationTest.java`

- [x] **Step 1: Write the failing context test**

```java
package com.sysudate.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class SysudateApiApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [x] **Step 2: Run the test and verify the project does not exist yet**

Run:

```powershell
docker run --rm -v "${PWD}/apps/api:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: failure because `apps/api/pom.xml` does not exist.

- [x] **Step 3: Create the Spring Boot project**

Use a Spring Boot 3.5.3 parent, Java 21, and these dependencies:

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.5</version>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Create the application entry point:

```java
package com.sysudate.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SysudateApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SysudateApiApplication.class, args);
    }
}
```

- [x] **Step 4: Run the context test**

Run the Dockerized Maven command from Step 2.

Expected: `BUILD SUCCESS`.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "build: bootstrap Spring Boot API"
```

### Task 2: Add The API Error Contract And Request IDs

**Files:**
- Create: `apps/api/src/main/java/com/sysudate/api/common/api/ApiError.java`
- Create: `apps/api/src/main/java/com/sysudate/api/common/error/BusinessException.java`
- Create: `apps/api/src/main/java/com/sysudate/api/common/error/GlobalExceptionHandler.java`
- Create: `apps/api/src/main/java/com/sysudate/api/common/observability/RequestIdFilter.java`
- Create: `apps/api/src/main/java/com/sysudate/api/system/SystemHealthController.java`
- Create: `apps/api/src/main/java/com/sysudate/api/common/security/SecurityConfiguration.java`
- Create: `apps/api/src/test/java/com/sysudate/api/system/SystemHealthControllerTest.java`

- [x] **Step 1: Write the failing HTTP contract test**

```java
@WebMvcTest(SystemHealthController.class)
@Import({SecurityConfiguration.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class SystemHealthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void healthReturnsRequestId() throws Exception {
        mvc.perform(get("/api/v1/system/health").header("X-Request-Id", "test-request"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "test-request"))
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [x] **Step 2: Run the test**

Run:

```powershell
docker run --rm -v "${PWD}/apps/api:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=SystemHealthControllerTest test
```

Expected: compilation failure because the controller and support classes do not exist.

- [x] **Step 3: Implement the contract**

`ApiError` is an immutable record:

```java
public record ApiError(String code, String message, String requestId, List<FieldError> details) {
    public record FieldError(String field, String message) {}
}
```

`RequestIdFilter` accepts a valid `X-Request-Id` or generates a UUID, stores it in MDC, echoes it in the response, and clears MDC in `finally`.

`GlobalExceptionHandler` maps validation errors to HTTP 400, `BusinessException` to its declared status, and unexpected errors to HTTP 500 without exposing exception messages.

`SecurityConfiguration` permits `/api/v1/system/health` and `/actuator/health`; all other endpoints require authentication.

- [x] **Step 4: Run tests**

Run `mvn test` through the Maven container.

Expected: `BUILD SUCCESS`.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "feat: add API error and request ID contracts"
```

### Task 3: Add The Serial Number Value Object

**Files:**
- Create: `apps/api/src/main/java/com/sysudate/api/serial/domain/SerialNumber.java`
- Create: `apps/api/src/test/java/com/sysudate/api/serial/domain/SerialNumberTest.java`

- [x] **Step 1: Write failing domain tests**

```java
class SerialNumberTest {
    @Test
    void generatesUppercaseSysuSerial() {
        SerialNumber serial = SerialNumber.generate();
        assertThat(serial.value()).matches("SYSU-[0-9A-F-]{36}");
    }

    @Test
    void normalizesValidSerial() {
        SerialNumber serial = SerialNumber.parse(" sysu-550e8400-e29b-41d4-a716-446655440000 ");
        assertThat(serial.value()).isEqualTo("SYSU-550E8400-E29B-41D4-A716-446655440000");
    }

    @Test
    void rejectsMalformedSerial() {
        assertThatThrownBy(() -> SerialNumber.parse("SYSU-123"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [x] **Step 2: Run the tests**

Expected: compilation failure because `SerialNumber` does not exist.

- [x] **Step 3: Implement the value object**

```java
public record SerialNumber(String value) {
    private static final Pattern PATTERN =
        Pattern.compile("^SYSU-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");

    public SerialNumber {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid serial number");
        }
    }

    public static SerialNumber generate() {
        return new SerialNumber("SYSU-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
    }

    public static SerialNumber parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Invalid serial number");
        return new SerialNumber(raw.trim().toUpperCase(Locale.ROOT));
    }
}
```

- [x] **Step 4: Run `SerialNumberTest`**

Expected: all three tests pass.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "feat: model immutable serial numbers"
```

### Task 4: Add Flyway And The Infrastructure Schema

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V1__infrastructure.sql`
- Create: `apps/api/src/test/java/com/sysudate/api/support/MySqlContainerTest.java`
- Create: `apps/api/src/test/java/com/sysudate/api/migration/FlywayMigrationTest.java`

- [x] **Step 1: Write the failing migration test**

```java
class FlywayMigrationTest extends MySqlContainerTest {
    @Test
    void appliesAllMigrationsToEmptyDatabase() {
        Flyway flyway = Flyway.configure()
            .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = database() "
                + "and table_name = 'notification_outbox'", Integer.class)).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run the migration test**

Expected: failure because no migration creates the table.

- [x] **Step 3: Create the migration**

`V1__infrastructure.sql` creates:

```sql
CREATE TABLE notification_outbox (
    id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at TIMESTAMP(3) NULL,
    INDEX idx_outbox_pending (status, available_at)
);

CREATE TABLE notification_delivery (
    id CHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    channel VARCHAR(30) NOT NULL,
    destination_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    delivered_at TIMESTAMP(3) NULL
);

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(100) NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(80) NULL,
    resource_id VARCHAR(100) NULL,
    request_id VARCHAR(80) NOT NULL,
    metadata JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_audit_actor_created (actor_id, created_at),
    INDEX idx_audit_action_created (action, created_at)
);
```

- [x] **Step 4: Run the migration test**

Expected: `BUILD SUCCESS`.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "feat: add versioned infrastructure schema"
```

### Task 5: Add Redis Verification Code Storage

**Files:**
- Create: `apps/api/src/main/java/com/sysudate/api/identity/verification/VerificationCodeStore.java`
- Create: `apps/api/src/main/java/com/sysudate/api/identity/verification/RedisVerificationCodeStore.java`
- Create: `apps/api/src/test/java/com/sysudate/api/identity/verification/RedisVerificationCodeStoreTest.java`

- [x] **Step 1: Write failing Redis-backed tests**

The tests start a Testcontainers Redis instance and assert:

```java
store.save("student@sysu.edu.cn", "123456", Duration.ofMinutes(5));
assertThat(store.consume("student@sysu.edu.cn", "123456")).isTrue();
assertThat(store.consume("student@sysu.edu.cn", "123456")).isFalse();
assertThat(store.consume("student@sysu.edu.cn", "000000")).isFalse();
```

- [x] **Step 2: Run the tests**

Expected: compilation failure because the store does not exist.

- [x] **Step 3: Implement atomic consume**

The interface is:

```java
public interface VerificationCodeStore {
    void save(String email, String code, Duration ttl);
    boolean consume(String email, String code);
}
```

The Redis implementation stores an HMAC-SHA-256 digest, never the plaintext code. `consume` runs a Lua script that compares the stored digest and deletes the key atomically only on a match.

- [x] **Step 4: Run the Redis tests**

Expected: all verification tests pass.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "feat: store one-time verification codes in Redis"
```

### Task 6: Add A Distributed Rate Limiter

**Files:**
- Create: `apps/api/src/main/java/com/sysudate/api/common/ratelimit/RateLimitDecision.java`
- Create: `apps/api/src/main/java/com/sysudate/api/common/ratelimit/RateLimitService.java`
- Create: `apps/api/src/main/java/com/sysudate/api/common/ratelimit/RedisRateLimitService.java`
- Create: `apps/api/src/test/java/com/sysudate/api/common/ratelimit/RedisRateLimitServiceTest.java`

- [x] **Step 1: Write failing behavior tests**

```java
assertThat(limiter.check("login:127.0.0.1", 2, Duration.ofMinutes(1)).allowed()).isTrue();
assertThat(limiter.check("login:127.0.0.1", 2, Duration.ofMinutes(1)).allowed()).isTrue();
RateLimitDecision rejected = limiter.check("login:127.0.0.1", 2, Duration.ofMinutes(1));
assertThat(rejected.allowed()).isFalse();
assertThat(rejected.retryAfterSeconds()).isPositive();
```

- [x] **Step 2: Run the test**

Expected: compilation failure.

- [x] **Step 3: Implement a Redis fixed-window script**

The Lua script increments one key, sets expiry only when the counter is created, and returns count and remaining TTL in one atomic operation. The Java service maps that result to:

```java
public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {}
```

- [x] **Step 4: Run the rate-limit tests**

Expected: all tests pass.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "feat: add Redis distributed rate limiting"
```

### Task 7: Add The Transactional Notification Outbox

**Files:**
- Create: `apps/api/src/main/java/com/sysudate/api/notification/NotificationEvent.java`
- Create: `apps/api/src/main/java/com/sysudate/api/notification/NotificationOutboxRepository.java`
- Create: `apps/api/src/main/java/com/sysudate/api/notification/JdbcNotificationOutboxRepository.java`
- Create: `apps/api/src/test/java/com/sysudate/api/notification/JdbcNotificationOutboxRepositoryTest.java`

- [x] **Step 1: Write the failing repository test**

```java
NotificationEvent event = NotificationEvent.emailVerification(
    "student@sysu.edu.cn", "123456", "request-1");
repository.enqueue(event);

List<NotificationEvent> pending = repository.lockPending(10);

assertThat(pending).extracting(NotificationEvent::id).containsExactly(event.id());
repository.markPublished(event.id());
assertThat(repository.lockPending(10)).isEmpty();
```

- [x] **Step 2: Run the repository test**

Expected: compilation failure.

- [x] **Step 3: Implement the repository**

Use `JdbcTemplate` with parameterized statements. `lockPending` runs inside a transaction and uses:

```sql
SELECT id, event_type, aggregate_id, payload, attempt_count, available_at, created_at
FROM notification_outbox
WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(3)
ORDER BY created_at
LIMIT ?
FOR UPDATE SKIP LOCKED
```

The payload stores destination and template variables; logging must never print it.

- [x] **Step 4: Run repository and migration tests**

Expected: all MySQL Testcontainers tests pass.

- [x] **Step 5: Commit**

```powershell
git add apps/api
git commit -m "feat: add transactional notification outbox"
```

### Task 8: Add Reproducible Local Infrastructure

**Files:**
- Create: `compose.yaml`
- Create: `.env.compose.example`
- Create: `infra/prometheus/prometheus.yml`
- Create: `apps/api/Dockerfile`
- Modify: `.gitignore`
- Modify: `README.md`

- [x] **Step 1: Write a failing configuration check**

Run:

```powershell
docker compose --env-file .env.compose.example config --quiet
```

Expected: failure because `compose.yaml` does not exist.

- [x] **Step 2: Create local services**

Compose defines:

- `mysql` from `mysql:8.4`;
- `redis` from `redis:7.4-alpine`;
- `rabbitmq` from `rabbitmq:4-management`;
- `prometheus` from `prom/prometheus`;
- `api` built from `apps/api/Dockerfile`.

Each dependency has a health check. The API waits for healthy dependencies and receives configuration only through environment variables.

- [x] **Step 3: Add the multi-stage API image**

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src src
RUN mvn -B package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 appuser
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [x] **Step 4: Validate configuration and tests**

Run:

```powershell
docker compose --env-file .env.compose.example config --quiet
docker run --rm -v "${PWD}/apps/api:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn verify
```

Expected: both commands exit with code 0.

- [x] **Step 5: Commit**

```powershell
git add compose.yaml .env.compose.example infra apps/api/Dockerfile .gitignore README.md
git commit -m "build: add reproducible local infrastructure"
```

### Task 9: Phase 1 Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-07-28-phase-1-java-foundation.md`
- Create: `docs/verification/phase-1.md`

- [x] **Step 1: Run the complete Java verification**

```powershell
docker run --rm -v "${PWD}/apps/api:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn verify
```

Expected: `BUILD SUCCESS` with no skipped project tests.

- [x] **Step 2: Validate Compose**

```powershell
docker compose --env-file .env.compose.example config --quiet
```

Expected: exit code 0.

- [x] **Step 3: Start dependencies and inspect readiness**

```powershell
docker compose --env-file .env.compose.example up -d mysql redis rabbitmq
docker compose ps
```

Expected: all three dependencies report healthy.

- [x] **Step 4: Record durable evidence**

`docs/verification/phase-1.md` records exact commands, exit codes, test counts, environment limitations, and the commit hash. Do not invent performance or reliability numbers.

- [x] **Step 5: Commit**

```powershell
git add docs/superpowers/plans/2026-07-28-phase-1-java-foundation.md docs/verification/phase-1.md
git commit -m "docs: record phase 1 verification"
```
