package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.util.Ensure;

public record LineItem(
        String id,
        Item item,
        MinorUnitAmount baseAmount,
        MinorUnitAmount discount,
        MinorUnitAmount subtotal,
        MinorUnitAmount tax,
        MinorUnitAmount total) {
    public LineItem {
        id = Ensure.nonBlank("line_item.id", id);
        item = Ensure.notNull("line_item.item", item);
        baseAmount = Ensure.notNull("line_item.base_amount", baseAmount);
        discount = Ensure.notNull("line_item.discount", discount);
        subtotal = Ensure.notNull("line_item.subtotal", subtotal);
        tax = Ensure.notNull("line_item.tax", tax);
        total = Ensure.notNull("line_item.total", total);
    }
}
