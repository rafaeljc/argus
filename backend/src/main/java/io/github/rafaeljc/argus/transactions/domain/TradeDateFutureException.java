package io.github.rafaeljc.argus.transactions.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import java.time.LocalDate;

public final class TradeDateFutureException extends DomainException {

    private final LocalDate tradeDate;

    public TradeDateFutureException(LocalDate tradeDate) {
        super("trade date cannot be in the future: " + tradeDate);
        this.tradeDate = tradeDate;
    }

    public LocalDate tradeDate() {
        return tradeDate;
    }

    @Override
    public String code() {
        return "TRADE_DATE_FUTURE";
    }
}
