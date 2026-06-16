package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TickerTest {

    @Test
    void constructor_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Ticker(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                 // empty
            "aapl",             // lowercase
            "AAPL1",            // digit
            "BRK-B",            // hyphen (spec uses '.', not '-')
            "ABCDEFGHIJK",      // 11 chars, exceeds max
            "AAPL "             // whitespace
    })
    void constructor_invalidInput_throwsIllegalArgument(String invalid) {
        assertThatThrownBy(() -> new Ticker(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_simpleTicker_isAllowed() {
        assertThat(new Ticker("AAPL").value()).isEqualTo("AAPL");
    }

    @Test
    void constructor_tickerWithDot_isAllowed() {
        assertThat(new Ticker("BRK.B").value()).isEqualTo("BRK.B");
    }

    @Test
    void constructor_singleChar_isAllowed() {
        assertThat(new Ticker("F").value()).isEqualTo("F");
    }

    @Test
    void constructor_tenChars_isAllowed() {
        assertThat(new Ticker("ABCDEFGHIJ").value()).isEqualTo("ABCDEFGHIJ");
    }
}
