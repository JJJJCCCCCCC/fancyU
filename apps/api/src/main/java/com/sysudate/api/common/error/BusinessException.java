package com.sysudate.api.common.error;

import java.util.Objects;
import org.springframework.http.HttpStatus;

public final class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.code = Objects.requireNonNull(code, "code");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
