package com.amannmalik.acp.server;

import com.amannmalik.acp.api.shared.ErrorResponse;

final class HttpProblem extends RuntimeException {
    private final int status;
    private final ErrorResponse.ErrorType errorType;
    private final String code;
    private final String param;

    HttpProblem(int status, ErrorResponse.ErrorType errorType, String code, String message) {
        this(status, errorType, code, message, null);
    }

    HttpProblem(int status, ErrorResponse.ErrorType errorType, String code, String message, String param) {
        super(message);
        this.status = status;
        this.errorType = errorType;
        this.code = code;
        this.param = param;
    }

    int status() {
        return status;
    }

    ErrorResponse.ErrorType errorType() {
        return errorType;
    }

    String code() {
        return code;
    }

    String param() {
        return param;
    }
}
