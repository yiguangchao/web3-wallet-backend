package com.example.wallet.common.utils;

import org.web3j.utils.Numeric;

public final class RawTransactionUtils {
    public static final int MAX_RAW_TRANSACTION_BYTES = 128 * 1024;

    private RawTransactionUtils() {
    }

    public static byte[] decodeCanonicalHex(String rawTransaction) {
        if (rawTransaction == null || !rawTransaction.startsWith("0x")) {
            throw new IllegalArgumentException("raw transaction must use a 0x prefix");
        }
        String hex = rawTransaction.substring(2);
        if (hex.isEmpty() || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException(
                    "raw transaction must contain complete bytes");
        }
        if (hex.length() > MAX_RAW_TRANSACTION_BYTES * 2) {
            throw new IllegalArgumentException("raw transaction exceeds the size limit");
        }
        for (int index = 0; index < hex.length(); index++) {
            if (!isAsciiHex(hex.charAt(index))) {
                throw new IllegalArgumentException(
                        "raw transaction contains non-hexadecimal data");
            }
        }
        return Numeric.hexStringToByteArray(rawTransaction);
    }

    private static boolean isAsciiHex(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }
}
