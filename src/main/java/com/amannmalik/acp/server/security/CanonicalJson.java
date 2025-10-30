package com.amannmalik.acp.server.security;

import jakarta.json.*;

import java.io.ByteArrayInputStream;
import java.util.*;

final class CanonicalJson {
    private CanonicalJson() {
    }

    static String canonicalize(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try (var reader = Json.createReader(new ByteArrayInputStream(body))) {
            var value = reader.readValue();
            var builder = new StringBuilder();
            write(value, builder);
            return builder.toString();
        } catch (jakarta.json.stream.JsonParsingException e) {
            throw new IllegalArgumentException("Request body is not valid JSON", e);
        }
    }

    private static void write(JsonValue value, StringBuilder builder) {
        var type = value.getValueType();
        switch (type) {
            case OBJECT -> writeObject(value.asJsonObject(), builder);
            case ARRAY -> writeArray(value.asJsonArray(), builder);
            case STRING -> builder.append(quote(((JsonString) value).getString()));
            case NUMBER -> builder.append(value);
            case TRUE, FALSE, NULL -> builder.append(value);
            default -> throw new IllegalStateException("Unhandled JSON value type: " + type);
        }
    }

    private static void writeObject(jakarta.json.JsonObject object, StringBuilder builder) {
        builder.append('{');
        List<String> keys = new ArrayList<>(object.keySet());
        Collections.sort(keys);
        var first = true;
        for (var key : keys) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(quote(key)).append(':');
            write(object.get(key), builder);
        }
        builder.append('}');
    }

    private static void writeArray(jakarta.json.JsonArray array, StringBuilder builder) {
        builder.append('[');
        var size = array.size();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(',');
            }
            write(array.get(i), builder);
        }
        builder.append(']');
    }

    private static String quote(String text) {
        return Json.createValue(text).toString();
    }
}
