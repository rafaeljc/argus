package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;

final class SymbolEntityMapper {

    private SymbolEntityMapper() {
    }

    static Symbol toDomain(SymbolJpaEntity entity) {
        return new Symbol(
                new Ticker(entity.getTicker()),
                Exchange.fromDbValue(entity.getExchange()),
                entity.getName(),
                entity.isDelisted(),
                entity.getLastVendorCheck(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static SymbolJpaEntity toEntity(Symbol symbol) {
        return new SymbolJpaEntity(
                symbol.ticker().value(),
                symbol.exchange().dbValue(),
                symbol.name(),
                symbol.isDelisted(),
                symbol.lastVendorCheck(),
                symbol.createdAt(),
                symbol.updatedAt());
    }
}
