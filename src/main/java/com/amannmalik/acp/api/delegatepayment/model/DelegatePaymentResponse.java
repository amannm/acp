package com.amannmalik.acp.api.delegatepayment.model;

import com.amannmalik.acp.util.Ensure;

import java.time.Instant;
import java.util.Map;

public record DelegatePaymentResponse(String id, Instant created, Map<String, String> metadata) {
    public DelegatePaymentResponse {
        id = Ensure.nonBlank("delegate_payment_response.id", id);
        created = Ensure.notNull("delegate_payment_response.created", created);
        metadata = Map.copyOf(Ensure.notNull("delegate_payment_response.metadata", metadata));
    }
}
