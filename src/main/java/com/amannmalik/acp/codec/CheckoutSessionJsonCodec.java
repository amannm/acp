package com.amannmalik.acp.codec;

import com.amannmalik.acp.api.checkout.model.Buyer;
import com.amannmalik.acp.api.checkout.model.CheckoutSession;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCompleteRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCreateRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionUpdateRequest;
import com.amannmalik.acp.api.checkout.model.FulfillmentOption;
import com.amannmalik.acp.api.checkout.model.FulfillmentOptionId;
import com.amannmalik.acp.api.checkout.model.Item;
import com.amannmalik.acp.api.checkout.model.LineItem;
import com.amannmalik.acp.api.checkout.model.Message;
import com.amannmalik.acp.api.checkout.model.Order;
import com.amannmalik.acp.api.checkout.model.PaymentData;
import com.amannmalik.acp.api.checkout.model.PaymentProvider;
import com.amannmalik.acp.api.checkout.model.Total;
import com.amannmalik.acp.api.shared.ErrorResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/// See specification/2025-09-29/spec/openapi/openapi.agentic_checkout.yaml
public final class CheckoutSessionJsonCodec {
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
            if (option instanceof FulfillmentOption.Shipping shipping) {
                var json = Json.createObjectBuilder()
                        .add("type", "shipping")
                        .add("id", shipping.id())
                        .add("title", shipping.title())
                        .add("subtotal", shipping.subtotal().value())
                        .add("tax", shipping.tax().value())
                        .add("total", shipping.total().value());
                if (shipping.subtitle() != null) {
                    json.add("subtitle", shipping.subtitle());
                }
                if (shipping.carrier() != null) {
                    json.add("carrier", shipping.carrier());
                }
                if (shipping.earliestDeliveryTime() != null) {
                    json.add("earliest_delivery_time", shipping.earliestDeliveryTime().toString());
                }
                if (shipping.latestDeliveryTime() != null) {
                    json.add("latest_delivery_time", shipping.latestDeliveryTime().toString());
                }
                builder.add(json);
            } else if (option instanceof FulfillmentOption.Digital digital) {
                var json = Json.createObjectBuilder()
                        .add("type", "digital")
                        .add("id", digital.id())
                        .add("title", digital.title())
                        .add("subtotal", digital.subtotal().value())
                        .add("tax", digital.tax().value())
                        .add("total", digital.total().value());
                if (digital.subtitle() != null) {
                    json.add("subtitle", digital.subtitle());
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
            if (message instanceof Message.Info info) {
                var json = Json.createObjectBuilder()
                        .add("type", "info")
                        .add("content_type", info.contentType().name().toLowerCase())
                        .add("content", info.content());
                if (info.param() != null) {
                    json.add("param", info.param());
                }
                builder.add(json);
            } else if (message instanceof Message.Error error) {
                var json = Json.createObjectBuilder()
                        .add("type", "error")
                        .add("code", error.code().name().toLowerCase())
                        .add("content_type", error.contentType().name().toLowerCase())
                        .add("content", error.content());
                if (error.param() != null) {
                    json.add("param", error.param());
                }
                builder.add(json);
            }
        }
        return builder;
    }

    private static JsonArrayBuilder writeLinks(List<com.amannmalik.acp.api.checkout.model.Link> links) {
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
}
