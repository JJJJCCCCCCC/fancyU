package com.sysudate.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sysudate.api.common.api.ApiError;
import com.sysudate.api.common.error.BusinessException;
import com.sysudate.api.common.error.GlobalExceptionHandler;
import com.sysudate.api.common.observability.RequestIdFilter;
import com.sysudate.api.common.security.SecurityConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SystemHealthController.class)
@Import({
    SecurityConfiguration.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
class SystemHealthControllerTest {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern REQUEST_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    @Autowired
    private MockMvc mvc;

    private final MockMvc errorMvc = MockMvcBuilders
        .standaloneSetup(new SystemHealthController(), new ErrorTestController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilters(new RequestIdFilter())
        .build();

    @Test
    void healthReturnsUpAndEchoesValidRequestId() throws Exception {
        mvc.perform(get("/api/v1/system/health").header(REQUEST_ID_HEADER, "test-request"))
            .andExpect(status().isOk())
            .andExpect(header().string(REQUEST_ID_HEADER, "test-request"))
            .andExpect(jsonPath("$.status").value("UP"));

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void blankRequestIdIsReplacedWithValidGeneratedId() throws Exception {
        String requestId = mvc.perform(
                get("/api/v1/system/health").header(REQUEST_ID_HEADER, ""))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(REQUEST_ID_HEADER);

        assertThat(requestId)
            .isNotBlank()
            .matches(REQUEST_ID_PATTERN);
    }

    @Test
    void invalidRequestIdIsReplacedWithValidGeneratedId() throws Exception {
        String invalidRequestId = "contains spaces!";

        String requestId = mvc.perform(
                get("/api/v1/system/health").header(REQUEST_ID_HEADER, invalidRequestId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(REQUEST_ID_HEADER);

        assertThat(requestId)
            .isNotEqualTo(invalidRequestId)
            .matches(REQUEST_ID_PATTERN);
    }

    @Test
    void unauthorizedResponsesIncludeRequestIdAndClearMdc() throws Exception {
        String requestId = mvc.perform(get("/api/v1/system/not-a-health-endpoint"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists(REQUEST_ID_HEADER))
            .andReturn()
            .getResponse()
            .getHeader(REQUEST_ID_HEADER);

        assertThat(requestId).matches(REQUEST_ID_PATTERN);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void nestedActuatorHealthPathsRequireAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/db"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void healthPostRequestsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/system/health")
                .header(REQUEST_ID_HEADER, "post-api-health"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(REQUEST_ID_HEADER, "post-api-health"));
        mvc.perform(post("/actuator/health")
                .header(REQUEST_ID_HEADER, "post-actuator-health"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(REQUEST_ID_HEADER, "post-actuator-health"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void apiErrorDefensivelyCopiesDetails() {
        List<ApiError.FieldError> details = new ArrayList<>();
        details.add(new ApiError.FieldError("email", "must not be blank"));

        ApiError error = new ApiError("VALIDATION_ERROR", "Invalid request", "request-1", details);
        details.clear();

        assertThat(error.details())
            .containsExactly(new ApiError.FieldError("email", "must not be blank"));
        assertThatThrownBy(() -> error.details().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void businessExceptionCarriesStatusAndCode() {
        BusinessException exception =
            new BusinessException(HttpStatus.CONFLICT, "SERIAL_EXISTS", "Serial already exists");

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo("SERIAL_EXISTS");
        assertThat(exception.getMessage()).isEqualTo("Serial already exists");
    }

    @Test
    void validationExceptionMapsFieldErrorsToBadRequest() throws Exception {
        errorMvc.perform(post("/test/validation")
                .header(REQUEST_ID_HEADER, "request-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.requestId").value("request-validation"))
            .andExpect(jsonPath("$.details[0].field").value("email"))
            .andExpect(jsonPath("$.details[0].message").value("must not be blank"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void businessExceptionMapsDeclaredStatusAndCode() throws Exception {
        errorMvc.perform(get("/test/business")
                .header(REQUEST_ID_HEADER, "request-business"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SERIAL_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Serial was not found"))
            .andExpect(jsonPath("$.requestId").value("request-business"))
            .andExpect(jsonPath("$.details").isEmpty());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void methodNotSupportedPreservesStatusWithoutLeakingFrameworkMessage() throws Exception {
        MvcResult result = errorMvc.perform(post("/api/v1/system/health")
                .header(REQUEST_ID_HEADER, "request-method"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("GET")))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
            .andExpect(jsonPath("$.message").value("Request method is not supported"))
            .andExpect(jsonPath("$.requestId").value("request-method"))
            .andReturn();

        assertThat(result.getResponse().getContentAsString())
            .doesNotContain("Request method 'POST' is not supported");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void malformedJsonPreservesBadRequestWithoutLeakingBody() throws Exception {
        String secret = "secret-json-value";

        MvcResult result = errorMvc.perform(post("/test/validation")
                .header(REQUEST_ID_HEADER, "request-json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + secret + "\""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.message").value("Request body is malformed"))
            .andExpect(jsonPath("$.requestId").value("request-json"))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void unsupportedMediaTypePreservesStatusWithoutLeakingBody() throws Exception {
        String secret = "secret-media-payload";

        MvcResult result = errorMvc.perform(post("/test/validation")
                .header(REQUEST_ID_HEADER, "request-media")
                .contentType(MediaType.TEXT_PLAIN)
                .content(secret))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
            .andExpect(jsonPath("$.message").value("Request media type is not supported"))
            .andExpect(jsonPath("$.requestId").value("request-media"))
            .andReturn();

        String supportedMediaTypes = result.getResponse().getHeaderNames().stream()
            .filter(name -> name.equalsIgnoreCase("Accept")
                || name.equalsIgnoreCase("Accept-Post"))
            .map(name -> result.getResponse().getHeader(name))
            .collect(Collectors.joining(","));
        assertThat(supportedMediaTypes).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secret);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void missingRequestParameterRemainsBadRequest() throws Exception {
        errorMvc.perform(get("/test/number")
                .header(REQUEST_ID_HEADER, "request-missing"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("Request could not be processed"))
            .andExpect(jsonPath("$.requestId").value("request-missing"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void requestParameterTypeMismatchRemainsBadRequest() throws Exception {
        errorMvc.perform(get("/test/number")
                .header(REQUEST_ID_HEADER, "request-type")
                .param("value", "not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("Request could not be processed"))
            .andExpect(jsonPath("$.requestId").value("request-type"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void unexpectedExceptionLogsOnlySafeMetadata() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            errorMvc.perform(get("/test/unexpected")
                    .header(REQUEST_ID_HEADER, "request-unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.requestId").value("request-unexpected"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logContent = appender.list.stream()
            .map(event -> event.getFormattedMessage() + " "
                + (event.getThrowableProxy() == null
                    ? ""
                    : event.getThrowableProxy().getMessage()))
            .collect(Collectors.joining("\n"));
        assertThat(logContent)
            .contains(
                "eventCode=UNHANDLED_EXCEPTION",
                "requestId=request-unexpected",
                "exceptionType=java.lang.IllegalStateException")
            .doesNotContain(ErrorTestController.SECRET);
        assertThat(appender.list)
            .isNotEmpty()
            .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private record ValidationInput(@NotBlank String email) {
    }

    @RestController
    @RequestMapping("/test")
    private static final class ErrorTestController {
        private static final String SECRET = "database password is secret";

        @PostMapping(
            path = "/validation",
            consumes = MediaType.APPLICATION_JSON_VALUE
        )
        void validate(@Valid @RequestBody ValidationInput input) {
        }

        @GetMapping("/business")
        void businessFailure() {
            throw new BusinessException(
                HttpStatus.NOT_FOUND,
                "SERIAL_NOT_FOUND",
                "Serial was not found");
        }

        @GetMapping("/unexpected")
        void unexpectedFailure() {
            throw new IllegalStateException(SECRET);
        }

        @GetMapping("/number")
        void number(@RequestParam int value) {
        }
    }
}
