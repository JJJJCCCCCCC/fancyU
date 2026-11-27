package com.sysudate.api.survey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SurveyVisibilityPolicy {
    private static final Map<String, Set<String>> MODULE_FIELDS = Map.of(
        "基础背景", Set.of(
            "nickname", "sex", "grade", "campus", "major", "hometown",
            "school"),
        "个人特质", Set.of(
            "constellation", "mbti", "interests", "interest_custom"),
        "外貌偏好", Set.of(
            "height", "beauty_self", "body_type", "fitness_level"),
        "匹配问题", Set.of(
            "q1_co_dependent", "q2_conflict", "q3_ritual",
            "q4_consumption", "q5_life_focus", "q6_emotion",
            "agree_topics"),
        "自我描述", Set.of("self_description"),
        "自我介绍", Set.of("self_description"),
        "理想型", Set.of("ideal_description"),
        "兴趣爱好", Set.of("interests", "interest_custom"),
        "照片", Set.of("photos", "avatar"));

    private final ObjectMapper objectMapper;

    public SurveyVisibilityPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> publicView(Map<String, Object> survey) {
        Map<String, Object> result = new LinkedHashMap<>();
        copyIfPresent(survey, result, "serial_number");
        copyIfPresent(survey, result, "nickname");
        List<String> modules = modules(survey.get("display_self"));
        for (String module : modules) {
            for (String field : MODULE_FIELDS.getOrDefault(
                module,
                Set.of())) {
                copyIfPresent(survey, result, field);
            }
        }
        result.put("display_self", modules);
        return result;
    }

    private List<String> modules(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(MODULE_FIELDS::containsKey)
                .toList();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                List<String> parsed = objectMapper.readValue(
                    string,
                    new TypeReference<>() {
                    });
                return parsed.stream()
                    .filter(MODULE_FIELDS::containsKey)
                    .toList();
            } catch (JsonProcessingException exception) {
                return List.of();
            }
        }
        return List.of();
    }

    private void copyIfPresent(
        Map<String, Object> source,
        Map<String, Object> target,
        String field
    ) {
        if (source.containsKey(field) && source.get(field) != null) {
            target.put(field, source.get(field));
        }
    }
}
