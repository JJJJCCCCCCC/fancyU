package com.sysudate.api.ai;

import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.JwtService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiAssistantClient {
    private final RestClient client;
    private final JwtService jwtService;

    public AiAssistantClient(
        RestClient.Builder builder,
        JwtService jwtService,
        @Value("${sysudate.ai.base-url}") String baseUrl,
        @Value("${sysudate.ai.timeout}") Duration timeout
    ) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = builder
            .requestFactory(requestFactory)
            .baseUrl(baseUrl)
            .build();
        this.jwtService = jwtService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new LegacyApiException(
                HttpStatus.BAD_REQUEST,
                400,
                "无效的请求参数");
        }
        try {
            return client.post()
                .uri("/v1/chat")
                .header(
                    "Authorization",
                    "Bearer " + jwtService.issueService("sysudate-api"))
                .header("X-Request-ID", requestId())
                .body(Map.of("messages", messages))
                .retrieve()
                .body(Map.class);
        } catch (RestClientException exception) {
            throw mapFailure(exception);
        }
    }

    static LegacyApiException mapFailure(RestClientException exception) {
        if (exception instanceof HttpClientErrorException clientError
            && (clientError.getStatusCode() == HttpStatus.BAD_REQUEST
                || clientError.getStatusCode()
                    == HttpStatus.UNPROCESSABLE_ENTITY)) {
            return new LegacyApiException(
                HttpStatus.BAD_REQUEST,
                400,
                "查询内容不符合 AI 安全规则");
        }
        return new LegacyApiException(
            HttpStatus.BAD_GATEWAY,
            502,
            "AI 服务暂不可用");
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? UUID.randomUUID().toString() : value;
    }
}
