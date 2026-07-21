package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Ticker;

public final class TickerNotFoundException extends DomainException {

    private final Ticker ticker;

    public TickerNotFoundException(Ticker ticker) {
        super("ticker not found: " + ticker);
        this.ticker = ticker;
    }

    public Ticker ticker() {
        return ticker;
    }

    @Override
    public String code() {
        return "TICKER_NOT_FOUND";
    }
}
