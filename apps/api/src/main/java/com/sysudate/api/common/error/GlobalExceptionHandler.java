package com.sysudate.api.common.error;

import com.sysudate.api.common.api.ApiError;
import com.sysudate.api.common.observability.RequestIdFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String UNHANDLED_EVENT_CODE = "UNHANDLED_EXCEPTION";
    private static final Logger LOGGER =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        List<ApiError.FieldError> details = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ApiError.FieldError(
                error.getField(),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
            .toList();
        ApiError error = new ApiError(
            "VALIDATION_ERROR",
            "Request validation failed",
            requestId(),
            details);

        return handleExceptionInternal(exception, error, headers, status, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(
        BusinessException exception
    ) {
        ApiError error = new ApiError(
            exception.code(),
            exception.getMessage(),
            requestId(),
            List.of());

        return ResponseEntity.status(exception.status()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        String requestId = requestId();
        logUnexpected(exception, requestId);
        ApiError error = new ApiError(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            requestId,
            List.of());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception,
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        Object responseBody = body instanceof ApiError
            ? body
            : mvcError(exception, status);
        return super.handleExceptionInternal(
            exception,
            responseBody,
            headers,
            status,
            request);
    }

    private ApiError mvcError(Exception exception, HttpStatusCode status) {
        if (exception instanceof HttpRequestMethodNotSupportedException) {
            return error("METHOD_NOT_ALLOWED", "Request method is not supported");
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return error("MALFORMED_REQUEST", "Request body is malformed");
        }
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            return error("UNSUPPORTED_MEDIA_TYPE", "Request media type is not supported");
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return error("BAD_REQUEST", "Request could not be processed");
        }
        if (status.is4xxClientError()) {
            return error("HTTP_" + status.value(), "Request could not be processed");
        }

        logUnexpected(exception, requestId());
        return error("INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ApiError error(String code, String message) {
        return new ApiError(code, message, requestId(), List.of());
    }

    private void logUnexpected(Exception exception, String requestId) {
        LOGGER.error(
            "eventCode={} requestId={} exceptionType={}",
            UNHANDLED_EVENT_CODE,
            requestId,
            exception.getClass().getName());
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
