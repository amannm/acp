package com.amannmalik.acp.address;

import com.amannmalik.acp.api.checkout.model.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AddressTest {
    @Test
    void stateMayBeOmitted() {
        var address = buildAddress(null, "us");
        assertNull(address.state());
    }

    @Test
    void countryCodesAreUppercased() {
        var address = buildAddress("CA", "us");
        assertEquals("US", address.country());
    }

    @Test
    void nonIsoCountryCodesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> buildAddress("CA", "usa"));
    }

    private static Address buildAddress(String state, String country) {
        return new Address(
                "Jane Doe",
                "1234 Chat Road",
                null,
                "San Francisco",
                state,
                country,
                "94131");
    }
}
