package com.sysudate.api.common.api;

import org.springframework.http.HttpStatus;

public final class LegacyApiException extends RuntimeException {
    private final HttpStatus status;
    private final int code;

    public LegacyApiException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public int code() {
        return code;
    }
}
