package com.sysudate.api.ai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sysudate.api.common.api.LegacyApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

class AiAssistantClientTest {
    @Test
    void mapsDownstreamBadRequestToClientError() {
        RestClientException downstream = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            HttpHeaders.EMPTY,
            new byte[0],
            UTF_8);

        LegacyApiException mapped =
            AiAssistantClient.mapFailure(downstream);

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapped.code()).isEqualTo(400);
    }

    @Test
    void mapsOtherDownstreamFailuresToBadGateway() {
        LegacyApiException mapped =
            AiAssistantClient.mapFailure(new RestClientException("timeout"));

        assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(mapped.code()).isEqualTo(502);
    }
}
