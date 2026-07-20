package io.github.rafaeljc.argus.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeDateFutureExceptionTest {

    private static final LocalDate TRADE_DATE = LocalDate.parse("2099-01-01");

    @Test
    void exposesWireCodeAndStatus() {
        TradeDateFutureException ex = new TradeDateFutureException(TRADE_DATE);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("TRADE_DATE_FUTURE");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEqualTo(List.of());
    }

    @Test
    void exposesCarriedTradeDate() {
        TradeDateFutureException ex = new TradeDateFutureException(TRADE_DATE);

        assertThat(ex.tradeDate()).isEqualTo(TRADE_DATE);
    }
}
