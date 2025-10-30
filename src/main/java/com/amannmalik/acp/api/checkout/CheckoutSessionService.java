package com.amannmalik.acp.api.checkout;

import com.amannmalik.acp.api.checkout.model.*;

public interface CheckoutSessionService {
    CheckoutSession create(CheckoutSessionCreateRequest request, String idempotencyKey);

    CheckoutSession update(CheckoutSessionId id, CheckoutSessionUpdateRequest request);

    CheckoutSession retrieve(CheckoutSessionId id);

    CheckoutSession complete(CheckoutSessionId id, CheckoutSessionCompleteRequest request, String idempotencyKey);

    CheckoutSession cancel(CheckoutSessionId id);
}
