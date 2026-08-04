package io.github.rafaeljc.argus.marketdata.infrastructure.noop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.ServiceUnavailableException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoOpLoggingVendorPriceGatewayTest {

    private final NoOpLoggingVendorPriceGateway gateway = new NoOpLoggingVendorPriceGateway();

    @Test
    void fetchSymbolUniverse_returnsEmptySet() {
        assertThat(gateway.fetchSymbolUniverse()).isEmpty();
    }

    @Test
    void fetchPriceHistory_throwsServiceUnavailable() {
        Ticker ticker = new Ticker("AAPL");
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        assertThatThrownBy(() -> gateway.fetchPriceHistory(ticker, start, end))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("vendor marketdata");
    }

    @Test
    void fetchClosesOn_throwsServiceUnavailable() {
        Set<Ticker> tickers = Set.of(new Ticker("AAPL"), new Ticker("MSFT"));
        LocalDate tradeDate = LocalDate.of(2026, 6, 22);

        assertThatThrownBy(() -> gateway.fetchClosesOn(tickers, tradeDate))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("vendor marketdata");
    }
}
