package com.sysudate.api.survey;

import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.identity.verification.VerificationCodeService;
import com.sysudate.api.serial.domain.SerialNumber;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacySurveyController {
    private final ObjectProvider<SurveyService> surveyService;
    private final ObjectProvider<VerificationCodeService> verificationService;

    public LegacySurveyController(
        ObjectProvider<SurveyService> surveyService,
        ObjectProvider<VerificationCodeService> verificationService
    ) {
        this.surveyService = surveyService;
        this.verificationService = verificationService;
    }

    @GetMapping("/")
    public Map<String, Object> get(
        @RequestParam String action,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String serial,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) Integer userId,
        @AuthenticationPrincipal CurrentAccount principal
    ) {
        return switch (action) {
            case "generateSerial" -> Map.of(
                "code", 200,
                "serialNumber", SerialNumber.generate().value());
            case "query" -> Map.of(
                "code", 200,
                "msg", "查询成功",
                "data", authenticatedLookup(
                    principal,
                    requiredSerial(serial)));
            case "queryByUserId" -> Map.of(
                "code", 200,
                "msg", "查询成功",
                "data", authenticatedAccountLookup(
                    principal,
                    requireUserId(userId)));
            case "getSurveyData" -> getSurveyData(
                serial,
                email,
                code);
            default -> throw badRequest("无效请求");
        };
    }

    @PostMapping("/")
    public Map<String, Object> post(
        @RequestBody Map<String, Object> body,
        @RequestHeader(
            name = "Idempotency-Key",
            required = false)
        String idempotencyKey,
        @AuthenticationPrincipal CurrentAccount principal
    ) {
        String action = string(body, "action");
        return switch (action) {
            case "sendCode" -> sendCode(string(body, "email"));
            case "checkCode" -> checkCode(
                string(body, "email"),
                string(body, "code"));
            case "verifyMatch" -> verifyMatch(
                string(body, "serial"),
                string(body, "email"));
            case "submitSurvey" -> surveys().submit(
                "love",
                data(body),
                principal,
                idempotencyKey);
            case "submitFriendSurvey" -> surveys().submit(
                "friend",
                data(body),
                principal,
                idempotencyKey);
            case "updateSurvey", "modifySurvey" -> update(
                string(body, "serial"),
                string(body, "email"),
                data(body));
            default -> throw badRequest("无效请求");
        };
    }

    private Map<String, Object> getSurveyData(
        String serial,
        String email,
        String code
    ) {
        if (!verification().consume(email, code)) {
            throw badRequest("验证码错误或已过期");
        }
        SurveyRepository.SurveyRecord survey = surveys().verifiedSurvey(
            requiredSerial(serial),
            email);
        return Map.of("code", 200, "data", survey.data());
    }

    private Map<String, Object> authenticatedLookup(
        CurrentAccount principal,
        String serial
    ) {
        requireAuthenticated(principal);
        return surveys().publicLookup(serial);
    }

    private Map<String, Object> authenticatedAccountLookup(
        CurrentAccount principal,
        int accountId
    ) {
        requireAuthenticated(principal);
        return surveys().publicLookupByAccount(accountId);
    }

    private void requireAuthenticated(CurrentAccount principal) {
        if (principal == null || principal.accountId() <= 0) {
            throw new LegacyApiException(
                HttpStatus.UNAUTHORIZED,
                401,
                "Please sign in first");
        }
    }

    private Map<String, Object> sendCode(String email) {
        verification().send(email);
        return Map.of("code", 200, "msg", "验证码已发送");
    }

    private Map<String, Object> checkCode(String email, String code) {
        if (!verification().matches(email, code)) {
            throw badRequest("验证码错误或已过期");
        }
        return Map.of("code", 200, "msg", "验证通过");
    }

    private Map<String, Object> verifyMatch(
        String serial,
        String email
    ) {
        if (!surveys().emailMatches(requiredSerial(serial), email)) {
            throw new LegacyApiException(
                HttpStatus.FORBIDDEN,
                403,
                "序列号与邮箱不匹配");
        }
        verification().send(email);
        return Map.of("code", 200, "msg", "验证码已发送");
    }

    private Map<String, Object> update(
        String serial,
        String email,
        Map<String, Object> data
    ) {
        surveys().update(requiredSerial(serial), email, data);
        return Map.of("code", 200, "msg", "更新成功");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> body) {
        Object value = body.get("data");
        if (!(value instanceof Map<?, ?>)) {
            throw badRequest("缺少问卷数据");
        }
        return (Map<String, Object>) value;
    }

    private String string(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String string) || string.isBlank()) {
            throw badRequest(field + " 不能为空");
        }
        return string.trim();
    }

    private String requiredSerial(String serial) {
        if (serial == null || serial.isBlank()) {
            throw badRequest("序列号不能为空");
        }
        return serial;
    }

    private int requireUserId(Integer userId) {
        if (userId == null || userId <= 0) {
            throw badRequest("userId 参数无效");
        }
        return userId;
    }

    private SurveyService surveys() {
        SurveyService service = surveyService.getIfAvailable();
        if (service == null) {
            throw unavailable();
        }
        return service;
    }

    private VerificationCodeService verification() {
        VerificationCodeService service =
            verificationService.getIfAvailable();
        if (service == null) {
            throw unavailable();
        }
        return service;
    }

    private LegacyApiException badRequest(String message) {
        return new LegacyApiException(
            HttpStatus.BAD_REQUEST,
            400,
            message);
    }

    private LegacyApiException unavailable() {
        return new LegacyApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            503,
            "服务暂不可用");
    }
}
