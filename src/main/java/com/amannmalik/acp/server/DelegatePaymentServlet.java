package com.amannmalik.acp.server;

import com.amannmalik.acp.api.delegatepayment.DelegatePaymentConflictException;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentService;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentValidationException;
import com.amannmalik.acp.api.shared.ApiVersion;
import com.amannmalik.acp.api.shared.ErrorResponse;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import com.amannmalik.acp.codec.JsonDecodingException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class DelegatePaymentServlet extends HttpServlet {
    private static final String APPLICATION_JSON = "application/json";

    private final DelegatePaymentService service;
    private final DelegatePaymentJsonCodec codec;

    public DelegatePaymentServlet(DelegatePaymentService service, DelegatePaymentJsonCodec codec) {
        this.service = service;
        this.codec = codec;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleWithErrors(resp, () -> {
            validateHeaders(req);
            var idempotencyKey = requireIdempotencyKey(req);
            var delegateRequest = codec.readRequest(req.getInputStream());
            var response = service.create(delegateRequest, idempotencyKey);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.setContentType(APPLICATION_JSON);
            codec.writeResponse(resp.getOutputStream(), response);
        });
    }

    private void validateHeaders(HttpServletRequest req) {
        var apiVersion = req.getHeader("API-Version");
        if (apiVersion == null || !apiVersion.equals(ApiVersion.SUPPORTED)) {
            throw new IllegalArgumentException("API-Version MUST equal %s".formatted(ApiVersion.SUPPORTED));
        }
        var authorization = req.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("Authorization header is required");
        }
        var contentType = req.getHeader("Content-Type");
        if (contentType == null || !contentType.startsWith(APPLICATION_JSON)) {
            throw new IllegalArgumentException("Content-Type MUST be application/json");
        }
    }

    private String requireIdempotencyKey(HttpServletRequest req) {
        var idemKey = req.getHeader("Idempotency-Key");
        if (idemKey == null || idemKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        return idemKey;
    }

    private void handleWithErrors(HttpServletResponse resp, IOExceptionRunnable action) throws IOException {
        try {
            action.run();
        } catch (IOException e) {
            throw e;
        } catch (DelegatePaymentConflictException e) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, ErrorResponse.ErrorType.INVALID_REQUEST, "idempotency_conflict", e.getMessage());
        } catch (DelegatePaymentValidationException | JsonDecodingException | IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, ErrorResponse.ErrorType.INVALID_REQUEST, "invalid_request", e.getMessage());
        } catch (Exception e) {
            var message = e.getMessage() == null ? "Unexpected server error" : e.getMessage();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ErrorResponse.ErrorType.PROCESSING_ERROR, "processing_error", message);
        }
    }

    private void sendError(
            HttpServletResponse resp,
            int status,
            ErrorResponse.ErrorType type,
            String code,
            String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType(APPLICATION_JSON);
        codec.writeError(resp.getOutputStream(), new ErrorResponse(type, code, message, null));
    }

    @FunctionalInterface
    private interface IOExceptionRunnable {
        void run() throws IOException;
    }
}
