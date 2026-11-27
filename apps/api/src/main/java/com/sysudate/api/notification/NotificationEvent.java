package com.sysudate.api.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record NotificationEvent(
    String id,
    String eventType,
    String aggregateId,
    String payload,
    int attemptCount,
    Instant availableAt,
    Instant createdAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public NotificationEvent {
        id = requireText(id, "id");
        eventType = requireText(eventType, "eventType");
        aggregateId = requireText(aggregateId, "aggregateId");
        payload = requireText(payload, "payload");
        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                "attemptCount must not be negative");
        }
        availableAt = Objects.requireNonNull(
            availableAt,
            "availableAt must not be null");
        createdAt = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null");
    }

    public static NotificationEvent emailVerification(
        String destination,
        String code,
        String requestId
    ) {
        String normalizedDestination =
            requireText(destination, "destination")
                .toLowerCase(Locale.ROOT);
        String validatedCode = requireText(code, "code");
        String validatedRequestId = requireText(requestId, "requestId");
        String payload = serializePayload(Map.of(
            "destination", normalizedDestination,
            "template", "email-verification",
            "variables", Map.of(
                "code", validatedCode,
                "requestId", validatedRequestId)));
        Instant now = Instant.now();
        return new NotificationEvent(
            UUID.randomUUID().toString(),
            "EMAIL_VERIFICATION",
            validatedRequestId,
            payload,
            0,
            now,
            now);
    }

    public static NotificationEvent surveyConfirmation(
        String destination,
        String serialNumber,
        String requestId
    ) {
        String normalizedDestination =
            requireText(destination, "destination")
                .toLowerCase(Locale.ROOT);
        String serial = requireText(serialNumber, "serialNumber");
        String validatedRequestId = requireText(requestId, "requestId");
        String payload = serializePayload(Map.of(
            "destination", normalizedDestination,
            "template", "survey-confirmation",
            "variables", Map.of(
                "serialNumber", serial,
                "requestId", validatedRequestId)));
        Instant now = Instant.now();
        return new NotificationEvent(
            UUID.randomUUID().toString(),
            "SURVEY_CONFIRMATION",
            serial,
            payload,
            0,
            now,
            now);
    }

    @Override
    public String toString() {
        return "NotificationEvent[id=%s, eventType=%s, aggregateId=%s, "
            .formatted(id, eventType, aggregateId)
            + "payload=<redacted>, attemptCount=%d, availableAt=%s, "
                .formatted(attemptCount, availableAt)
            + "createdAt=%s]".formatted(createdAt);
    }

    private static String serializePayload(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Unable to serialize notification payload",
                exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank");
        }
        return value.trim();
    }
}
