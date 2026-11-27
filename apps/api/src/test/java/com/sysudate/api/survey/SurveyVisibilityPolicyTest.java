package com.sysudate.api.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyVisibilityPolicyTest {
    private final SurveyVisibilityPolicy policy =
        new SurveyVisibilityPolicy(new ObjectMapper());

    @Test
    void missingDisplaySettingsFailClosed() {
        Map<String, Object> result = policy.publicView(Map.of(
            "serial_number", "SYSU-550E8400-E29B-41D4-A716-446655440000",
            "nickname", "student",
            "email", "private@sysu.edu.cn",
            "contact_email", "contact@example.com",
            "self_description", "private text"));

        assertThat(result).containsOnlyKeys(
            "serial_number",
            "nickname",
            "display_self");
        assertThat(result.get("display_self")).isEqualTo(List.of());
    }

    @Test
    void onlySelectedModulesAreVisible() {
        Map<String, Object> result = policy.publicView(Map.of(
            "serial_number", "SYSU-550E8400-E29B-41D4-A716-446655440000",
            "nickname", "student",
            "display_self", "[\"基础背景\",\"自我描述\"]",
            "grade", "大三",
            "major", "计算机",
            "mbti", "INTJ",
            "self_description", "hello"));

        assertThat(result)
            .containsEntry("grade", "大三")
            .containsEntry("major", "计算机")
            .containsEntry("self_description", "hello")
            .doesNotContainKey("mbti");
    }
}
