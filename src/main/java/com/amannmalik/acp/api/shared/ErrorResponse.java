package com.amannmalik.acp.api.shared;

import com.amannmalik.acp.util.Ensure;

public record ErrorResponse(ErrorType type, String code, String message, String param) {
    public enum ErrorType {
        INVALID_REQUEST,
        REQUEST_NOT_IDEMPOTENT,
        RATE_LIMIT_EXCEEDED,
        PROCESSING_ERROR,
        SERVICE_UNAVAILABLE
    }

    public ErrorResponse {
        type = Ensure.notNull("error.type", type);
        code = Ensure.nonBlank("error.code", code);
        message = Ensure.nonBlank("error.message", message);
        if (param != null && param.isBlank()) {
            throw new IllegalArgumentException("error.param MUST be non-blank when provided");
        }
    }
}
