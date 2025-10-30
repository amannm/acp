package com.amannmalik.acp.server;

import com.amannmalik.acp.api.shared.ErrorResponse;

public final class HttpProblem extends RuntimeException {
    private final int status;
    private final ErrorResponse.ErrorType errorType;
    private final String code;
    private final String param;

    public HttpProblem(int status, ErrorResponse.ErrorType errorType, String code, String message) {
        this(status, errorType, code, message, null);
    }

    public HttpProblem(int status, ErrorResponse.ErrorType errorType, String code, String message, String param) {
        super(message);
        this.status = status;
        this.errorType = errorType;
        this.code = code;
        this.param = param;
    }

    public int status() {
        return status;
    }

    public ErrorResponse.ErrorType errorType() {
        return errorType;
    }

    public String code() {
        return code;
    }

    public String param() {
        return param;
    }
}
