package com.amannmalik.acp.api.delegatepayment;

import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentResponse;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryDelegatePaymentService implements DelegatePaymentService {
    private final ConcurrentMap<String, StoredRecord> idempotencyStore = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDelegatePaymentService() {
        this(Clock.systemUTC());
    }

    public InMemoryDelegatePaymentService(Clock clock) {
        this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
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
            throw new DelegatePaymentValidationException("allowance.max_amount MUST be > 0");
        }
        var now = clock.instant();
        if (!allowance.expiresAt().isAfter(now)) {
            throw new DelegatePaymentValidationException("allowance.expires_at MUST be in the future");
        }
    }

    private DelegatePaymentResponse issueToken(DelegatePaymentRequest request, String idempotencyKey) {
        var metadata = responseMetadata(request, idempotencyKey);
        return new DelegatePaymentResponse(
                nextTokenId(),
                clock.instant(),
                metadata);
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

    private record StoredRecord(DelegatePaymentRequest request, DelegatePaymentResponse response) {}
}
