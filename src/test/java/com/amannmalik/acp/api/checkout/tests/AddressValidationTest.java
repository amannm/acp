package com.amannmalik.acp.api.checkout.tests;

import com.amannmalik.acp.api.checkout.model.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AddressValidationTest {
    @Test
    void acceptsBlankLineTwoWithinLimits() {
        assertDoesNotThrow(() -> new Address(
                "Name",
                "123456789012345678901234567890123456789012345678901234567890",
                "",
                "City",
                "CA",
                "US",
                "94102"));
    }

    @Test
    void rejectsNameLongerThan256Characters() {
        var longName = "N".repeat(257);
        assertThrows(IllegalArgumentException.class, () -> new Address(
                longName,
                "Line 1",
                null,
                "City",
                "CA",
                "US",
                "94102"));
    }

    @Test
    void rejectsCountryNotTwoCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new Address(
                "Name",
                "Line 1",
                null,
                "City",
                "CA",
                "USA",
                "94102"));
    }

    @Test
    void rejectsLineOneLongerThan60Characters() {
        var line = "L".repeat(61);
        assertThrows(IllegalArgumentException.class, () -> new Address(
                "Name",
                line,
                null,
                "City",
                "CA",
                "US",
                "94102"));
    }

    @Test
    void rejectsPostalCodeLongerThan20Characters() {
        var postal = "9".repeat(21);
        assertThrows(IllegalArgumentException.class, () -> new Address(
                "Name",
                "Line 1",
                null,
                "City",
                "CA",
                "US",
                postal));
    }

    @Test
    void allowsMissingState() {
        assertDoesNotThrow(() -> new Address(
                "Name",
                "Line 1",
                null,
                "City",
                null,
                "FR",
                "75001"));
    }

    @Test
    void rejectsBlankStateWhenProvided() {
        assertThrows(IllegalArgumentException.class, () -> new Address(
                "Name",
                "Line 1",
                null,
                "City",
                "",
                "FR",
                "75001"));
    }
}
