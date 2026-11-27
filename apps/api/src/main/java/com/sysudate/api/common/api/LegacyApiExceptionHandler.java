package com.sysudate.api.common.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LegacyApiExceptionHandler {
    @ExceptionHandler(LegacyApiException.class)
    public ResponseEntity<Map<String, Object>> handle(
        LegacyApiException exception
    ) {
        return ResponseEntity.status(exception.status()).body(Map.of(
            "code", exception.code(),
            "msg", exception.getMessage()));
    }
}
