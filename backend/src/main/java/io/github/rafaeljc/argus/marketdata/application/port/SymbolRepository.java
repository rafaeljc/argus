package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.util.Optional;

public interface SymbolRepository {

    Symbol save(Symbol symbol);

    Optional<Symbol> findByTicker(Ticker ticker);

    void deleteByTicker(Ticker ticker);
}
