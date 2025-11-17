package com.amannmalik.acp.address;

import com.amannmalik.acp.api.checkout.model.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AddressTest {
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

    @Test
    void stateIsTrimmed() {
        var address = buildAddress("  CA  ", "us");
        assertEquals("CA", address.state());
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
}
