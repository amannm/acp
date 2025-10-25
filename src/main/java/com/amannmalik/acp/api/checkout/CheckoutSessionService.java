package com.amannmalik.acp.api.checkout;

import com.amannmalik.acp.api.checkout.model.CheckoutSession;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCompleteRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCreateRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionId;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionUpdateRequest;

public interface CheckoutSessionService {
    CheckoutSession create(CheckoutSessionCreateRequest request);

    CheckoutSession update(CheckoutSessionId id, CheckoutSessionUpdateRequest request);

    CheckoutSession retrieve(CheckoutSessionId id);

    CheckoutSession complete(CheckoutSessionId id, CheckoutSessionCompleteRequest request);

    CheckoutSession cancel(CheckoutSessionId id);
}
