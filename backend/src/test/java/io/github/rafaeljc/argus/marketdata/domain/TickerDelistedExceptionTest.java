package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.util.List;
import org.junit.jupiter.api.Test;

class TickerDelistedExceptionTest {

    private static final Ticker TICKER = new Ticker("GE");

    @Test
    void exposesWireCodeAndStatus() {
        TickerDelistedException ex = new TickerDelistedException(TICKER);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("TICKER_DELISTED");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEqualTo(List.of());
    }

    @Test
    void exposesCarriedTicker() {
        TickerDelistedException ex = new TickerDelistedException(TICKER);

        assertThat(ex.ticker()).isEqualTo(TICKER);
    }
}
