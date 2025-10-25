package com.amannmalik.acp.codec;

import com.amannmalik.acp.api.checkout.model.Address;
import com.amannmalik.acp.api.delegatepayment.model.Allowance;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentResponse;
import com.amannmalik.acp.api.delegatepayment.model.PaymentMethodCard;
import com.amannmalik.acp.api.delegatepayment.model.RiskSignal;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.ErrorResponse;
import com.amannmalik.acp.api.shared.MinorUnitAmount;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// See specification/2025-09-29/spec/openapi/openapi.delegate_payment.yaml
public final class DelegatePaymentJsonCodec {
    public DelegatePaymentRequest readRequest(InputStream body) {
        var root = readObject(body);
        var paymentMethod = mapPaymentMethod(JsonSupport.requireObject(root, "payment_method"));
        var allowance = mapAllowance(JsonSupport.requireObject(root, "allowance"));
        var billingAddress = AddressJson.readOptional(root, "billing_address");
        var riskSignals = mapRiskSignals(JsonSupport.requireArray(root, "risk_signals"));
        var metadata = mapMetadata(JsonSupport.requireObject(root, "metadata"));
        return new DelegatePaymentRequest(paymentMethod, allowance, billingAddress, riskSignals, metadata);
    }

    public void writeResponse(OutputStream outputStream, DelegatePaymentResponse response) {
        var builder = Json.createObjectBuilder()
                .add("id", response.id())
                .add("created", response.created().toString())
                .add("metadata", buildMetadata(response.metadata()));
        ErrorJson.writeObject(builder, outputStream);
    }

    public void writeError(OutputStream stream, ErrorResponse error) {
        ErrorJson.write(stream, error);
    }

    private static JsonObject readObject(InputStream body) {
        try (JsonReader reader = Json.createReader(body)) {
            return reader.readObject();
        }
    }

    private static PaymentMethodCard mapPaymentMethod(JsonObject object) {
        var cardNumberType = PaymentMethodCard.CardNumberType.valueOf(
                JsonSupport.requireString(object, "card_number_type").toUpperCase());
        List<PaymentMethodCard.Check> checks;
        if (object.containsKey("checks_performed") && !object.isNull("checks_performed")) {
            checks = object.getJsonArray("checks_performed").stream()
                    .map(value -> ((JsonString) value).getString())
                    .map(value -> PaymentMethodCard.Check.valueOf(value.toUpperCase()))
                    .toList();
        } else {
            checks = List.of();
        }
        Map<String, String> metadata = object.containsKey("metadata")
                ? mapMetadata(object.getJsonObject("metadata"))
                : Map.of();
        return new PaymentMethodCard(
                cardNumberType,
                object.getBoolean("virtual"),
                JsonSupport.requireString(object, "number"),
                JsonSupport.optionalString(object, "exp_month"),
                JsonSupport.optionalString(object, "exp_year"),
                JsonSupport.optionalString(object, "name"),
                JsonSupport.optionalString(object, "cvc"),
                checks,
                JsonSupport.optionalString(object, "iin"),
                PaymentMethodCard.DisplayCardFundingType.valueOf(
                        JsonSupport.requireString(object, "display_card_funding_type").toUpperCase()),
                JsonSupport.optionalString(object, "display_wallet_type"),
                JsonSupport.optionalString(object, "display_brand"),
                JsonSupport.optionalString(object, "display_last4"),
                metadata);
    }

    private static Allowance mapAllowance(JsonObject object) {
        return new Allowance(
                Allowance.Reason.valueOf(JsonSupport.requireString(object, "reason").toUpperCase()),
                new MinorUnitAmount(JsonSupport.requireLong(object, "max_amount")),
                new CurrencyCode(JsonSupport.requireString(object, "currency")),
                JsonSupport.requireString(object, "checkout_session_id"),
                JsonSupport.requireString(object, "merchant_id"),
                Instant.parse(JsonSupport.requireString(object, "expires_at")));
    }

    private static List<RiskSignal> mapRiskSignals(JsonArray array) {
        return array.stream()
                .map(JsonValue::asJsonObject)
                .map(obj -> new RiskSignal(
                        RiskSignal.Type.valueOf(JsonSupport.requireString(obj, "type").toUpperCase()),
                        JsonSupport.requireInt(obj, "score"),
                        RiskSignal.Action.valueOf(JsonSupport.requireString(obj, "action").toUpperCase())))
                .toList();
    }

    private static Map<String, String> mapMetadata(JsonObject object) {
        var map = new LinkedHashMap<String, String>();
        for (var entry : object.entrySet()) {
            if (entry.getValue().getValueType() != jakarta.json.JsonValue.ValueType.STRING) {
                throw new JsonDecodingException("metadata values MUST be strings");
            }
            map.put(entry.getKey(), object.getString(entry.getKey()));
        }
        return Map.copyOf(map);
    }

    private static JsonObject buildMetadata(Map<String, String> metadata) {
        var builder = Json.createObjectBuilder();
        metadata.forEach(builder::add);
        return builder.build();
    }
}
