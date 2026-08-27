package com.example.wallet.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RawTransactionUtilsTest {

    @Test
    void decodesCanonicalHexTransaction() {
        assertThat(RawTransactionUtils.decodeCanonicalHex("0x0102ff"))
                .containsExactly(1, 2, -1);
    }

    @Test
    void rejectsMissingPrefixEmptyOddLengthAndNonHexData() {
        assertInvalid("0102", "raw transaction must use a 0x prefix");
        assertInvalid("0x", "raw transaction must contain complete bytes");
        assertInvalid("0x1", "raw transaction must contain complete bytes");
        assertInvalid("0x01xz", "raw transaction contains non-hexadecimal data");
        assertInvalid("0x０１", "raw transaction contains non-hexadecimal data");
    }

    @Test
    void rejectsTransactionAboveSizeLimitBeforeDecoding() {
        String oversized = "0x" + "00".repeat(
                RawTransactionUtils.MAX_RAW_TRANSACTION_BYTES + 1);

        assertInvalid(oversized, "raw transaction exceeds the size limit");
    }

    private void assertInvalid(String rawTransaction, String message) {
        assertThatThrownBy(() -> RawTransactionUtils.decodeCanonicalHex(rawTransaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }
}
