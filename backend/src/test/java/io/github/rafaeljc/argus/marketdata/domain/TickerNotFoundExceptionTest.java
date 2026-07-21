package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.util.List;
import org.junit.jupiter.api.Test;

class TickerNotFoundExceptionTest {

    private static final Ticker TICKER = new Ticker("ZZZZ");

    @Test
    void exposesWireCodeAndStatus() {
        TickerNotFoundException ex = new TickerNotFoundException(TICKER);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("TICKER_NOT_FOUND");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEqualTo(List.of());
    }

    @Test
    void exposesCarriedTicker() {
        TickerNotFoundException ex = new TickerNotFoundException(TICKER);

        assertThat(ex.ticker()).isEqualTo(TICKER);
    }
}
