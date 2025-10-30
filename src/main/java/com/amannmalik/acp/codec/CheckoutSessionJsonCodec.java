package com.amannmalik.acp.codec;

import com.amannmalik.acp.api.checkout.model.*;
import com.amannmalik.acp.api.shared.ErrorResponse;
import com.amannmalik.acp.api.shared.MinorUnitAmount;
import jakarta.json.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

/// See specification/2025-09-29/spec/openapi/openapi.agentic_checkout.yaml
public final class CheckoutSessionJsonCodec {
    public CheckoutSessionJsonCodec() {
    }

    private static JsonObject readObject(InputStream body) {
        try (JsonReader reader = Json.createReader(body)) {
            return reader.readObject();
        }
    }

    private static Buyer mapBuyer(JsonObject object) {
        return new Buyer(
                JsonSupport.requireString(object, "first_name"),
                JsonSupport.requireString(object, "last_name"),
                JsonSupport.requireString(object, "email"),
                JsonSupport.optionalString(object, "phone_number"));
    }

    private static Buyer readBuyerOrNull(JsonObject root, String key) {
        if (!root.containsKey(key) || root.isNull(key)) {
            return null;
        }
        return mapBuyer(root.getJsonObject(key));
    }

    private static Item mapItem(JsonObject jsonObject) {
        return new Item(
                JsonSupport.requireString(jsonObject, "id"),
                JsonSupport.requireInt(jsonObject, "quantity"));
    }

    private static PaymentData mapPaymentData(JsonObject object) {
        var provider = PaymentProvider.Provider.valueOf(JsonSupport.requireString(object, "provider").toUpperCase());
        var billingAddress = object.containsKey("billing_address") && !object.isNull("billing_address")
                ? AddressJson.read(object.getJsonObject("billing_address"))
                : null;
        return new PaymentData(
                JsonSupport.requireString(object, "token"),
                provider,
                billingAddress);
    }

    private static JsonObjectBuilder writeBuyer(Buyer buyer) {
        var builder = Json.createObjectBuilder()
                .add("first_name", buyer.firstName())
                .add("last_name", buyer.lastName())
                .add("email", buyer.email());
        if (buyer.phoneNumber() != null) {
            builder.add("phone_number", buyer.phoneNumber());
        }
        return builder;
    }

    private static JsonArrayBuilder writeLineItems(List<LineItem> lineItems) {
        var builder = Json.createArrayBuilder();
        for (var lineItem : lineItems) {
            builder.add(Json.createObjectBuilder()
                    .add("id", lineItem.id())
                    .add("item", Json.createObjectBuilder()
                            .add("id", lineItem.item().id())
                            .add("quantity", lineItem.item().quantity()))
                    .add("base_amount", lineItem.baseAmount().value())
                    .add("discount", lineItem.discount().value())
                    .add("subtotal", lineItem.subtotal().value())
                    .add("tax", lineItem.tax().value())
                    .add("total", lineItem.total().value()));
        }
        return builder;
    }

    private static JsonObjectBuilder writePaymentProvider(PaymentProvider paymentProvider) {
        var builder = Json.createObjectBuilder()
                .add("provider", paymentProvider.provider().name().toLowerCase());
        var methods = Json.createArrayBuilder();
        for (var method : paymentProvider.supportedPaymentMethods()) {
            methods.add(method.name().toLowerCase());
        }
        builder.add("supported_payment_methods", methods);
        return builder;
    }

    private static JsonArrayBuilder writeFulfillmentOptions(List<FulfillmentOption> options) {
        var builder = Json.createArrayBuilder();
        for (var option : options) {
            if (option instanceof FulfillmentOption.Shipping(String id, String title, String subtitle, String carrier, Instant earliestDeliveryTime, Instant latestDeliveryTime, MinorUnitAmount subtotal, MinorUnitAmount tax, MinorUnitAmount total)) {
                var json = Json.createObjectBuilder()
                        .add("type", "shipping")
                        .add("id", id)
                        .add("title", title)
                        .add("subtotal", subtotal.value())
                        .add("tax", tax.value())
                        .add("total", total.value());
                if (subtitle != null) {
                    json.add("subtitle", subtitle);
                }
                if (carrier != null) {
                    json.add("carrier", carrier);
                }
                if (earliestDeliveryTime != null) {
                    json.add("earliest_delivery_time", earliestDeliveryTime.toString());
                }
                if (latestDeliveryTime != null) {
                    json.add("latest_delivery_time", latestDeliveryTime.toString());
                }
                builder.add(json);
            } else if (option instanceof FulfillmentOption.Digital(String id, String title, String subtitle, MinorUnitAmount subtotal, MinorUnitAmount tax, MinorUnitAmount total)) {
                var json = Json.createObjectBuilder()
                        .add("type", "digital")
                        .add("id", id)
                        .add("title", title)
                        .add("subtotal", subtotal.value())
                        .add("tax", tax.value())
                        .add("total", total.value());
                if (subtitle != null) {
                    json.add("subtitle", subtitle);
                }
                builder.add(json);
            }
        }
        return builder;
    }

