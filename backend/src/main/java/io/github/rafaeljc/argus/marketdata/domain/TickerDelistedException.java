package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Ticker;

public final class TickerDelistedException extends DomainException {

    private final Ticker ticker;

    public TickerDelistedException(Ticker ticker) {
        super("ticker delisted: " + ticker);
        this.ticker = ticker;
    }

    public Ticker ticker() {
        return ticker;
    }

    @Override
    public String code() {
        return "TICKER_DELISTED";
    }
}
