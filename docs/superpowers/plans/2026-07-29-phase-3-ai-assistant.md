# Phase 3 AI Assistant Implementation Plan

> Execute with `superpowers:executing-plans`; keep the AI service stateless and
> database-free.

**Goal:** Preserve the administrator data assistant through a hardened FastAPI
service that can only invoke authorized Java query tools.

### Task 1: Bootstrap The AI Service

- [x] Add FastAPI, Pydantic settings, structured logging, health, and metrics.
- [x] Validate short-lived Java-to-AI service JWTs.
- [x] Add unit tests and a non-root production image.

### Task 2: Define Typed Intents

- [x] Model every existing assistant intent with bounded Pydantic parameters.
- [x] Implement deterministic Chinese intent parsing for provider-independent
  operation.
- [x] Reject unknown fields, unsafe requests, and prompt-injection instructions.

### Task 3: Add Java Query Tools

- [x] Add service-authenticated internal endpoints for each allowed intent.
- [x] Use parameterized repository queries and strict result limits.
- [x] Remove contact, email, photo, credential, and unrestricted text fields.
- [x] Add authorization and leakage tests.

### Task 4: Add The Provider Adapter

- [x] Add an OpenAI-compatible adapter with timeout and bounded retries.
- [x] Validate provider response shape before using generated text.
- [x] Never log prompts or returned user records.
- [x] Add a deterministic formatter for provider failures.

### Task 5: Preserve Public Compatibility

- [x] Make Java `/llm/chat` call FastAPI and keep the current frontend envelope.
- [x] Preserve Java-owned admin chat history routes.
- [x] Propagate request and trace IDs across both services.

### Task 6: Add Reproducible Evaluation

- [x] Add synthetic Chinese cases for all intents and unsafe requests.
- [x] Report intent accuracy, parameter match, fallback success, and leaks.
- [x] Gate release on zero unsafe-field leaks.
- [x] Store the generated evaluation report as verification evidence.
