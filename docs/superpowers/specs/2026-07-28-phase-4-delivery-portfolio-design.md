# Phase 4 Delivery And Portfolio Design

## Goal

Make the refactored project reproducible, observable, secure to publish, and easy to demonstrate in an internship interview.

## Frontend Integration

Maintained HTML pages load one `config.js` value for the API base URL. No page embeds a production Function Compute URL. Existing page layout and business behavior remain unchanged.

Unused pages are removed only after an automated link and request scan proves that maintained pages do not reference them. Deletion occurs in a separate reviewed change.

## Build And Deployment

Docker Compose provides the complete local stack. Production images use multi-stage builds, non-root users, health checks, pinned base-image majors, and environment-based configuration.

GitHub Actions runs:

1. Java formatting and tests;
2. Python linting and tests;
3. AI evaluation;
4. frontend static checks;
5. secret scanning;
6. container image builds.

Deployment documentation covers local Docker Compose and a cloud-neutral single-host deployment. Alibaba Cloud history remains documented as evidence, but the refactor does not depend on access to the old account.

## Observability

- JSON logs share request and trace IDs across Java and Python.
- Actuator and Prometheus expose application and AI metrics.
- dashboards focus on request latency, errors, database pool, queue failures, and AI fallback.
- health checks distinguish liveness from dependency readiness.

## Portfolio Evidence

The public README includes:

- the verified 1,500+ registration history with accurate wording;
- before-and-after architecture;
- one end-to-end sequence diagram;
- local startup and test commands;
- API and AI evaluation examples using synthetic data;
- engineering decisions and rejected alternatives;
- privacy boundaries and a list of materials intentionally excluded.

Claims are backed by test output, evaluation artifacts, sanitized screenshots, and existing deployment evidence. No invented latency, concurrency, active-user, or model-quality number is published.

## Exit Criteria

- a clean machine can start the stack from documented commands;
- CI runs without production secrets;
- maintained pages contain no hard-coded production API URL;
- all public artifacts pass secret and PII checks;
- the README supports a five-minute demo and a deeper architecture interview.

