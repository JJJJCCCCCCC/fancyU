# SYSUDate Industrial Modernization Design

## 1. Objective

Refactor SYSUDate into a portfolio-grade backend and AI application project without adding product features or changing the existing user-facing business scope.

The modernized system must preserve:

- campus email registration and verification;
- account login and profile management;
- serial-number generation, binding, lookup, and survey association;
- love and friendship surveys;
- matching participation, match history, and mutual contact exchange;
- complaints and administrator workflows;
- the existing administrator natural-language data assistant;
- compatibility with the current static frontend and existing MySQL data.

The refactor is successful when the same workflows run through a tested Spring Boot API, the administrator assistant runs through a separately tested FastAPI service, and the full stack starts reproducibly with Docker Compose.

## 2. Current-State Findings

The current backend is a 2,535-line Express file with approximately 28 HTTP routes. Database access, authentication, email delivery, schema creation, matching, complaint handling, administration, and LLM orchestration share one runtime module.

The existing project has valuable real-world evidence:

- more than 1,500 historical registrations;
- deployment on Alibaba Cloud Function Compute, RDS, and OSS;
- complete public-facing user flows;
- production debugging and operations evidence.

The main engineering gaps are:

- no automated test suite;
- no explicit domain boundaries;
- schema changes occur both in SQL files and during live requests;
- in-memory rate limits disappear on process restart;
- HTTP responses use business codes inside mostly successful HTTP responses;
- frontend pages duplicate a hard-coded production API URL;
- the AI assistant combines intent parsing, database querying, PII-rich result construction, prompt assembly, and provider calls in one file;
- no AI evaluation set, structured output validation, audit trail, or deterministic fallback.

## 3. Target Architecture

```text
Static frontend
      |
      v
Spring Boot modular monolith
  |-- identity
  |-- account
  |-- serial
  |-- survey
  |-- matching
  |-- complaint
  |-- administration
  |-- notification
  |-- ai-gateway
      |
      |-- MySQL 8 + Flyway
      |-- Redis
      |-- RabbitMQ
      `-- FastAPI AI service
              |
              `-- OpenAI-compatible model endpoint
```

The Java application is a modular monolith. Business modules are separated by package and service interfaces, but remain one deployable process because the system does not need operationally expensive microservices.

The Python service is separate because model-provider integration, schema-based generation, prompt evaluation, and AI testing are better supported by the Python ecosystem. It has no direct database credentials. It can only call authenticated, read-only Java query tools.

## 4. Technology Decisions

### Java service

- Java 21
- Spring Boot 3
- Maven Wrapper
- Spring Web and Bean Validation
- Spring Security and JWT
- MyBatis for explicit SQL and legacy-schema compatibility
- Flyway for deterministic schema versions
- Spring Data Redis
- Spring AMQP with RabbitMQ
- springdoc-openapi
- Micrometer, Prometheus, and Actuator
- JUnit 5, AssertJ, MockMvc, and Testcontainers

### AI service

- Python 3.12
- FastAPI
- Pydantic v2
- httpx
- OpenAI-compatible provider adapter
- pytest
- respx for provider and Java-tool mocks
- a synthetic, privacy-safe evaluation data set

### Delivery

- Docker Compose for local MySQL, Redis, RabbitMQ, Java API, AI service, and Prometheus
- GitHub Actions for Java tests, Python tests, image builds, and secret scanning
- structured JSON logs with request and trace identifiers

## 5. Data Ownership And Compatibility

MySQL remains the source of truth for accounts, surveys, matches, complaints, and AI conversation metadata.

Redis owns only ephemeral state:

- email verification codes with TTL;
- failed verification counters;
- distributed rate-limit buckets;
- short-lived idempotency keys;
- optional read-through cache entries that can be safely rebuilt.

RabbitMQ carries notification jobs. The database transaction writes an outbox record; a publisher moves committed events to RabbitMQ. Consumers use an idempotency key before sending email. Failed messages use bounded retries and a dead-letter queue.

The first Flyway baseline represents the deployed schema without dropping or renaming existing columns. Normalization and constraints are introduced only through additive migrations. Migration verification runs against a sanitized schema fixture before production use.

## 6. API Compatibility

The first migration stage preserves the current browser contract through a compatibility controller:

