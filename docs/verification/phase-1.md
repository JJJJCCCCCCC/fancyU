# Phase 1 Verification

Date: 2026-07-28

Branch: `refactor/phase-1-java-foundation`

Status: complete

Verified commit: `217dd54`

## Environment

- Host: Windows, PowerShell
- Docker Desktop server: 29.5.3
- Maven image: `maven:3.9.9-eclipse-temurin-21`
- Host Java/Maven: not installed
- Testcontainers API override: `-Dapi.version=1.40`

## Verified Evidence

### Infrastructure migration

Command:

```powershell
docker run --rm `
  -v "${PWD}/apps/api:/workspace" `
  -v sysudate-m2:/root/.m2 `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -w /workspace `
  maven:3.9.9-eclipse-temurin-21 `
  mvn '-Dapi.version=1.40' -Dtest=FlywayMigrationTest test
```

Result:

- Exit code: 0
- Tests: 1
- Failures: 0
- Errors: 0
- Skipped: 0
- MySQL: real `mysql:8.4` Testcontainer
- Flyway: 11.20.3, no unsupported MySQL version warning
- Spring Boot application context: started successfully with Flyway

### Complete Java regression

Durable Surefire reports under the local, ignored `apps/api/target/` directory
record:

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `RedisRateLimitServiceTest` | 15 | 0 | 0 | 0 |
| `RedisVerificationCodeStoreTest` | 31 | 0 | 0 | 0 |
| `FlywayApplicationContextTest` | 1 | 0 | 0 | 0 |
| `FlywayMigrationTest` | 1 | 0 | 0 | 0 |
| `JdbcNotificationOutboxRepositoryTest` | 7 | 0 | 0 | 0 |
| `SerialNumberTest` | 14 | 0 | 0 | 0 |
| `ActuatorHealthSecurityTest` | 4 | 0 | 0 | 0 |
| `SystemHealthControllerTest` | 16 | 0 | 0 | 0 |
| `SysudateApiApplicationTest` | 1 | 0 | 0 | 0 |
| **Total** | **90** | **0** | **0** | **0** |

The Redis tests used real `redis:7.4-alpine` Testcontainers. The MySQL tests
used real `mysql:8.4` Testcontainers. The Maven `verify` lifecycle completed
with exit code 0 and packaged the executable Spring Boot JAR.

### Static delivery checks after Task 5

Commands and results:

```powershell
git diff --check
# exit 0

docker compose --env-file .env.compose.example config --quiet
# exit 0
```

Additional local checks:

- `compose.yaml`, Prometheus config and Spring YAML parsed successfully.
- Every `${VARIABLE}` referenced by Compose exists in
  `.env.compose.example`.
- Java files added in Tasks 6 and 7 have no lines over 100 characters.
- `target/` is ignored and is not part of any commit.

## Runtime Verification

Command:

```powershell
docker compose --env-file .env.compose.example up --build --wait
```

Result:

- Exit code: 0
- API, MySQL, Redis, RabbitMQ and Prometheus: healthy
- `GET http://localhost:8180/api/v1/system/health`: `{"status":"UP"}`
- Prometheus target `sysudate-api`: `up`
- Prometheus scrapes the isolated API management port
  `http://api:9091/actuator/prometheus`; that port is not published to the host.

The example API host port is `8180` because Windows reserves the host range
`8071-8170` on the verification machine. The container still listens on
application port `8080`.
