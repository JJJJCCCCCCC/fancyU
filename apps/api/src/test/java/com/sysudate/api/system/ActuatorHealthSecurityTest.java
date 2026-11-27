package com.sysudate.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sysudate.api.common.observability.RequestIdFilter;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
    "management.endpoints.web.exposure.include=health,prometheus"
})
@AutoConfigureMockMvc
class ActuatorHealthSecurityTest {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern REQUEST_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    @Autowired
    private MockMvc mvc;

    @Test
    void actuatorHealthEchoesValidRequestId() throws Exception {
        mvc.perform(get("/actuator/health")
                .header(REQUEST_ID_HEADER, "actuator-health-get"))
            .andExpect(status().isOk())
            .andExpect(header().string(REQUEST_ID_HEADER, "actuator-health-get"))
            .andExpect(jsonPath("$.status").value("UP"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void actuatorHealthGeneratesRequestIdWhenMissing() throws Exception {
        String requestId = mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists(REQUEST_ID_HEADER))
            .andExpect(jsonPath("$.status").value("UP"))
            .andReturn()
            .getResponse()
            .getHeader(REQUEST_ID_HEADER);

        assertThat(requestId).matches(REQUEST_ID_PATTERN);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void actuatorHealthPostRequiresAuthentication() throws Exception {
        mvc.perform(post("/actuator/health")
                .header(REQUEST_ID_HEADER, "actuator-health-post"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(REQUEST_ID_HEADER, "actuator-health-post"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void prometheusMetricsAreAvailableToTheInternalScraper()
        throws Exception {
        mvc.perform(get("/actuator/prometheus")
                .header(REQUEST_ID_HEADER, "prometheus-scrape"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                REQUEST_ID_HEADER,
                "prometheus-scrape"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