    private static JsonArrayBuilder writeTotals(List<Total> totals) {
        var builder = Json.createArrayBuilder();
        for (var total : totals) {
            builder.add(Json.createObjectBuilder()
                    .add("type", total.type().name().toLowerCase())
                    .add("display_text", total.displayText())
                    .add("amount", total.amount().value()));
        }
        return builder;
    }

    private static JsonArrayBuilder writeMessages(List<Message> messages) {
        var builder = Json.createArrayBuilder();
        for (var message : messages) {
            if (message instanceof Message.Info(String param, Message.ContentType contentType, String content)) {
                var json = Json.createObjectBuilder()
                        .add("type", "info")
                        .add("content_type", contentType.name().toLowerCase())
                        .add("content", content);
                if (param != null) {
                    json.add("param", param);
                }
                builder.add(json);
            } else if (message instanceof Message.Error(Message.ErrorCode code, String param, Message.ContentType contentType, String content)) {
                var json = Json.createObjectBuilder()
                        .add("type", "error")
                        .add("code", code.name().toLowerCase())
                        .add("content_type", contentType.name().toLowerCase())
                        .add("content", content);
                if (param != null) {
                    json.add("param", param);
                }
                builder.add(json);
            }
        }
        return builder;
    }

    private static JsonArrayBuilder writeLinks(List<Link> links) {
        var builder = Json.createArrayBuilder();
        for (var link : links) {
            builder.add(Json.createObjectBuilder()
                    .add("type", link.type().name().toLowerCase())
                    .add("url", link.url().toString()));
        }
        return builder;
    }

    private static JsonObjectBuilder writeOrder(Order order) {
        return Json.createObjectBuilder()
                .add("id", order.id())
                .add("checkout_session_id", order.checkoutSessionId().value())
                .add("permalink_url", order.permalinkUrl().toString());
    }

    private static List<Item> mapItemsArray(JsonArray array) {
        return array.stream()
                .map(JsonValue::asJsonObject)
                .map(CheckoutSessionJsonCodec::mapItem)
                .toList();
    }

    public CheckoutSessionCreateRequest readCreateRequest(InputStream body) {
        var root = readObject(body);
        var items = mapItemsArray(JsonSupport.requireArray(root, "items"));
        var buyer = readBuyerOrNull(root, "buyer");
        var fulfillmentAddress = AddressJson.readOptional(root, "fulfillment_address");
        return new CheckoutSessionCreateRequest(items, buyer, fulfillmentAddress);
    }

    public CheckoutSessionUpdateRequest readUpdateRequest(InputStream body) {
        var root = readObject(body);
        List<Item> items = root.containsKey("items")
                ? mapItemsArray(root.getJsonArray("items"))
                : null;
        var buyer = readBuyerOrNull(root, "buyer");
        var fulfillmentAddress = AddressJson.readOptional(root, "fulfillment_address");
        FulfillmentOptionId fulfillmentOptionId = root.containsKey("fulfillment_option_id")
                ? new FulfillmentOptionId(JsonSupport.requireString(root, "fulfillment_option_id"))
                : null;
        return new CheckoutSessionUpdateRequest(items, buyer, fulfillmentAddress, fulfillmentOptionId);
    }

    public CheckoutSessionCompleteRequest readCompleteRequest(InputStream body) {
        var root = readObject(body);
        var buyer = readBuyerOrNull(root, "buyer");
        var paymentData = mapPaymentData(JsonSupport.requireObject(root, "payment_data"));
        return new CheckoutSessionCompleteRequest(buyer, paymentData);
    }

    public void writeCheckoutSession(OutputStream outputStream, CheckoutSession session) {
        var builder = Json.createObjectBuilder();
        builder.add("id", session.id().value());
        if (session.buyer() != null) {
            builder.add("buyer", writeBuyer(session.buyer()));
        }
        if (session.paymentProvider() != null) {
            builder.add("payment_provider", writePaymentProvider(session.paymentProvider()));
        }
        builder.add("status", session.status().name().toLowerCase());
        builder.add("currency", session.currency().value());
        builder.add("line_items", writeLineItems(session.lineItems()));
        if (session.fulfillmentAddress() != null) {
            builder.add("fulfillment_address", AddressJson.write(session.fulfillmentAddress()));
        }
        builder.add("fulfillment_options", writeFulfillmentOptions(session.fulfillmentOptions()));
        if (session.fulfillmentOptionId() != null) {
            builder.add("fulfillment_option_id", session.fulfillmentOptionId().value());
        }
        builder.add("totals", writeTotals(session.totals()));
        builder.add("messages", writeMessages(session.messages()));
        builder.add("links", writeLinks(session.links()));
        if (session.order() != null) {
            builder.add("order", writeOrder(session.order()));
        }
        ErrorJson.writeObject(builder, outputStream);
    }

    public void writeError(OutputStream outputStream, ErrorResponse error) {
        ErrorJson.write(outputStream, error);
    }
}
