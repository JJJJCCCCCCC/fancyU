package com.sysudate.api.survey;

import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.notification.NotificationEvent;
import com.sysudate.api.notification.NotificationOutboxRepository;
import com.sysudate.api.serial.domain.SerialNumber;
import com.sysudate.api.survey.SurveyRepository.SurveyRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty("spring.datasource.url")
public class SurveyService {
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "nickname",
        "interests",
        "interest_custom",
        "self_description",
        "ideal_description",
        "feedback");
    private static final List<String> BLOCKED_WORDS = List.of(
        "赌博", "毒品", "诈骗", "传销", "卖淫", "枪支", "代写论文");

    private final SurveyRepository surveys;
    private final SurveyVisibilityPolicy visibility;
    private final ObjectProvider<NotificationOutboxRepository> outbox;

    public SurveyService(
        SurveyRepository surveys,
        SurveyVisibilityPolicy visibility,
        ObjectProvider<NotificationOutboxRepository> outbox
    ) {
        this.surveys = surveys;
        this.visibility = visibility;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> submit(
        String surveyType,
        Map<String, Object> rawData,
        CurrentAccount principal,
        String idempotencyKey
    ) {
        String normalizedType = normalizeType(surveyType);
        Integer accountId = principal == null
            ? null
            : principal.accountId();
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            Optional<Map<String, Object>> existing =
                surveys.idempotentResult(key);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        if (accountId != null
            && surveys.accountHasSurvey(accountId, normalizedType)) {
            throw conflict("该账号已填写此类问卷");
        }

        Map<String, Object> data = sanitize(rawData);
        String email = resolveEmail(data, principal);
        String serialNumber = SerialNumber.generate().value();
        surveys.insert(
            normalizedType,
            serialNumber,
            accountId,
            email,
            data);
        Map<String, Object> result = Map.of(
            "code", 200,
            "msg", "提交成功",
            "serialNumber", serialNumber);
        if (key != null) {
            surveys.saveIdempotentResult(
                key,
                accountId,
                "submit-" + normalizedType,
                result);
        }
        NotificationOutboxRepository repository = outbox.getIfAvailable();
        if (repository != null && email != null) {
            repository.enqueue(NotificationEvent.surveyConfirmation(
                email,
                serialNumber,
                requestId()));
        }
        return result;
    }

    public Map<String, Object> publicLookup(String serialNumber) {
        SurveyRecord survey = requireSurvey(serialNumber);
        return visibility.publicView(survey.data());
    }

    public Map<String, Object> publicLookupByAccount(int accountId) {
        SurveyRecord survey = surveys.findByAccountId(accountId)
            .orElseThrow(() -> notFound("未找到该用户"));
        return visibility.publicView(survey.data());
    }

    public SurveyRecord verifiedSurvey(
        String serialNumber,
        String email
    ) {
        SurveyRecord survey = requireSurvey(serialNumber);
        if (!emailMatches(survey, email)) {
            throw new LegacyApiException(
                HttpStatus.FORBIDDEN,
                403,
                "序列号与邮箱不匹配");
        }
        return survey;
    }

    @Transactional
    public void update(
        String serialNumber,
        String email,
        Map<String, Object> changes
    ) {
        SurveyRecord survey = verifiedSurvey(serialNumber, email);
        surveys.update(survey, sanitize(changes));
    }

    public boolean emailMatches(String serialNumber, String email) {
        return surveys.findBySerial(normalizeSerial(serialNumber))
            .map(survey -> emailMatches(survey, email))
            .orElse(false);
    }

    public Map<String, Object> status(int accountId) {
        return Map.of(
            "love", Map.of(
                "filled", surveys.accountHasSurvey(accountId, "love")),
            "friend", Map.of(
                "filled", surveys.accountHasSurvey(accountId, "friend")));
    }

    private SurveyRecord requireSurvey(String serialNumber) {
        return surveys.findBySerial(normalizeSerial(serialNumber))
            .orElseThrow(() -> notFound("未找到该序列号对应的问卷"));
    }

    private boolean emailMatches(SurveyRecord survey, String rawEmail) {
        if (rawEmail == null) {
            return false;
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        return email.equalsIgnoreCase(survey.email())
            || email.equalsIgnoreCase(survey.contactEmail());
    }

    private Map<String, Object> sanitize(Map<String, Object> rawData) {
        if (rawData == null) {
            throw badRequest("缺少问卷数据");
        }
        Map<String, Object> data = new LinkedHashMap<>(rawData);
        if (data.containsKey("own_campus")
            && !data.containsKey("campus")) {
            data.put("campus", data.get("own_campus"));
        }
        for (String field : SENSITIVE_FIELDS) {
            Object value = data.get(field);
            if (value == null) {
                continue;
            }
            String text = Objects.toString(value);
            for (String blocked : BLOCKED_WORDS) {
                if (text.contains(blocked)) {
                    throw badRequest("内容包含不允许的词语");
                }
            }
        }
        data.remove("serial_number");
        data.remove("account_id");
        data.remove("survey_type");
        data.remove("account_email");
        return data;
    }

    private String resolveEmail(
        Map<String, Object> data,
        CurrentAccount principal
    ) {
        Object contactEmail = data.get("contact_email");
        if (contactEmail instanceof String value && !value.isBlank()) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
        if (principal != null) {
            return principal.email();
        }
        return null;
    }

    private String normalizeType(String surveyType) {
        return switch (surveyType) {
            case "love" -> "love";
            case "friend" -> "friend";
            default -> throw badRequest("问卷类型无效");
        };
    }

    private String normalizeSerial(String serialNumber) {
        try {
            return SerialNumber.parse(serialNumber).value();
        } catch (IllegalArgumentException exception) {
            throw badRequest("序列号格式无效");
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > 120) {
            throw badRequest("幂等键过长");
        }
        return normalized;
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? UUID.randomUUID().toString() : value;
    }

    private LegacyApiException badRequest(String message) {
        return new LegacyApiException(
            HttpStatus.BAD_REQUEST,
            400,
            message);
    }

    private LegacyApiException conflict(String message) {
        return new LegacyApiException(
            HttpStatus.CONFLICT,
            400,
            message);
    }

    private LegacyApiException notFound(String message) {
        return new LegacyApiException(
            HttpStatus.NOT_FOUND,
            404,
            message);
    }
}
