package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.util.Ensure;

public record Total(TotalType type, String displayText, MinorUnitAmount amount) {
    public Total {
        type = Ensure.notNull("total.type", type);
        displayText = Ensure.nonBlank("total.display_text", displayText);
        amount = Ensure.notNull("total.amount", amount);
    }

    public enum TotalType {
        ITEMS_BASE_AMOUNT,
        ITEMS_DISCOUNT,
        SUBTOTAL,
        DISCOUNT,
        FULFILLMENT,
        TAX,
        FEE,
        TOTAL
    }
}
