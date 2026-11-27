package com.sysudate.api.common.api;

import java.util.List;

public record ApiError(
    String code,
    String message,
    String requestId,
    List<FieldError> details
) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record FieldError(String field, String message) {
    }
}
