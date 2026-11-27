# Phase 2 Business Migration Design

## Goal

Move all existing non-AI behavior from `fc-code/index.js` into tested Spring Boot modules while preserving the current frontend contract and legacy data.

## Migration Order

1. identity and account;
2. serial and survey;
3. matching and contact exchange;
4. complaint and administration;
5. notification delivery;
6. compatibility endpoints and frontend contract replay.

## Serial And Survey Invariants

- A serial number is immutable after creation.
- The format is `SYSU-` followed by a UUID-compatible uppercase identifier.
- A serial belongs to exactly one survey record.
- An account may own a love survey, a friendship survey, or both.
- Binding requires proof that the account email matches the survey email.
- Public lookup returns only fields selected by the survey owner's display settings.
- A missing or malformed display setting never exposes additional fields.
- Serial creation and survey insertion run in one database transaction.
- Replayed submissions use an idempotency key and cannot allocate a second serial.

## Account And Verification Invariants

- Registration requires an allowed campus email domain and a one-time verification code.
- Verification codes are hashed in Redis, have a five-minute TTL, and are deleted atomically after successful use.
- Login failure responses do not reveal whether an email exists.
- Password changes revoke refresh sessions.
- Account deletion anonymizes or removes owned data according to the documented privacy workflow.

## Matching Invariants

- Existing match-history records remain readable.
- A user can only view match records involving their own account.
- Contact information is returned only after both sides agree.
- Agreement transitions are transactional and idempotent.
- Leaving matching does not silently expose or create matches.
- No new matching algorithm or recommendation feature is introduced.

## Compatibility Layer

The compatibility controller translates the current request and response shapes into versioned application-service calls. It contains no SQL and no business logic.

Contract fixtures cover:

- `generateSerial`;
- `query`;
- `getSurveyData`;
- `queryByUserId`;
- `sendCode`;
- `checkCode`;
- `verifyMatch`;
- `submitSurvey`;
- `submitFriendSurvey`;
- `updateSurvey`;
- account and administrator routes already used by the frontend.

## Exit Criteria

- maintained frontend pages complete all existing workflows against Spring Boot;
- contract tests cover every maintained frontend request;
- the Node backend is no longer required for normal operation;
- migration validation succeeds on an empty schema and a sanitized legacy schema;
- no runtime schema creation remains.

