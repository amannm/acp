package com.amannmalik.acp.server;

import com.amannmalik.acp.api.checkout.CheckoutSessionConflictException;
import com.amannmalik.acp.api.checkout.CheckoutSessionNotFoundException;
import com.amannmalik.acp.api.checkout.CheckoutSessionService;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCompleteRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionId;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionUpdateRequest;
import com.amannmalik.acp.api.shared.ApiVersion;
import com.amannmalik.acp.api.shared.ErrorResponse;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import com.amannmalik.acp.codec.JsonDecodingException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class CheckoutSessionServlet extends HttpServlet {
    private static final String APPLICATION_JSON = "application/json";

    private final CheckoutSessionService service;
    private final CheckoutSessionJsonCodec codec;

    public CheckoutSessionServlet(CheckoutSessionService service, CheckoutSessionJsonCodec codec) {
        this.service = service;
        this.codec = codec;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleWithErrors(resp, () -> {
            validateHeaders(req);
            var segments = pathSegments(req);
            if (segments.isEmpty()) {
                var request = codec.readCreateRequest(req.getInputStream());
                var session = service.create(request);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            var sessionId = new CheckoutSessionId(segments.get(0));
            if (segments.size() == 1) {
                var request = codec.readUpdateRequest(req.getInputStream());
                var session = service.update(sessionId, request);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            var action = segments.get(1);
            if ("complete".equals(action)) {
                var request = codec.readCompleteRequest(req.getInputStream());
                ensureIdempotencyKey(req);
                var session = service.complete(sessionId, request);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            if ("cancel".equals(action)) {
                var session = service.cancel(sessionId);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        });
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleWithErrors(resp, () -> {
            validateHeaders(req);
            var segments = pathSegments(req);
            if (segments.size() != 1) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            var session = service.retrieve(new CheckoutSessionId(segments.get(0)));
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType(APPLICATION_JSON);
            codec.writeCheckoutSession(resp.getOutputStream(), session);
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
    }

    private void ensureIdempotencyKey(HttpServletRequest req) {
        var idemKey = req.getHeader("Idempotency-Key");
        if (idemKey == null || idemKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for this endpoint");
        }
    }

    private void handleWithErrors(HttpServletResponse resp, IOExceptionRunnable action) throws IOException {
        try {
            action.run();
        } catch (IOException e) {
            throw e;
        } catch (CheckoutSessionNotFoundException e) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, ErrorResponse.ErrorType.INVALID_REQUEST, "not_found", e.getMessage());
        } catch (CheckoutSessionConflictException e) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, ErrorResponse.ErrorType.INVALID_REQUEST, "state_conflict", e.getMessage());
        } catch (JsonDecodingException | IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, ErrorResponse.ErrorType.INVALID_REQUEST, "invalid_request", e.getMessage());
        } catch (Exception e) {
            var message = e.getMessage() == null ? "Unexpected server error" : e.getMessage();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ErrorResponse.ErrorType.PROCESSING_ERROR, "internal_error", message);
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

    private static List<String> pathSegments(HttpServletRequest req) {
        var pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || pathInfo.equals("/")) {
            return List.of();
        }
        return Arrays.stream(pathInfo.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    @FunctionalInterface
    private interface IOExceptionRunnable {
        void run() throws IOException;
    }
}