- legacy `/?action=...` requests continue to work;
- current `/account/*`, `/admin/*`, `/api/complaint`, and `/llm/chat` URLs remain available;
- responses retain `code`, `msg`, and `data` during frontend migration;
- new versioned endpoints use `/api/v1/...`, proper HTTP status codes, and a consistent error envelope.

After all frontend pages use `/api/v1`, compatibility endpoints are marked deprecated but remain available for one release. They are not deleted until contract tests prove that no maintained page calls them.

## 7. Security And Privacy

- Passwords remain bcrypt hashes and are never logged.
- JWT signing secrets and model-provider credentials are environment variables.
- Administrator identity is stored as a hashed account/role, not compared with plaintext environment passwords.
- All administrator endpoints require role-based authorization.
- AI query tools return allowlisted fields and aggregated data; contact details, email addresses, photos, and free-form sensitive descriptions are excluded by default.
- Query tool calls, prompt version, provider latency, token usage, and result status are audited without storing secrets.
- CORS is allowlisted by environment.
- Verification, login, serial lookup, complaint submission, and AI chat use Redis-backed limits.
- Request validation rejects unknown enum values, oversized text, invalid serial formats, and unsafe pagination.

## 8. AI Assistant Design

The existing administrator assistant remains the only AI product feature.

The optimized flow is:

1. FastAPI validates the conversation and administrator context.
2. The planner converts the latest question into a `QueryIntent` JSON object.
3. Pydantic validates the action and parameters against an allowlist.
4. FastAPI calls one Java read-only tool endpoint.
5. Java enforces authorization, field projection, pagination, and parameterized SQL.
6. FastAPI asks the model to summarize only the returned tool result.
7. A deterministic formatter returns the tool result when the model is unavailable.
8. Metrics and an audit record capture latency, tool choice, validation failures, and fallback use.

No arbitrary SQL generation and no direct model-to-database connection are permitted. RAG is intentionally excluded because this feature operates on structured operational data and has no relevant document corpus.

## 9. Error Handling And Observability

The Java API uses one exception handler and one error format:

```json
{
  "code": "SERIAL_NOT_FOUND",
  "message": "Serial number was not found",
  "requestId": "01J...",
  "details": []
}
```

Expected validation and business errors do not log stack traces. Unexpected errors include request and trace identifiers but never credentials or PII.

Required metrics include:

- HTTP request count, latency, and error rate;
- database pool usage and slow-query count;
- Redis and RabbitMQ operation failures;
- verification and login rate-limit rejections;
- notification retry and dead-letter counts;
- AI request latency, tool-validation failures, provider failures, and fallback rate.

## 10. Testing Strategy

- Unit tests cover serial validation, display-field filtering, matching agreement transitions, intent schemas, and deterministic AI formatting.
- Repository tests run with Testcontainers MySQL.
- API integration tests use MockMvc and Redis/RabbitMQ containers where required.
- Contract tests replay the current frontend request shapes against compatibility endpoints.
- Python tests mock the model provider and Java tools.
- End-to-end smoke tests cover registration, serial binding, survey submission, serial lookup, administrator query, and AI fallback.
- Migration tests apply all Flyway scripts to an empty database and to a sanitized legacy-schema fixture.

No real user records are copied into tests.

## 11. Delivery Phases

### Phase 1: Java foundation

Create the modular Spring Boot service, versioned error contract, health endpoints, MySQL/Flyway integration, Testcontainers test base, Redis-backed verification primitives, and Docker Compose infrastructure.

### Phase 2: Business migration

Migrate account, serial, survey, matching, complaint, administration, and notification behavior. Add compatibility controllers and frontend contract tests.

### Phase 3: AI assistant modernization

Build the FastAPI service, read-only Java tool endpoints, validated query intents, provider adapter, fallback formatter, audit data, and synthetic evaluation suite.

### Phase 4: Delivery and portfolio readiness

Centralize frontend configuration, complete observability, add CI and container builds, run migration and end-to-end checks, and rewrite the public README around verified architecture and evidence.

Each phase produces runnable software and has its own specification and implementation plan.

## 12. Non-Goals

- no new user-facing feature;
- no recommender-model research project;
- no chat feature for ordinary users;
- no Kubernetes requirement;
- no microservice split for each domain;
- no replacement of the existing frontend framework;
- no publication of production data, credentials, or unredacted evidence.

