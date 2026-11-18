package com.swiftpay.support;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Generates unique, checksum-valid TR IBANs (bank code 99999, never used by the seed data). */
public final class TestIbans {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private TestIbans() {
    }

    public static String next() {
        return of(String.format("99999%017d", SEQ.incrementAndGet()));
    }

    /** Builds TR + check digits + 22-digit BBAN. */
    public static String of(String bban22) {
        // "TR00" moved to the end, letters -> digits: T=29, R=27
        BigInteger number = new BigInteger(bban22 + "292700");
        int check = 98 - number.mod(BigInteger.valueOf(97)).intValue();
        return "TR" + String.format("%02d", check) + bban22;
    }
}
