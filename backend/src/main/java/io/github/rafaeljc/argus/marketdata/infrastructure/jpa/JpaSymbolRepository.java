package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaSymbolRepository implements SymbolRepository {

    private final SpringDataSymbolJpaRepository jpa;

    JpaSymbolRepository(SpringDataSymbolJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Symbol save(Symbol symbol) {
        SymbolJpaEntity persisted = jpa.save(SymbolEntityMapper.toEntity(symbol));
        return SymbolEntityMapper.toDomain(persisted);
    }

    @Override
    public Optional<Symbol> findByTicker(Ticker ticker) {
        return jpa.findById(ticker.value()).map(SymbolEntityMapper::toDomain);
    }

    @Override
    public void deleteByTicker(Ticker ticker) {
        jpa.deleteById(ticker.value());
    }
}
