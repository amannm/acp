package com.amannmalik.acp.api.delegatepayment.model;

import com.amannmalik.acp.util.Ensure;

public record RiskSignal(Type type, int score, Action action) {
    public RiskSignal {
        type = Ensure.notNull("risk_signal.type", type);
        action = Ensure.notNull("risk_signal.action", action);
    }

    public enum Type {
        CARD_TESTING
    }

    public enum Action {
        BLOCKED,
        MANUAL_REVIEW,
        AUTHORIZED
    }
}
