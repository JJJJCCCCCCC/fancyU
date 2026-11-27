# Final Modernization Verification

Verification date: 2026-07-29

## Passed

- Java 21 clean compile and 45 non-Testcontainers tests:
  0 failures, 0 errors, 0 skipped.
- API production image build after replacing the accidental MyBatis transitive
  JDBC dependency with an explicit Spring JDBC dependency.
- AI Python 3.12 test image: Ruff passed, 21 tests passed.
- AI evaluation: 11/11 cases passed, 4/4 unsafe requests blocked. The offline evaluator covers intent and parameters for allowed requests and rejection for unsafe requests.
- Frontend static verification: 14 pages, local navigation, runtime API config,
  password policy, privacy copy, and documented API contracts.
- Playwright smoke test under a dark operating-system preference: light UI
  retained; home-to-query and dashboard-to-query navigation passed; serial
  submission and public-profile rendering passed.
- Clean container restart with retained volumes: MySQL, Redis, RabbitMQ, API,
  AI, Web, Prometheus, and Grafana all healthy.
- Compose topology validation: production mode excludes the local MySQL
  service; `local-db` mode includes it.
- End-to-end administrator AI request through Nginx, Java, FastAPI, and Java
  query tools: login 200, chat 200, `count_users` intent.
- Automated stack smoke covers system health, serial generation contract,
  unauthenticated serial lookup rejection, administrator login, aggregate AI
  query, prompt-injection rejection, and the CORS allowlist.
- End-to-end English prompt-injection request returned HTTP 400 with the stable
  AI safety error contract.
- Separate user and SERVICE JWT secrets passed the real Java-to-FastAPI-to-Java
  round trip; SERVICE tokens carry the `sysudate-ai` audience.
- CORS preflight for the configured OSS origin returned 200 and the exact
  origin; an unconfigured origin returned 403 without an allow-origin header.
- Prometheus: `sysudate-api`, `sysudate-ai`, and `prometheus` targets up.
- Promtool: Prometheus configuration valid, three alert rules loaded.
- Grafana: `SYSUDate Overview` dashboard provisioned.
- Gitleaks 8.28.0: no leaks found.
- Actionlint 1.7.7: workflow valid.

## Environment Limitation

This Windows Docker Desktop installation does not expose a usable Docker daemon
socket inside a Maven container. Therefore the seven Testcontainers classes
cannot be rerun from a nested local container. They remain mandatory in the
GitHub Actions Java job, where Maven runs directly on the Linux runner and can
start MySQL and Redis containers. The local production stack and its real HTTP
and AI paths were verified independently as listed above.

During the final security rerun, Docker Desktop's configured 163 mirror could
not resolve the JRE and Nginx image metadata. The latest API source still passed
a clean compile and 45 tests; its generated JAR was exercised in the previously
verified non-root production runtime image. GitHub Actions performs the standard
multi-stage Dockerfile build on a Linux runner.
