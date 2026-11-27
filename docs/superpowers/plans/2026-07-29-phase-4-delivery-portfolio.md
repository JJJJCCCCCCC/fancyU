# Phase 4 Delivery And Portfolio Implementation Plan

> Execute with `superpowers:executing-plans`; verification evidence is part of
> the deliverable.

**Goal:** Make the complete system reproducible, observable, secure to publish,
and easy to demonstrate in a backend plus AI internship interview.

### Task 1: Integrate The Frontend

- [x] Add one runtime `config.js` and remove every hard-coded cloud API URL.
- [x] Serve the maintained frontend through a production Nginx image.
- [x] Add navigation, request-contract, and browser workflow checks.
- [x] Remove a page only when link/request scans prove it is unused.

### Task 2: Complete Local And Production Packaging

- [x] Add AI and web services to Compose with health-based dependencies.
- [x] Add environment templates without secrets.
- [x] Add liveness/readiness checks and non-root images.
- [x] Document cloud-neutral single-host deployment and rollback.

### Task 3: Add CI Quality Gates

- [x] Run Java tests, Python lint/tests, AI evaluation, frontend checks, secret
  scanning, and image builds in GitHub Actions.
- [x] Pin action majors and use no production credentials.
- [x] Fix Mockito agent configuration and fail builds on skipped required tests.

### Task 4: Finish Observability

- [x] Emit JSON logs with shared request and trace IDs.
- [x] Scrape Java and AI metrics with Prometheus.
- [x] Provision Grafana dashboards for latency, errors, pools, queues, and AI
  fallback.
- [x] Add alert rules for API readiness and notification failures.

### Task 5: Build Portfolio Evidence

- [x] Rewrite the README around architecture, workflows, decisions, and privacy.
- [x] Add architecture and sequence diagrams.
- [x] Add reproducible synthetic evaluation cases and a five-minute demo script.
- [x] Add verified test, evaluation, and deployment evidence only.

### Task 6: Final Release Verification

- [x] Start the complete stack from a clean Compose state.
- [x] Run Java, Python, AI evaluation, static frontend, secret, and browser tests.
- [x] Verify every maintained page and API workflow.
- [x] Produce server-resident and re-upload file manifests.
- [x] Record unresolved external limitations without implying success.
