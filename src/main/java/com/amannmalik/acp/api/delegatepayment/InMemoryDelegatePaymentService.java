package com.amannmalik.acp.api.delegatepayment;

import com.amannmalik.acp.api.checkout.model.CheckoutSessionId;
import com.amannmalik.acp.api.delegatepayment.model.Allowance;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentResponse;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryDelegatePaymentService implements DelegatePaymentService, DelegatePaymentTokenValidator {
    private static final String TOKEN_PARAM = "$.payment_data.token";

    private final ConcurrentMap<String, StoredRecord> idempotencyStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StoredToken> tokens = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDelegatePaymentService() {
        this(Clock.systemUTC());
    }

    public InMemoryDelegatePaymentService(Clock clock) {
        this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
    }

    private static Map<String, String> responseMetadata(DelegatePaymentRequest request, String idempotencyKey) {
        var map = new LinkedHashMap<>(request.metadata());
        map.putIfAbsent("merchant_id", request.allowance().merchantId());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            map.put("idempotency_key", idempotencyKey);
        }
        return Map.copyOf(map);
    }

    private static String nextTokenId() {
        var uuid = UUID.randomUUID().toString().replace("-", "");
        return "vt_%s".formatted(uuid.substring(0, 16));
    }

    @Override
    public DelegatePaymentResponse create(DelegatePaymentRequest request, String idempotencyKey) {
        validateAllowance(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return issueToken(request, null);
        }
        var stored = idempotencyStore.compute(idempotencyKey, (key, current) -> {
            if (current == null) {
                return new StoredRecord(request, issueToken(request, idempotencyKey));
            }
            if (!current.request().equals(request)) {
                throw new DelegatePaymentIdempotencyConflictException(
                        "Same Idempotency-Key used with different parameters");
            }
            return current;
        });
        return stored.response();
    }

    private void validateAllowance(DelegatePaymentRequest request) {
        var allowance = request.allowance();
        if (allowance.maxAmount().value() <= 0L) {
            throw new DelegatePaymentValidationException(
                    "allowance.max_amount MUST be > 0",
                    "invalid_card",
                    "$.allowance.max_amount");
        }
        var now = clock.instant();
        if (!allowance.expiresAt().isAfter(now)) {
            throw new DelegatePaymentValidationException(
                    "allowance.expires_at MUST be in the future",
                    "invalid_card",
                    "$.allowance.expires_at");
        }
    }

    private DelegatePaymentResponse issueToken(DelegatePaymentRequest request, String idempotencyKey) {
        var metadata = responseMetadata(request, idempotencyKey);
        var tokenId = nextTokenId();
        var response = new DelegatePaymentResponse(tokenId, clock.instant(), metadata);
        tokens.put(tokenId, new StoredToken(request));
        return response;
    }

    @Override
    public TokenReservation reserve(String token, CheckoutSessionId checkoutSessionId, MinorUnitAmount totalAmount, CurrencyCode currency) {
        var normalizedToken = normalizeToken(token);
        var stored = tokens.get(normalizedToken);
        if (stored == null) {
            throw new DelegatePaymentTokenException("Unknown delegated payment token", "invalid_token", TOKEN_PARAM);
        }
        validateAllowance(stored.request().allowance(), checkoutSessionId, totalAmount, currency);
        return stored.reserve();
    }

    private String normalizeToken(String token) {
        if (token == null) {
            throw new DelegatePaymentTokenException("payment_data.token MUST be provided", "invalid_token", TOKEN_PARAM);
        }
        var trimmed = token.trim();
        if (trimmed.isEmpty()) {
            throw new DelegatePaymentTokenException("payment_data.token MUST be non-blank", "invalid_token", TOKEN_PARAM);
        }
        return trimmed;
    }

    private void validateAllowance(
            Allowance allowance, CheckoutSessionId checkoutSessionId, MinorUnitAmount totalAmount, CurrencyCode currency) {
        if (!allowance.checkoutSessionId().equals(checkoutSessionId.value())) {
            throw new DelegatePaymentTokenException(
                    "Delegated token does not match checkout session",
                    "invalid_token",
                    TOKEN_PARAM);
        }
        if (!allowance.currency().equals(currency)) {
            throw new DelegatePaymentTokenException(
                    "Delegated token currency mismatch",
                    "invalid_token",
                    TOKEN_PARAM);
        }
        if (totalAmount.value() > allowance.maxAmount().value()) {
            throw new DelegatePaymentTokenException(
                    "Checkout total exceeds delegated allowance",
                    "allowance_exceeded",
                    TOKEN_PARAM);
        }
        var now = clock.instant();
        if (!now.isBefore(allowance.expiresAt())) {
            throw new DelegatePaymentTokenException(
                    "Delegated token has expired",
                    "invalid_token",
                    TOKEN_PARAM);
        }
    }

    private record StoredRecord(DelegatePaymentRequest request, DelegatePaymentResponse response) {
    }

    private enum TokenState {
        AVAILABLE,
        RESERVED,
        CONSUMED
    }

    private final class StoredToken {
        private final DelegatePaymentRequest request;
        private final AtomicReference<TokenState> state = new AtomicReference<>(TokenState.AVAILABLE);

        private StoredToken(DelegatePaymentRequest request) {
            this.request = request;
        }

        DelegatePaymentRequest request() {
            return request;
        }

        TokenReservation reserve() {
            if (!state.compareAndSet(TokenState.AVAILABLE, TokenState.RESERVED)) {
                throw new DelegatePaymentTokenException(
                        "Delegated payment token is not available",
                        "invalid_token",
                        TOKEN_PARAM);
            }
            return new Reservation(this);
        }

        private void release() {
            state.compareAndSet(TokenState.RESERVED, TokenState.AVAILABLE);
        }

        private void consume() {
            if (!state.compareAndSet(TokenState.RESERVED, TokenState.CONSUMED)) {
                throw new IllegalStateException("Delegated token state transition invalid");
            }
        }
    }

    private static final class Reservation implements TokenReservation {
        private final StoredToken token;
        private boolean committed;

        private Reservation(StoredToken token) {
            this.token = token;
        }

        @Override
        public void commit() {
            if (committed) {
                return;
            }
            token.consume();
            committed = true;
        }

        @Override
        public void close() {
            if (!committed) {
                token.release();
            }
        }
    }
}
