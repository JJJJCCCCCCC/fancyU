# Phase 2 Business Migration Implementation Plan

> Execute with `superpowers:executing-plans`. Use test-first changes for every
> externally visible contract.

**Goal:** Replace the legacy Node runtime with Spring Boot while preserving the
maintained frontend request and response contracts and existing MySQL data.

**Architecture:** A modular monolith with identity, survey, matching,
administration, complaint, notification, and compatibility modules. Controllers
delegate to application services; repositories alone own SQL; Flyway alone owns
DDL.

### Task 1: Freeze Legacy Contracts

- [x] Inventory every maintained frontend request and navigation edge.
- [x] Add JSON contract fixtures for all legacy root actions and REST routes.
- [x] Add a static test that every frontend request is represented by a fixture.

### Task 2: Migrate The Legacy Schema

- [x] Add a Flyway migration for accounts, preferences, love surveys, friend
  surveys, match history, complaints, chat sessions, and idempotency records.
- [x] Preserve legacy table and column names needed by production data.
- [x] Add empty-schema and sanitized-legacy-schema migration tests.
- [x] Prove that a second migration run is a no-op.

### Task 3: Implement Authentication And Accounts

- [x] Add JWT access tokens with account ID, role, and token version.
- [x] Add BCrypt password storage and constant-shape login failures.
- [x] Implement register, bind, login, current account, username/contact/password
  changes, username availability, and account deletion.
- [x] Revoke existing tokens after password reset/change and account deletion.
- [x] Apply Redis rate limits and one-time verification codes.

### Task 4: Implement Survey And Serial Workflows

- [x] Insert a survey and immutable serial in one transaction.
- [x] Support one love and one friend survey per account.
- [x] Implement public lookup with fail-closed display-field filtering.
- [x] Implement verified survey retrieval and updates.
- [x] Support request idempotency without allocating a second serial.

### Task 5: Implement Matching And Consent

- [x] Implement survey status, participation toggles, and owned match history.
- [x] Make mutual contact agreement transactional and idempotent.
- [x] Return contact details only after both accounts agree.
- [x] Implement consent withdrawal without exposing unrelated records.

### Task 6: Implement Complaints And Administration

- [x] Implement complaint submission, listing, and status transitions.
- [x] Implement admin login, user list/detail/delete, and existing statistics.
- [x] Implement compatible chat-session persistence.
- [x] Audit destructive and administrative actions.

### Task 7: Implement Notification Delivery

- [x] Enqueue verification and confirmation email events through the outbox.
- [x] Publish outbox events to RabbitMQ with retry-safe delivery IDs.
- [x] Consume and send email without logging addresses, codes, or payloads.
- [x] Record delivery status and failure category.

### Task 8: Retire The Node Runtime

- [x] Route every maintained frontend contract through Java.
- [x] Run fixture replay and browser workflow tests.
- [x] Confirm `fc-code` is not referenced by runtime configuration.
- [x] Move legacy code to documented reference-only status.
- [x] Record exact test and migration evidence.
