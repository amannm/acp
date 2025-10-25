package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.util.Ensure;

import java.time.Instant;

public sealed interface FulfillmentOption permits FulfillmentOption.Digital, FulfillmentOption.Shipping {
    String id();

    String title();

    MinorUnitAmount subtotal();

    MinorUnitAmount tax();

    MinorUnitAmount total();

    String subtitle();

    record Shipping(
            String id,
            String title,
            String subtitle,
            String carrier,
            Instant earliestDeliveryTime,
            Instant latestDeliveryTime,
            MinorUnitAmount subtotal,
            MinorUnitAmount tax,
            MinorUnitAmount total) implements FulfillmentOption {
        public Shipping {
            id = Ensure.nonBlank("fulfillment_option.id", id);
            title = Ensure.nonBlank("fulfillment_option.title", title);
            if (carrier != null && carrier.isBlank()) {
                throw new IllegalArgumentException("fulfillment_option.carrier MUST be non-blank when provided");
            }
            if (subtitle != null && subtitle.isBlank()) {
                throw new IllegalArgumentException("fulfillment_option.subtitle MUST be non-blank when provided");
            }
            subtotal = Ensure.notNull("fulfillment_option.subtotal", subtotal);
            tax = Ensure.notNull("fulfillment_option.tax", tax);
            total = Ensure.notNull("fulfillment_option.total", total);
        }

        @Override
        public String subtitle() {
            return subtitle;
        }
    }

    record Digital(
            String id,
            String title,
            String subtitle,
            MinorUnitAmount subtotal,
            MinorUnitAmount tax,
            MinorUnitAmount total) implements FulfillmentOption {
        public Digital {
            id = Ensure.nonBlank("fulfillment_option.id", id);
            title = Ensure.nonBlank("fulfillment_option.title", title);
            if (subtitle != null && subtitle.isBlank()) {
                throw new IllegalArgumentException("fulfillment_option.subtitle MUST be non-blank when provided");
            }
            subtotal = Ensure.notNull("fulfillment_option.subtotal", subtotal);
            tax = Ensure.notNull("fulfillment_option.tax", tax);
            total = Ensure.notNull("fulfillment_option.total", total);
        }

        @Override
        public String subtitle() {
            return subtitle;
        }
    }
}
