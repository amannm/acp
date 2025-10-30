package com.amannmalik.acp.server;

import com.amannmalik.acp.api.delegatepayment.*;
import com.amannmalik.acp.api.shared.ApiVersion;
import com.amannmalik.acp.api.shared.ErrorResponse;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import com.amannmalik.acp.codec.JsonDecodingException;
import com.amannmalik.acp.server.security.RequestAuthenticator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.Locale;

public final class DelegatePaymentServlet extends HttpServlet {
    private static final String APPLICATION_JSON = "application/json";

    private final DelegatePaymentService service;
    private final DelegatePaymentJsonCodec codec;
    private final RequestAuthenticator authenticator;

    public DelegatePaymentServlet(
            DelegatePaymentService service, DelegatePaymentJsonCodec codec, RequestAuthenticator authenticator) {
        this.service = service;
        this.codec = codec;
        this.authenticator = authenticator;
    }

    private static String normalizeHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static byte[] readBody(HttpServletRequest request) throws IOException {
        try (var inputStream = request.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private static void propagateCorrelationHeaders(HttpServletRequest req, HttpServletResponse resp) {
        var requestId = req.getHeader("Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            resp.setHeader("Request-Id", requestId);
        }
        var idempotencyKey = req.getHeader("Idempotency-Key");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            resp.setHeader("Idempotency-Key", idempotencyKey);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleWithErrors(req, resp, () -> {
            validateHeaders(req);
            requireJsonPayload(req);
            var body = readBody(req);
            authenticator.authenticate(req, body);
            var idempotencyKey = normalizeHeader(req.getHeader("Idempotency-Key"));
            var delegateRequest = codec.readRequest(new java.io.ByteArrayInputStream(body));
            var response = service.create(delegateRequest, idempotencyKey);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            propagateCorrelationHeaders(req, resp);
            resp.setContentType(APPLICATION_JSON);
            codec.writeResponse(resp.getOutputStream(), response);
        });
    }

    private void validateHeaders(HttpServletRequest req) {
        var apiVersion = req.getHeader("API-Version");
        if (apiVersion == null) {
            throw new HttpProblem(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "missing_api_version",
                    "API-Version header is required");
        }
        if (!ApiVersion.SUPPORTED.equals(apiVersion)) {
            throw new HttpProblem(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unsupported_api_version",
                    "API-Version MUST equal %s".formatted(ApiVersion.SUPPORTED));
        }
        var authorization = req.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new HttpProblem(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unauthorized",
                    "Authorization header is required");
        }
    }

    private void requireJsonPayload(HttpServletRequest req) {
        var contentType = req.getHeader("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(APPLICATION_JSON)) {
            throw new HttpProblem(
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unsupported_media_type",
                    "Content-Type MUST be application/json");
        }
    }

    private void handleWithErrors(HttpServletRequest req, HttpServletResponse resp, IOExceptionRunnable action)
            throws IOException {
        try {
            action.run();
        } catch (HttpProblem problem) {
            sendError(resp, problem.status(), problem.errorType(), problem.code(), problem.getMessage(), problem.param(), req);
        } catch (IOException e) {
            throw e;
        } catch (DelegatePaymentIdempotencyConflictException e) {
            sendError(
                    resp,
                    HttpServletResponse.SC_CONFLICT,
                    ErrorResponse.ErrorType.REQUEST_NOT_IDEMPOTENT,
                    "idempotency_conflict",
                    e.getMessage(),
                    null,
                    req);
        } catch (DelegatePaymentConflictException e) {
            sendError(
                    resp,
                    HttpServletResponse.SC_CONFLICT,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "idempotency_conflict",
                    e.getMessage(),
                    null,
                    req);
        } catch (DelegatePaymentValidationException e) {
            sendError(
                    resp,
                    422,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "invalid_request",
                    e.getMessage(),
                    null,
                    req);
        } catch (JsonDecodingException | IllegalArgumentException e) {
            sendError(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "invalid_request",
                    e.getMessage(),
                    null,
                    req);
        } catch (Exception e) {
            var message = e.getMessage() == null ? "Unexpected server error" : e.getMessage();
            sendError(
                    resp,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ErrorResponse.ErrorType.PROCESSING_ERROR,
                    "processing_error",
                    message,
                    null,
                    req);
        }
    }

    private void sendError(
            HttpServletResponse resp,
            int status,
            ErrorResponse.ErrorType type,
            String code,
            String message,
            String param,
            HttpServletRequest req) throws IOException {
        resp.setStatus(status);
        propagateCorrelationHeaders(req, resp);
        resp.setContentType(APPLICATION_JSON);
        codec.writeError(resp.getOutputStream(), new ErrorResponse(type, code, message, param));
    }

    @FunctionalInterface
    private interface IOExceptionRunnable {
        void run() throws IOException;
    }
}
