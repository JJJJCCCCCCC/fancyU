package com.sysudate.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.admin.AdminRepository.UserPage;
import com.sysudate.api.admin.AdminRepository.UserQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcAdminRepository implements AdminRepository {
    private static final Set<String> SORT_FIELDS = Set.of(
        "created_at",
        "updated_at",
        "nickname",
        "sex",
        "campus",
        "grade",
        "height");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAdminRepository(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserPage users(UserQuery query) {
        List<String> predicates = new ArrayList<>();
        predicates.add("1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(query.search())) {
            predicates.add(
                "(u.nickname LIKE ? OR u.serial_number LIKE ? "
                    + "OR u.email LIKE ? OR u.contact_email LIKE ?)");
            String search = "%" + query.search().trim() + "%";
            parameters.addAll(List.of(search, search, search, search));
        }
        addEquals(predicates, parameters, "u.sex", query.sex());
        addEquals(predicates, parameters, "u.campus", query.campus());
        addEquals(predicates, parameters, "u.grade", query.grade());
        if (query.joinMatching() != null) {
            predicates.add(
                "COALESCE(p.join_matching, TRUE) = ?");
            parameters.add(query.joinMatching());
        }
        String where = String.join(" AND ", predicates);
        Long total = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN account_preferences p
                  ON p.account_id = u.account_id
                WHERE
                """ + where,
            Long.class,
            parameters.toArray());

        String sort = SORT_FIELDS.contains(query.sort())
            ? query.sort()
            : "created_at";
        String order = "ASC".equalsIgnoreCase(query.order())
            ? "ASC"
            : "DESC";
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(query.pageSize());
        pageParameters.add((query.page() - 1) * query.pageSize());
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
                SELECT u.*,
                       COALESCE(p.join_matching, TRUE) AS join_matching,
                       a.username AS account_username
                FROM users u
                LEFT JOIN accounts a ON a.id = u.account_id
                LEFT JOIN account_preferences p
                  ON p.account_id = u.account_id
                WHERE
                """ + where + " ORDER BY u." + sort + " " + order
                + " LIMIT ? OFFSET ?",
            pageParameters.toArray());
        return new UserPage(
            rows,
            total == null ? 0 : total,
            query.page(),
            query.pageSize());
    }

    @Override
    public Optional<Map<String, Object>> user(String serialNumber) {
        Optional<Map<String, Object>> love = survey(
            "users",
            "love",
            serialNumber);
        return love.isPresent()
            ? love
            : survey("friend_survey", "friend", serialNumber);
    }

    private Optional<Map<String, Object>> survey(
        String table,
        String surveyType,
        String serialNumber
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
                SELECT survey.*, ? AS resolved_survey_type,
                       a.username AS account_username,
                       a.email AS account_email,
                       a.created_at AS account_created_at,
                       COALESCE(p.join_matching, TRUE) AS join_matching
                FROM %s survey
                LEFT JOIN accounts a ON a.id = survey.account_id
                LEFT JOIN account_preferences p
                  ON p.account_id = survey.account_id
                WHERE survey.serial_number = ?
                LIMIT 1
                """.formatted(table),
            surveyType,
            serialNumber);
        return rows.stream().findFirst();
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", count(
            "SELECT COUNT(*) FROM users"));
        result.put("totalAccounts", count(
            "SELECT COUNT(*) FROM accounts"));
        result.put("joinMatching", count(
            """
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN account_preferences p
                  ON p.account_id = u.account_id
                WHERE COALESCE(p.join_matching, TRUE) = TRUE
                """));
        result.put("sexDist", distribution("sex", 100));
        result.put("campusDist", distribution("campus", 100));
        result.put("gradeDist", distribution("grade", 100));
        result.put("mbtiDist", distribution("mbti", 20));
        result.put(
            "constellationDist",
            distribution("constellation", 20));
        result.put(
            "relationshipGoalDist",
            distribution("relationship_goal", 20));
        result.put("sexChoiceDist", distribution("sex_choice", 20));
        result.put("majorDist", distribution("major", 20));
        result.put("avgHeight", jdbc.queryForObject(
            """
                SELECT ROUND(AVG(height), 1)
                FROM users
                WHERE height BETWEEN 100 AND 250
                """,
            Double.class));
        result.put("dailyReg", jdbc.queryForList(
            """
                SELECT DATE(created_at) AS date, COUNT(*) AS count
                FROM accounts
                GROUP BY DATE(created_at)
                ORDER BY date DESC
                LIMIT 30
                """));
        result.put("dailySurvey", jdbc.queryForList(
            """
                SELECT DATE(created_at) AS date, COUNT(*) AS count
                FROM users
                GROUP BY DATE(created_at)
                ORDER BY date DESC
                LIMIT 30
                """));
        return result;
    }

    @Override
    @Transactional
    public boolean deleteSurvey(String serialNumber) {
        Optional<Map<String, Object>> survey = user(serialNumber);
        if (survey.isEmpty()) {
            return false;
        }
        Object accountId = survey.get().get("account_id");
        if (accountId instanceof Number number) {
            jdbc.update(
                """
                    DELETE FROM match_history
                    WHERE account_id = ? OR match_account_id = ?
                    """,
                number.intValue(),
                number.intValue());
            jdbc.update(
                """
                    UPDATE complaints
                    SET status = 'dismissed'
                    WHERE reporter_account_id = ?
                    """,
                number.intValue());
        }
        int love = jdbc.update(
            "DELETE FROM users WHERE serial_number = ?",
            serialNumber);
        int friend = jdbc.update(
            "DELETE FROM friend_survey WHERE serial_number = ?",
            serialNumber);
        return love + friend == 1;
    }

    @Override
    public List<Map<String, Object>> chatList() {
        return jdbc.queryForList(
            """
                SELECT id, title, created_at, updated_at
                FROM chat_sessions
                ORDER BY updated_at DESC
                LIMIT 50
                """);
    }

    @Override
    public Optional<Map<String, Object>> chat(String id) {
        return jdbc.query(
            "SELECT * FROM chat_sessions WHERE id = ?",
            (resultSet, rowNumber) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", resultSet.getString("id"));
                result.put("title", resultSet.getString("title"));
                result.put(
                    "messages",
                    readMessages(resultSet.getString("messages")));
                result.put(
                    "created_at",
                    resultSet.getTimestamp("created_at").toInstant());
                result.put(
                    "updated_at",
                    resultSet.getTimestamp("updated_at").toInstant());
                return result;
            },
            id).stream().findFirst();
    }

    @Override
    public void saveChat(
        String id,
        String title,
        List<Map<String, Object>> messages
    ) {
        jdbc.update(
            """
                INSERT INTO chat_sessions (id, title, messages)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    messages = VALUES(messages),
                    updated_at = CURRENT_TIMESTAMP(3)
                """,
            id,
            title,
            writeJson(messages));
    }

    @Override
    public void deleteChat(String id) {
        jdbc.update("DELETE FROM chat_sessions WHERE id = ?", id);
    }

    @Override
    public void audit(
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String requestId
    ) {
        jdbc.update(
            """
                INSERT INTO audit_log (
                    actor_type, actor_id, action, resource_type,
                    resource_id, request_id
                ) VALUES ('ADMIN', ?, ?, ?, ?, ?)
                """,
            actor,
            action,
            resourceType,
            resourceId,
            requestId);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> distribution(
        String field,
        int limit
    ) {
        return jdbc.queryForList(
            "SELECT " + field + ", COUNT(*) AS count FROM users "
                + "WHERE " + field + " IS NOT NULL AND " + field
                + " <> '' GROUP BY " + field
                + " ORDER BY count DESC LIMIT ?",
            limit);
    }

    private void addEquals(
        List<String> predicates,
        List<Object> parameters,
        String field,
        String value
    ) {
        if (hasText(value)) {
            predicates.add(field + " = ?");
            parameters.add(value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                "Unable to serialize chat",
                exception);
        }
    }

    private List<Map<String, Object>> readMessages(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
