# Phase 3 AI Assistant Modernization Design

## Goal

Preserve and harden the existing administrator natural-language data assistant as a separate FastAPI service.

## Service Contract

Spring Boot calls:

```text
POST /internal/v1/chat
```

The request contains an administrator identity reference, conversation messages, locale, request ID, and trace ID. The response contains answer text, selected tool, normalized parameters, fallback status, provider latency, and prompt version.

Calls use a short-lived service JWT. The AI service cannot be reached by public frontend credentials.

## Query Intent

The allowed actions correspond to existing behavior:

- user detail by serial;
- matching participation statistics;
- recent users;
- aggregate distributions;
- filtered user search;
- user count;
- existing matching recommendation lookup;
- default recent-user overview.

Parameters use typed enums, bounded numeric ranges, and bounded pagination. Unknown actions fail validation and return a safe clarification response.

## Java Query Tools

FastAPI calls one internal Java endpoint per validated intent. Java owns SQL and authorization.

Tool results:

- exclude email, contact details, photos, password metadata, and unrestricted free text;
- use parameterized SQL;
- cap result counts;
- include a schema version;
- carry no database-specific exception details.

## Provider Adapter

The OpenAI-compatible provider adapter:

- reads endpoint, model name, API key, timeout, and retry policy from configuration;
- requests JSON-schema output for planning;
- validates all responses with Pydantic;
- uses bounded exponential retry only for transient failures;
- records token usage when supplied;
- never logs prompt contents containing returned user data.

## Fallback

The assistant remains useful when the model endpoint fails. A deterministic formatter produces concise Chinese summaries from the validated Java tool result. Provider failure therefore returns HTTP success with `fallbackUsed: true` when the database query itself succeeded.

## Evaluation

The repository includes a synthetic Chinese evaluation set with:

- paraphrases for every allowed intent;
- invalid and ambiguous questions;
- prompt-injection attempts;
- attempts to retrieve emails and contact details;
- provider timeout and malformed JSON cases.

Metrics include intent accuracy, parameter exact match, unsafe-field leakage count, fallback success rate, and P95 latency. The release gate requires zero unsafe-field leaks in the evaluation set.

## Exit Criteria

- AI service has no database credentials;
- every intent and failure path is tested;
- evaluation report is reproducible from a command;
- administrator chat history remains compatible;
- the old regex parser and direct AI-side data construction are no longer active.

