package io.github.rafaeljc.argus.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsufficientHoldingsExceptionTest {

    private static final Ticker TICKER = new Ticker("AAPL");
    private static final BigDecimal HELD = new BigDecimal("5");
    private static final Quantity ATTEMPTED = new Quantity(new BigDecimal("10"));

    @Test
    void exposesWireCodeAndStatus() {
        InsufficientHoldingsException ex = new InsufficientHoldingsException(TICKER, HELD, ATTEMPTED);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("INSUFFICIENT_HOLDINGS");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEqualTo(List.of());
    }

    @Test
    void exposesCarriedFields() {
        InsufficientHoldingsException ex = new InsufficientHoldingsException(TICKER, HELD, ATTEMPTED);

        assertThat(ex.ticker()).isEqualTo(TICKER);
        assertThat(ex.held()).isEqualTo(HELD);
        assertThat(ex.attempted()).isEqualTo(ATTEMPTED);
    }
}
