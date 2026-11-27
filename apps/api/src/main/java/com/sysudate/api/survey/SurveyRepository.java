package com.sysudate.api.survey;

import java.util.Map;
import java.util.Optional;

public interface SurveyRepository {
    Optional<SurveyRecord> findBySerial(String serialNumber);

    Optional<SurveyRecord> findByAccountId(int accountId);

    boolean accountHasSurvey(int accountId, String surveyType);

    void insert(
        String surveyType,
        String serialNumber,
        Integer accountId,
        String email,
        Map<String, Object> data);

    void update(
        SurveyRecord survey,
        Map<String, Object> changes);

    Optional<Map<String, Object>> idempotentResult(String key);

    void saveIdempotentResult(
        String key,
        Integer accountId,
        String operation,
        Map<String, Object> result);

    record SurveyRecord(
        String table,
        String surveyType,
        String serialNumber,
        Integer accountId,
        String email,
        String contactEmail,
        Map<String, Object> data
    ) {
    }
}
