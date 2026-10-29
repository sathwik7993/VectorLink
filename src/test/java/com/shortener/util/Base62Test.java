package com.shortener.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62Test {

    @Test
    void encodesKnownValues() {
        assertThat(Base62.encode(0)).isEqualTo("0");
        assertThat(Base62.encode(61)).isEqualTo("Z");
        assertThat(Base62.encode(62)).isEqualTo("10");
    }

    @Test
    void roundTripsAcrossTheFeistelOutputRange() {
        long[] values = {1, 999_001, 1_000_000, 123_456_789_012L, (1L << 62) - 1};
        for (long value : values) {
            assertThat(Base62.decode(Base62.encode(value))).isEqualTo(value);
        }
    }

    @Test
    void producesUniqueCodesForSequentialIds() {
        Set<String> codes = new HashSet<>();
        for (long id = 1_000_000; id < 1_010_000; id++) {
            codes.add(Base62.encode(FeistelCipher.encrypt(id)));
        }
        assertThat(codes).hasSize(10_000);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> Base62.encode(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("ab$")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class);
    }
}
