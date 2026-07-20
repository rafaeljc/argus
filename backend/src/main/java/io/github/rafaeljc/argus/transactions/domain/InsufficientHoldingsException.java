package io.github.rafaeljc.argus.transactions.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;

public final class InsufficientHoldingsException extends DomainException {

    private final Ticker ticker;
    private final BigDecimal held;
    private final Quantity attempted;

    public InsufficientHoldingsException(Ticker ticker, BigDecimal held, Quantity attempted) {
        super("oversell: have %s of %s, attempted %s".formatted(held, ticker, attempted));
        this.ticker = ticker;
        this.held = held;
        this.attempted = attempted;
    }

    public Ticker ticker() {
        return ticker;
    }

    public BigDecimal held() {
        return held;
    }

    public Quantity attempted() {
        return attempted;
    }

    @Override
    public String code() {
        return "INSUFFICIENT_HOLDINGS";
    }
}
