package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExchangeTest {

    @ParameterizedTest
    @CsvSource({
            "NYSE,NYSE",
            "NASDAQ,NASDAQ"
    })
    void dbValue_matchesSchemaCheckConstraint(Exchange exchange, String expected) {
        assertThat(exchange.dbValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "NYSE,NYSE",
            "NASDAQ,NASDAQ"
    })
    void fromDbValue_knownValue_returnsMatchingConstant(String dbValue, Exchange expected) {
        assertThat(Exchange.fromDbValue(dbValue)).isEqualTo(expected);
    }

    @Test
    void fromDbValue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> Exchange.fromDbValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDbValue_unknown_throwsIllegalArgument() {
        assertThatThrownBy(() -> Exchange.fromDbValue("LSE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LSE");
    }

    @Test
    void fromDbValue_isCaseSensitive() {
        assertThatThrownBy(() -> Exchange.fromDbValue("nyse"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
