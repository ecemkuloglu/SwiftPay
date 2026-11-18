package com.swiftpay.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Turkish IBAN helpers: normalisation, validation (format + ISO 13616 mod-97) and masking. */
public final class Iban {

    private static final Pattern TR_IBAN = Pattern.compile("^TR\\d{24}$");
    private static final String MASKED_MIDDLE = " **** **** **** **** ";

    private Iban() {
    }

    /** Upper-cases and removes all whitespace; null stays null. */
    public static String normalize(String iban) {
        return iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** True if {@code iban} (already normalised) is TR + 24 digits with a valid mod-97 checksum. */
    public static boolean isValid(String iban) {
        return iban != null && TR_IBAN.matcher(iban).matches() && mod97(iban) == 1;
    }

    /** {@code TR330006100519786457841326} -> {@code TR33 **** **** **** **** 1326}. */
    public static String mask(String iban) {
        if (iban == null || iban.length() < 8) {
            return "****";
        }
        return iban.substring(0, 4) + MASKED_MIDDLE + iban.substring(iban.length() - 4);
    }

    /** Moves the first 4 chars to the end, maps letters to 10..35 and returns the number mod 97. */
    private static int mod97(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (int i = 0; i < rearranged.length(); i++) {
            int value = Character.getNumericValue(rearranged.charAt(i));
            remainder = value > 9 ? (remainder * 100 + value) % 97 : (remainder * 10 + value) % 97;
        }
        return remainder;
    }
}
