package com.sysudate.api.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcAiQueryRepository implements AiQueryRepository {
    private static final Set<String> DISTRIBUTION_FIELDS = Set.of(
        "sex", "campus", "grade", "mbti", "constellation", "major",
        "relationship_goal", "experience", "future_plan", "hometown",
        "fitness_level", "body_type", "interests");
    private static final Set<String> FILTER_FIELDS = Set.of(
        "sex", "campus", "grade", "mbti", "constellation", "major",
        "relationship_goal");
    private static final String SAFE_FIELDS = """
        serial_number, nickname, sex, campus, grade, major, mbti,
        constellation, relationship_goal, experience, future_plan,
        hometown, interests, height, body_type, fitness_level, created_at
        """;

    private final JdbcTemplate jdbc;

    public JdbcAiQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> execute(
        String intent,
        Map<String, Object> parameters
    ) {
        return switch (intent) {
            case "count_users" -> countUsers();
            case "distribution" -> distribution(parameters);
            case "search_users" -> search(parameters);
            case "recent_users" -> recent(parameters);
            case "user_detail" -> detail(parameters);
            case "matching_stats" -> matchingStats();
            default -> throw new IllegalArgumentException(
                "Unsupported AI query intent");
        };
    }

    private Map<String, Object> countUsers() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", count("SELECT COUNT(*) FROM users"));
        result.put("male", count(
            "SELECT COUNT(*) FROM users WHERE sex = '男'"));
        result.put("female", count(
            "SELECT COUNT(*) FROM users WHERE sex = '女'"));
        return envelope("count", "用户数量", result);
    }

    private Map<String, Object> distribution(Map<String, Object> parameters) {
        String field = String.valueOf(parameters.get("field"));
        if (!DISTRIBUTION_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unsupported distribution");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + field + " AS value, COUNT(*) AS count "
                + "FROM users WHERE " + field + " IS NOT NULL AND "
                + field + " <> '' GROUP BY " + field
                + " ORDER BY count DESC LIMIT 20");
        return envelope("stats", field + " 分布", rows);
    }

    private Map<String, Object> search(Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (String field : FILTER_FIELDS) {
            Object value = parameters.get(field);
            if (value instanceof String text && !text.isBlank()) {
                clauses.add(field + " = ?");
                values.add(text.trim());
            }
        }
        addHeight(parameters, clauses, values, "height_min", ">=");
        addHeight(parameters, clauses, values, "height_max", "<=");
        String where = clauses.isEmpty()
            ? "1 = 1"
            : String.join(" AND ", clauses);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + SAFE_FIELDS + " FROM users WHERE " + where
                + " ORDER BY created_at DESC LIMIT 50",
            values.toArray());
        return envelope("table", "用户查询", rows);
    }

    private Map<String, Object> recent(Map<String, Object> parameters) {
        int limit = boundedInt(parameters.get("limit"), 10, 1, 50);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + SAFE_FIELDS
                + " FROM users ORDER BY created_at DESC LIMIT ?",
            limit);
        return envelope("table", "最近用户", rows);
    }

    private Map<String, Object> detail(Map<String, Object> parameters) {
        String serial = String.valueOf(parameters.get("serial"));
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + SAFE_FIELDS
                + " FROM users WHERE serial_number = ? LIMIT 1",
            serial);
        return rows.isEmpty()
            ? envelope("empty", "未找到用户", List.of())
            : envelope("detail", "用户详情", rows.getFirst());
    }

    private Map<String, Object> matchingStats() {
        List<Map<String, Object>> byCampus = jdbc.queryForList(
            """
                SELECT u.campus AS value, COUNT(*) AS count
                FROM users u
                LEFT JOIN account_preferences p
                  ON p.account_id = u.account_id
                WHERE u.account_id IS NOT NULL
                  AND COALESCE(p.join_matching, TRUE) = TRUE
                GROUP BY u.campus
                ORDER BY count DESC
                LIMIT 20
                """);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAccounts", count("SELECT COUNT(*) FROM accounts"));
        data.put("totalMatching", count(
            """
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN account_preferences p
                  ON p.account_id = u.account_id
                WHERE u.account_id IS NOT NULL
                  AND COALESCE(p.join_matching, TRUE) = TRUE
                """));
        data.put("byCampus", byCampus);
        return envelope("stats", "匹配参与统计", data);
    }

    private void addHeight(
        Map<String, Object> parameters,
        List<String> clauses,
        List<Object> values,
        String name,
        String operator
    ) {
        Object value = parameters.get(name);
        if (value instanceof Number number) {
            int height = Math.clamp(number.intValue(), 100, 250);
            clauses.add("height " + operator + " ?");
            values.add(height);
        }
    }

    private int boundedInt(Object value, int fallback, int min, int max) {
        if (!(value instanceof Number number)) {
            return fallback;
        }
        return Math.clamp(number.intValue(), min, max);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> envelope(
        String type,
        String title,
        Object data
    ) {
        return Map.of("type", type, "title", title, "data", data);
    }
}
