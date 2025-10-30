package com.amannmalik.acp.server;

import com.amannmalik.acp.api.checkout.CheckoutSessionConflictException;
import com.amannmalik.acp.api.checkout.CheckoutSessionMethodNotAllowedException;
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
import java.util.Locale;

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
        handleWithErrors(req, resp, () -> {
            validateHeaders(req);
            var segments = pathSegments(req);
            if (segments.isEmpty()) {
                requireJsonPayload(req);
                var request = codec.readCreateRequest(req.getInputStream());
                var session = service.create(request, normalizeHeader(req.getHeader("Idempotency-Key")));
                resp.setStatus(HttpServletResponse.SC_CREATED);
                propagateCorrelationHeaders(req, resp);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            var sessionId = new CheckoutSessionId(segments.get(0));
            if (segments.size() == 1) {
                requireJsonPayload(req);
                var request = codec.readUpdateRequest(req.getInputStream());
                var session = service.update(sessionId, request);
                resp.setStatus(HttpServletResponse.SC_OK);
                propagateCorrelationHeaders(req, resp);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            var action = segments.get(1);
            if ("complete".equals(action)) {
                requireJsonPayload(req);
                var request = codec.readCompleteRequest(req.getInputStream());
                var idempotencyKey = ensureIdempotencyKey(req);
                var session = service.complete(sessionId, request, idempotencyKey);
                resp.setStatus(HttpServletResponse.SC_OK);
                propagateCorrelationHeaders(req, resp);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            if ("cancel".equals(action)) {
                var session = service.cancel(sessionId);
                resp.setStatus(HttpServletResponse.SC_OK);
                propagateCorrelationHeaders(req, resp);
                resp.setContentType(APPLICATION_JSON);
                codec.writeCheckoutSession(resp.getOutputStream(), session);
                return;
            }
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        });
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleWithErrors(req, resp, () -> {
            validateHeaders(req);
            var segments = pathSegments(req);
            if (segments.size() != 1) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            var session = service.retrieve(new CheckoutSessionId(segments.get(0)));
            resp.setStatus(HttpServletResponse.SC_OK);
            propagateCorrelationHeaders(req, resp);
            resp.setContentType(APPLICATION_JSON);
            codec.writeCheckoutSession(resp.getOutputStream(), session);
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

    private String ensureIdempotencyKey(HttpServletRequest req) {
        var idemKey = normalizeHeader(req.getHeader("Idempotency-Key"));
        if (idemKey == null) {
            throw new HttpProblem(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "missing_idempotency_key",
                    "Idempotency-Key is required for this endpoint");
        }
        return idemKey;
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
        } catch (CheckoutSessionNotFoundException e) {
            sendError(
                    resp,
                    HttpServletResponse.SC_NOT_FOUND,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "not_found",
                    e.getMessage(),
                    null,
                    req);
        } catch (CheckoutSessionConflictException e) {
            sendError(
                    resp,
                    HttpServletResponse.SC_CONFLICT,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "state_conflict",
                    e.getMessage(),
                    null,
                    req);
        } catch (CheckoutSessionMethodNotAllowedException e) {
            resp.setHeader("Allow", "POST");
            sendError(
                    resp,
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "method_not_allowed",
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
                    "internal_error",
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

    private static List<String> pathSegments(HttpServletRequest req) {
        var pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || pathInfo.equals("/")) {
            return List.of();
        }
        return Arrays.stream(pathInfo.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
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

    private static String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @FunctionalInterface
    private interface IOExceptionRunnable {
        void run() throws IOException;
    }
}
