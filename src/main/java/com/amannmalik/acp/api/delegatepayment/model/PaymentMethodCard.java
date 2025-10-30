package com.amannmalik.acp.api.delegatepayment.model;

import com.amannmalik.acp.util.Ensure;

import java.util.List;
import java.util.Map;

public record PaymentMethodCard(
        CardNumberType cardNumberType,
        boolean virtual,
        String number,
        String expMonth,
        String expYear,
        String name,
        String cvc,
        String cryptogram,
        String eciValue,
        List<Check> checksPerformed,
        String iin,
        DisplayCardFundingType displayCardFundingType,
        String displayWalletType,
        String displayBrand,
        String displayLast4,
        Map<String, String> metadata) {
    public enum CardNumberType {
        FPAN,
        NETWORK_TOKEN
    }

    public enum Check {
        AVS,
        CVV,
        ANI,
        AUTH0
    }

    public enum DisplayCardFundingType {
        CREDIT,
        DEBIT,
        PREPAID
    }

    public PaymentMethodCard {
        cardNumberType = Ensure.notNull("payment_method.card_number_type", cardNumberType);
        number = Ensure.nonBlank("payment_method.number", number);
        expMonth = normalizeOptional(expMonth);
        expYear = normalizeOptional(expYear);
        if (expMonth != null && expMonth.length() > 2) {
            throw new IllegalArgumentException("payment_method.exp_month MUST be 2 digits");
        }
        if (expYear != null && expYear.length() > 4) {
            throw new IllegalArgumentException("payment_method.exp_year MUST be 4 digits");
        }
        if (cvc != null && cvc.length() > 4) {
            throw new IllegalArgumentException("payment_method.cvc MUST be <= 4 digits");
        }
        cryptogram = normalizeOptional(cryptogram);
        eciValue = normalizeOptional(eciValue);
        if (eciValue != null && eciValue.length() > 2) {
            throw new IllegalArgumentException("payment_method.eci_value MUST be <= 2 characters");
        }
        if (iin != null && iin.length() > 6) {
            throw new IllegalArgumentException("payment_method.iin MUST be <= 6 digits");
        }
        displayCardFundingType = Ensure.notNull("payment_method.display_card_funding_type", displayCardFundingType);
        if (displayLast4 != null && displayLast4.length() != 4) {
            throw new IllegalArgumentException("payment_method.display_last4 MUST be 4 characters");
        }
        checksPerformed = Ensure.immutableList("payment_method.checks_performed", checksPerformed);
        metadata = Map.copyOf(Ensure.notNull("payment_method.metadata", metadata));
    }

    private static String normalizeOptional(String text) {
        if (text == null) {
            return null;
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Optional fields MUST be non-blank when provided");
        }
        return text;
    }
}
