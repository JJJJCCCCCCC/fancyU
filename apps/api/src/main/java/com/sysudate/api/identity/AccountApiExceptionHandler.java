package com.sysudate.api.identity;

import com.sysudate.api.common.api.LegacyApiException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AccountController.class)
public class AccountApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(
        MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + " 不能为空")
            .orElse("请求参数不完整");
        return legacy(HttpStatus.BAD_REQUEST, 400, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> malformed() {
        return legacy(HttpStatus.BAD_REQUEST, 400, "请求内容格式错误");
    }

    @ExceptionHandler(LegacyApiException.class)
    ResponseEntity<Map<String, Object>> business(
        LegacyApiException exception
    ) {
        return legacy(
            exception.status(),
            exception.code(),
            exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> legacy(
        HttpStatus status,
        int code,
        String message
    ) {
        return ResponseEntity.status(status).body(Map.of(
            "code", code,
            "msg", message));
    }
}
