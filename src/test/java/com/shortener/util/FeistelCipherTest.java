package com.shortener.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeistelCipherTest {

    @Test
    void decryptReversesEncrypt() {
        for (long id = 1_000_000; id < 1_005_000; id++) {
            assertThat(FeistelCipher.decrypt(FeistelCipher.encrypt(id))).isEqualTo(id);
        }
    }

    @Test
    void sequentialIdsDoNotProduceSequentialOutputs() {
        long a = FeistelCipher.encrypt(1_000_000);
        long b = FeistelCipher.encrypt(1_000_001);
        assertThat(Math.abs(b - a)).isGreaterThan(1_000);
    }

    @Test
    void outputStaysWithin62Bits() {
        long max = (1L << 62) - 1;
        assertThat(FeistelCipher.encrypt(max)).isBetween(0L, max);
        assertThat(FeistelCipher.encrypt(1)).isBetween(0L, max);
    }
}
