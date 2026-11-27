# Phase 1 Java Foundation Design

## Goal

Establish a production-shaped Spring Boot foundation that can receive migrated SYSUDate behavior without changing the current frontend or production data.

## Deliverable

Phase 1 creates `apps/api` and local infrastructure. It does not replace the Node.js API yet.

The service exposes:

- `GET /actuator/health`;
- `GET /api/v1/system/health`;
- one versioned error contract;
- infrastructure adapters for MySQL, Redis, and RabbitMQ;
- reusable JWT and request-ID support;
- a Redis-backed verification-code store and distributed rate limiter;
- a transactional notification outbox model.

## Package Boundaries

```text
com.sysudate
├─ common
│  ├─ api
│  ├─ error
│  ├─ security
│  └─ observability
├─ identity
├─ account
├─ serial
├─ survey
├─ matching
├─ complaint
├─ administration
├─ notification
└─ aigateway
```

Domain packages expose application services. Web controllers depend on application services, not mapper implementations. MyBatis mappers remain internal to their owning package.

## Database Baseline

Flyway starts from a documented baseline compatible with the sanitized legacy schema. Runtime `CREATE TABLE` and `ALTER TABLE` calls are forbidden.

Phase 1 owns only infrastructure tables:

- `flyway_schema_history` through Flyway metadata;
- `notification_outbox`;
- `notification_delivery`;
- `audit_log`.

Legacy business tables are represented in a baseline migration and are not destructively modified.

## Local Infrastructure

Docker Compose starts MySQL 8, Redis, RabbitMQ with management UI, and Prometheus. Application services use health checks and named volumes. Credentials come from `.env.example` values intended only for local development.

## Test Gate

Phase 1 passes:

- unit tests for error mapping, serial format, verification TTL behavior, and rate-limit decisions;
- Testcontainers integration tests for Flyway and the notification outbox;
- an application context test;
- a Docker Compose configuration validation;
- secret scanning of tracked files.

## Exit Criteria

- `./mvnw verify` passes from a clean checkout;
- local dependencies start with one command;
- the API starts without the legacy Node process;
- health endpoints report MySQL, Redis, and RabbitMQ state;
- no production endpoint or credential is required for tests.
