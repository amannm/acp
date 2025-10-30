package com.amannmalik.acp.codec;

import com.amannmalik.acp.api.checkout.model.Address;
import jakarta.json.*;

final class AddressJson {
    private AddressJson() {
    }

    static Address read(JsonObject object) {
        return new Address(
                JsonSupport.requireString(object, "name"),
                JsonSupport.requireString(object, "line_one"),
                JsonSupport.optionalString(object, "line_two"),
                JsonSupport.requireString(object, "city"),
                JsonSupport.requireString(object, "state"),
                JsonSupport.requireString(object, "country"),
                JsonSupport.requireString(object, "postal_code"));
    }

    static Address readOptional(JsonObject parent, String key) {
        if (!parent.containsKey(key) || parent.isNull(key)) {
            return null;
        }
        return read(parent.getJsonObject(key));
    }

    static JsonObjectBuilder write(Address address) {
        var builder = Json.createObjectBuilder()
                .add("name", address.name())
                .add("line_one", address.lineOne());
        if (address.lineTwo() != null) {
            builder.add("line_two", address.lineTwo());
        }
        return builder
                .add("city", address.city())
                .add("state", address.state())
                .add("country", address.country())
                .add("postal_code", address.postalCode());
    }
}
