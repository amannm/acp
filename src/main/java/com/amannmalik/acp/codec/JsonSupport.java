package com.amannmalik.acp.codec;

import com.amannmalik.acp.api.shared.MinorUnitAmount;
import jakarta.json.*;

final class JsonSupport {
    private JsonSupport() {
    }

    static JsonObject requireObject(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            throw new JsonDecodingException("Missing object: " + key);
        }
        var value = parent.get(key);
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new JsonDecodingException("Expected object at: " + key);
        }
        return value.asJsonObject();
    }

    static JsonArray requireArray(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            throw new JsonDecodingException("Missing array: " + key);
        }
        var value = parent.get(key);
        if (value.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new JsonDecodingException("Expected array at: " + key);
        }
        return value.asJsonArray();
    }

    static String requireString(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            throw new JsonDecodingException("Missing string: " + key);
        }
        var value = parent.get(key);
        if (value.getValueType() != JsonValue.ValueType.STRING) {
            throw new JsonDecodingException("Expected string at: " + key);
        }
        var string = ((JsonString) value).getString();
        if (string.isBlank()) {
            throw new JsonDecodingException("String MUST be non-blank: " + key);
        }
        return string;
    }

    static String optionalString(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            return null;
        }
        var value = parent.get(key);
        if (value.getValueType() != JsonValue.ValueType.STRING) {
            throw new JsonDecodingException("Expected string at: " + key);
        }
        var string = ((JsonString) value).getString();
        if (string.isBlank()) {
            throw new JsonDecodingException("String MUST be non-blank: " + key);
        }
        return string;
    }

    static String optionalStringAllowBlank(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            return null;
        }
        var value = parent.get(key);
        if (value.getValueType() != JsonValue.ValueType.STRING) {
            throw new JsonDecodingException("Expected string at: " + key);
        }
        return ((JsonString) value).getString();
    }

    static int requireInt(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            throw new JsonDecodingException("Missing integer: " + key);
        }
        var value = parent.get(key);
        if (!(value instanceof JsonNumber number)) {
            throw new JsonDecodingException("Expected integer at: " + key);
        }
        return number.intValueExact();
    }

    static long requireLong(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            throw new JsonDecodingException("Missing integer: " + key);
        }
        var value = parent.get(key);
        if (!(value instanceof JsonNumber number)) {
            throw new JsonDecodingException("Expected integer at: " + key);
        }
        return number.longValueExact();
    }

    static MinorUnitAmount requireAmount(JsonObject parent, String key) {
        return new MinorUnitAmount(requireLong(parent, key));
    }

    static boolean requireBoolean(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            throw new JsonDecodingException("Missing boolean: " + key);
        }
        var value = parent.get(key);
        return switch (value.getValueType()) {
            case TRUE -> true;
            case FALSE -> false;
            default -> throw new JsonDecodingException("Expected boolean at: " + key);
        };
    }
}
