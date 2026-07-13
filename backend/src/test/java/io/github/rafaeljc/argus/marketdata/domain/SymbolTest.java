package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SymbolTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void constructor_allRequiredFields_isAllowed() {
        Symbol symbol = new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW);

        assertThat(symbol.ticker()).isEqualTo(AAPL);
        assertThat(symbol.exchange()).isEqualTo(Exchange.NASDAQ);
        assertThat(symbol.name()).isEqualTo("Apple Inc.");
        assertThat(symbol.isDelisted()).isFalse();
        assertThat(symbol.lastVendorCheck()).isEqualTo(NOW);
        assertThat(symbol.createdAt()).isEqualTo(NOW);
        assertThat(symbol.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void constructor_optionalFieldsNull_isAllowed() {
        Symbol symbol = new Symbol(AAPL, Exchange.NASDAQ, null, false, null, NOW, NOW);

        assertThat(symbol.name()).isNull();
        assertThat(symbol.lastVendorCheck()).isNull();
    }

    @Test
    void constructor_nullTicker_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Symbol(null, Exchange.NASDAQ, "Apple", false, NOW, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullExchange_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Symbol(AAPL, null, "Apple", false, NOW, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Symbol(AAPL, Exchange.NASDAQ, "Apple", false, NOW, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullUpdatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Symbol(AAPL, Exchange.NASDAQ, "Apple", false, NOW, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_updatedAtEqualsCreatedAt_isAllowed() {
        Symbol symbol = new Symbol(AAPL, Exchange.NASDAQ, "Apple", false, NOW, NOW, NOW);

        assertThat(symbol.updatedAt()).isEqualTo(symbol.createdAt());
    }

    @Test
    void constructor_updatedAtBeforeCreatedAt_throwsIllegalArgument() {
        Instant earlier = NOW.minusSeconds(1);

        assertThatThrownBy(() -> new Symbol(AAPL, Exchange.NASDAQ, "Apple", false, NOW, NOW, earlier))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
