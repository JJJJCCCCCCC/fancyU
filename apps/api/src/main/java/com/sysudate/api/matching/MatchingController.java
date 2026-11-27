package com.sysudate.api.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.matching.MatchingRepository.ExchangeState;
import com.sysudate.api.survey.SurveyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
public class MatchingController {
    private final ObjectProvider<MatchingService> matchingService;
    private final ObjectProvider<SurveyService> surveyService;

    public MatchingController(
        ObjectProvider<MatchingService> matchingService,
        ObjectProvider<SurveyService> surveyService
    ) {
        this.matchingService = matchingService;
        this.surveyService = surveyService;
    }

    @GetMapping("/survey-status")
    public Map<String, Object> surveyStatus(
        @AuthenticationPrincipal CurrentAccount principal
    ) {
        CurrentAccount account = require(principal);
        return Map.of(
            "code", 200,
            "data", surveys().status(account.accountId()));
    }

    @GetMapping("/matches")
    public Map<String, Object> matches(
        @AuthenticationPrincipal CurrentAccount principal
    ) {
        return Map.of(
            "code", 200,
            "data", matching().history(require(principal).accountId()));
    }

    @PostMapping("/exchange-contact")
    public Map<String, Object> exchange(
        @AuthenticationPrincipal CurrentAccount principal,
        @Valid @RequestBody ExchangeRequest request
    ) {
        ExchangeState state = matching().agree(
            request.matchId(),
            require(principal).accountId());
        return Map.of(
            "code", 200,
            "msg", state.contactExchanged()
                ? "双方已同意，联系方式已显示"
                : "已同意交换联系方式，等待对方确认",
            "contact_exchanged", state.contactExchanged());
    }

    @PostMapping("/withdraw-consent")
    public Map<String, Object> withdraw(
        @AuthenticationPrincipal CurrentAccount principal
    ) {
        matching().withdraw(require(principal).accountId());
        return Map.of(
            "code", 200,
            "msg", "已撤回同意并退出所有匹配");
    }

    private CurrentAccount require(CurrentAccount principal) {
        if (principal == null) {
            throw new LegacyApiException(
                HttpStatus.UNAUTHORIZED,
                401,
                "未登录或登录已过期");
        }
        return principal;
    }

    private MatchingService matching() {
        MatchingService service = matchingService.getIfAvailable();
        if (service == null) {
            throw unavailable();
        }
        return service;
    }

    private SurveyService surveys() {
        SurveyService service = surveyService.getIfAvailable();
        if (service == null) {
            throw unavailable();
        }
        return service;
    }

    private LegacyApiException unavailable() {
        return new LegacyApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            503,
            "服务暂不可用");
    }

    public record ExchangeRequest(
        @JsonProperty("match_id") @Positive int matchId
    ) {
    }
}
