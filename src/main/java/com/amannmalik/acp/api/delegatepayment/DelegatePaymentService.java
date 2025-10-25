package com.amannmalik.acp.api.delegatepayment;

import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentResponse;

public interface DelegatePaymentService {
    DelegatePaymentResponse create(DelegatePaymentRequest request, String idempotencyKey);
}
