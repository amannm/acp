package com.amannmalik.acp.api.delegatepayment.model;

import com.amannmalik.acp.api.checkout.model.Address;
import com.amannmalik.acp.util.Ensure;
import java.util.List;
import java.util.Map;

public record DelegatePaymentRequest(
        PaymentMethodCard paymentMethod,
        Allowance allowance,
        Address billingAddress,
        List<RiskSignal> riskSignals,
        Map<String, String> metadata) {
    public DelegatePaymentRequest {
        paymentMethod = Ensure.notNull("delegate_payment.payment_method", paymentMethod);
        allowance = Ensure.notNull("delegate_payment.allowance", allowance);
        riskSignals = Ensure.immutableList("delegate_payment.risk_signals", riskSignals);
        if (riskSignals.isEmpty()) {
            throw new IllegalArgumentException("delegate_payment.risk_signals MUST contain at least one signal");
        }
        metadata = Map.copyOf(Ensure.notNull("delegate_payment.metadata", metadata));
    }
}
