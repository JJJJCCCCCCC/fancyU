package com.sysudate.api.survey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.survey.SurveyRepository.SurveyRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcSurveyRepository implements SurveyRepository {
    private static final Set<String> WRITABLE_FIELDS = Set.of(
        "contact_email", "contact_entries", "school", "nickname", "sex",
        "sex_choice", "sex_prefer", "grade", "grade_prefer",
        "relationship_goal", "experience", "ex_prefer", "future_plan",
        "major", "prefer_major", "major_prefer", "campus", "hometown",
        "constellation", "constellation_prefer", "mbti", "mbti_prefer",
        "interests", "interest_custom", "height", "height_min",
        "height_max", "beauty_self", "beauty_prefer_min", "body_type",
        "fitness_level", "body_prefer", "fitness_prefer",
        "q1_co_dependent", "q2_conflict", "q3_ritual",
        "q4_consumption", "q5_life_focus", "q6_emotion",
        "agree_topics", "self_description", "ideal_description", "photos",
        "display_self", "feedback", "avatar", "online_offline",
        "friend_goal", "ideal_friend", "special_request",
        "personality_self_1", "personality_self_2", "personality_self_3",
        "personality_self_4", "personality_self_5",
        "personality_expect_1", "personality_expect_2",
        "personality_expect_3", "personality_expect_4",
        "personality_expect_5", "personality_expect_1_no_pref",
        "personality_expect_2_no_pref", "personality_expect_3_no_pref",
        "personality_expect_4_no_pref", "personality_expect_5_no_pref",
        "expect_schools", "expect_school_campuses", "expect_quick");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<String>> columnCache =
        new ConcurrentHashMap<>();

    public JdbcSurveyRepository(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SurveyRecord> findBySerial(String serialNumber) {
        Optional<SurveyRecord> love = findInTable(
            "users",
            "love",
            "serial_number",
            serialNumber);
        return love.isPresent()
            ? love
            : findInTable(
                "friend_survey",
                "friend",
                "serial_number",
                serialNumber);
    }

    @Override
    public Optional<SurveyRecord> findByAccountId(int accountId) {
        Optional<SurveyRecord> love = findInTable(
            "users",
            "love",
            "account_id",
            accountId);
        return love.isPresent()
            ? love
            : findInTable(
                "friend_survey",
                "friend",
                "account_id",
                accountId);
    }

    @Override
    public boolean accountHasSurvey(int accountId, String surveyType) {
        String table = tableFor(surveyType);
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE account_id = ?",
            Integer.class,
            accountId);
        return count != null && count > 0;
    }

    @Override
    public void insert(
        String surveyType,
        String serialNumber,
        Integer accountId,
        String email,
        Map<String, Object> data
    ) {
        String table = tableFor(surveyType);
        Set<String> columns = columns(table);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("serial_number", serialNumber);
        values.put("survey_type", surveyType);
        values.put("account_id", accountId);
        values.put("email", email);
        data.forEach((field, value) -> {
            if (WRITABLE_FIELDS.contains(field)
                && columns.contains(field)) {
                values.put(field, databaseValue(value));
            }
        });
        values.keySet().removeIf(field -> !columns.contains(field));

        String fieldSql = String.join(", ", values.keySet());
        String placeholders = String.join(
            ", ",
            values.keySet().stream().map(field -> "?").toList());
        jdbc.update(
            "INSERT INTO " + table + " (" + fieldSql + ") VALUES ("
                + placeholders + ")",
            values.values().toArray());
    }

    @Override
    public void update(
        SurveyRecord survey,
        Map<String, Object> changes
    ) {
        Set<String> columns = columns(survey.table());
        Map<String, Object> values = new LinkedHashMap<>();
        changes.forEach((field, value) -> {
            if (WRITABLE_FIELDS.contains(field)
                && columns.contains(field)) {
                values.put(field, databaseValue(value));
            }
        });
        if (values.isEmpty()) {
            return;
        }
        String assignments = String.join(
            ", ",
            values.keySet().stream().map(field -> field + " = ?").toList());
        List<Object> parameters = new ArrayList<>(values.values());
        parameters.add(survey.serialNumber());
        jdbc.update(
            "UPDATE " + survey.table() + " SET " + assignments
                + " WHERE serial_number = ?",
            parameters.toArray());
    }

    @Override
    public Optional<Map<String, Object>> idempotentResult(String key) {
        return jdbc.query(
            """
                SELECT response_payload
                FROM request_idempotency
                WHERE idempotency_key = ?
                  AND expires_at > CURRENT_TIMESTAMP(3)
                """,
            (resultSet, rowNumber) -> readJsonMap(
                resultSet.getString("response_payload")),
            key).stream().findFirst();
    }

    @Override
    public void saveIdempotentResult(
        String key,
        Integer accountId,
        String operation,
        Map<String, Object> result
    ) {
        jdbc.update(
            """
                INSERT INTO request_idempotency (
                    idempotency_key, account_id, operation,
                    response_payload, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
            key,
            accountId,
            operation,
            writeJson(result),
            Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
    }

    private Optional<SurveyRecord> findInTable(
        String table,
        String surveyType,
        String field,
        Object value
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE " + field + " = ? LIMIT 1",
            value);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> data = normalizeRow(rows.getFirst());
        return Optional.of(new SurveyRecord(
            table,
            surveyType,
            (String) data.get("serial_number"),
            number(data.get("account_id")),
            (String) data.get("email"),
            (String) data.get("contact_email"),
            data));
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(
            key.toLowerCase(),
            value));
        return normalized;
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Set<String> columns(String table) {
        return columnCache.computeIfAbsent(table, ignored -> Set.copyOf(
            jdbc.queryForList(
                """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                    """,
                String.class,
                table)));
    }

    private String tableFor(String surveyType) {
        return switch (surveyType) {
            case "love" -> "users";
            case "friend" -> "friend_survey";
            default -> throw new IllegalArgumentException(
                "Unsupported survey type");
        };
    }

    private Object databaseValue(Object value) {
        if (value instanceof List<?> || value instanceof Map<?, ?>) {
            return writeJson(value);
        }
        return value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                "Unable to serialize survey field",
                exception);
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Unable to parse idempotent response",
                exception);
        }
    }
}
