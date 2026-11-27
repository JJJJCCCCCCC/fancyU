package com.sysudate.api.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.matching.MatchingRepository.ExchangeState;
import com.sysudate.api.matching.MatchingRepository.MatchRecord;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcMatchingRepository implements MatchingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMatchingRepository(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MatchRecord> findForAccount(int accountId) {
        return jdbc.query(
            """
                SELECT mh.*, a.username AS peer_username,
                       COALESCE(l.nickname, f.nickname) AS peer_nickname,
                       COALESCE(l.school, f.school) AS peer_school,
                       COALESCE(l.grade, f.grade) AS peer_grade,
                       COALESCE(l.major, f.major) AS peer_major,
                       COALESCE(l.mbti, f.mbti) AS peer_mbti,
                       COALESCE(l.interests, f.interests) AS peer_interests,
                       COALESCE(
                           l.self_description,
                           f.self_description
                       ) AS peer_description,
                       COALESCE(l.height, f.height) AS peer_height,
                       COALESCE(l.body_type, f.body_type) AS peer_body_type,
                       COALESCE(
                           l.fitness_level,
                           f.fitness_level
                       ) AS peer_fitness
                FROM match_history mh
                LEFT JOIN accounts a ON a.id = mh.match_account_id
                LEFT JOIN users l
                  ON l.account_id = mh.match_account_id
                 AND mh.survey_type = 'love'
                LEFT JOIN friend_survey f
                  ON f.account_id = mh.match_account_id
                 AND mh.survey_type = 'friend'
                WHERE mh.account_id = ?
                ORDER BY mh.created_at DESC, mh.id DESC
                """,
            (resultSet, rowNumber) -> {
                Map<String, Object> person = new LinkedHashMap<>();
                person.put("name", firstNonBlank(
                    resultSet.getString("peer_nickname"),
                    resultSet.getString("peer_username"),
                    "匿名用户"));
                person.put("school", resultSet.getString("peer_school"));
                person.put("grade", resultSet.getString("peer_grade"));
                person.put("major", resultSet.getString("peer_major"));
                person.put("mbti", resultSet.getString("peer_mbti"));
                person.put(
                    "hobbies",
                    resultSet.getObject("peer_interests"));
                person.put(
                    "self_description",
                    resultSet.getString("peer_description"));
                person.put("height", resultSet.getObject("peer_height"));
                person.put(
                    "body_type",
                    resultSet.getString("peer_body_type"));
                person.put(
                    "fitness_level",
                    resultSet.getString("peer_fitness"));
                return new MatchRecord(
                    resultSet.getInt("id"),
                    resultSet.getInt("account_id"),
                    resultSet.getInt("match_account_id"),
                    resultSet.getString("survey_type"),
                    resultSet.getInt("match_round"),
                    jsonMap(resultSet.getString("match_data")),
                    resultSet.getBoolean("user_agreed"),
                    resultSet.getBoolean("match_agreed"),
                    resultSet.getBoolean("contact_exchanged"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    person);
            },
            accountId);
    }

    @Override
    public Optional<MatchRecord> findOwned(int matchId, int accountId) {
        return findForAccount(accountId).stream()
            .filter(match -> match.id() == matchId)
            .findFirst();
    }

    @Override
    @Transactional
    public ExchangeState agree(int matchId, int accountId) {
        MatchRow current = lockOwned(matchId, accountId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Match record not found"));
        jdbc.update(
            """
                UPDATE match_history
                SET user_agreed = TRUE
                WHERE id = ? AND account_id = ?
                """,
            matchId,
            accountId);

        Optional<MatchRow> peer = lockPeer(current);
        boolean peerAgreed = peer.map(MatchRow::userAgreed)
            .orElse(current.matchAgreed());
        if (peer.isPresent()) {
            jdbc.update(
                """
                    UPDATE match_history
                    SET match_agreed = TRUE
                    WHERE id = ?
                    """,
                peer.get().id());
        }
        boolean exchanged = peerAgreed;
        jdbc.update(
            """
                UPDATE match_history
                SET match_agreed = ?, contact_exchanged = ?
                WHERE id = ?
                """,
            peerAgreed,
            exchanged,
            matchId);
        if (exchanged && peer.isPresent()) {
            jdbc.update(
                """
                    UPDATE match_history
                    SET match_agreed = TRUE, contact_exchanged = TRUE
                    WHERE id = ?
                    """,
                peer.get().id());
        }
        return new ExchangeState(true, peerAgreed, exchanged);
    }

    @Override
    public String contactForAccount(int accountId) {
        List<String> contacts = jdbc.query(
            """
                SELECT COALESCE(
                    l.contact_email,
                    f.contact_email,
                    a.email
                ) AS contact
                FROM accounts a
                LEFT JOIN users l ON l.account_id = a.id
                LEFT JOIN friend_survey f ON f.account_id = a.id
                WHERE a.id = ?
                LIMIT 1
                """,
            (resultSet, rowNumber) -> resultSet.getString("contact"),
            accountId);
        return contacts.isEmpty() ? null : contacts.getFirst();
    }

    @Override
    @Transactional
    public void withdraw(int accountId) {
        jdbc.update(
            """
                INSERT INTO account_preferences (
                    account_id, join_matching, join_friend_matching
                ) VALUES (?, FALSE, FALSE)
                ON DUPLICATE KEY UPDATE
                    join_matching = FALSE,
                    join_friend_matching = FALSE
                """,
            accountId);
        jdbc.update(
            """
                DELETE FROM match_history
                WHERE account_id = ? OR match_account_id = ?
                """,
            accountId,
            accountId);
    }

    private Optional<MatchRow> lockOwned(int matchId, int accountId) {
        return jdbc.query(
            """
                SELECT id, pair_key, account_id, match_account_id,
                       survey_type, user_agreed, match_agreed
                FROM match_history
                WHERE id = ? AND account_id = ?
                FOR UPDATE
                """,
            this::mapRow,
            matchId,
            accountId).stream().findFirst();
    }

    private Optional<MatchRow> lockPeer(MatchRow current) {
        if (current.pairKey() != null && !current.pairKey().isBlank()) {
            return jdbc.query(
                """
                    SELECT id, pair_key, account_id, match_account_id,
                           survey_type, user_agreed, match_agreed
                    FROM match_history
                    WHERE pair_key = ? AND id <> ?
                    LIMIT 1
                    FOR UPDATE
                    """,
                this::mapRow,
                current.pairKey(),
                current.id()).stream().findFirst();
        }
        return jdbc.query(
            """
                SELECT id, pair_key, account_id, match_account_id,
                       survey_type, user_agreed, match_agreed
                FROM match_history
                WHERE account_id = ?
                  AND match_account_id = ?
                  AND survey_type = ?
                ORDER BY created_at DESC
                LIMIT 1
                FOR UPDATE
                """,
            this::mapRow,
            current.matchedAccountId(),
            current.accountId(),
            current.surveyType()).stream().findFirst();
    }

    private MatchRow mapRow(
        java.sql.ResultSet resultSet,
        int rowNumber
    ) throws java.sql.SQLException {
        return new MatchRow(
            resultSet.getInt("id"),
            resultSet.getString("pair_key"),
            resultSet.getInt("account_id"),
            resultSet.getInt("match_account_id"),
            resultSet.getString("survey_type"),
            resultSet.getBoolean("user_agreed"),
            resultSet.getBoolean("match_agreed"));
    }

    private Map<String, Object> jsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record MatchRow(
        int id,
        String pairKey,
        int accountId,
        int matchedAccountId,
        String surveyType,
        boolean userAgreed,
        boolean matchAgreed
    ) {
    }
}
