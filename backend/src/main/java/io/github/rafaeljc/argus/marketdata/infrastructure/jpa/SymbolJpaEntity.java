package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "symbols")
class SymbolJpaEntity {

    @Id
    @Column(name = "ticker", nullable = false, updatable = false)
    private String ticker;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    @Column(name = "name")
    private String name;

    @Column(name = "is_delisted", nullable = false)
    private boolean isDelisted;

    @Column(name = "last_vendor_check")
    private Instant lastVendorCheck;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SymbolJpaEntity() {
        // Hibernate
    }

    SymbolJpaEntity(String ticker,
                    String exchange,
                    String name,
                    boolean isDelisted,
                    Instant lastVendorCheck,
                    Instant createdAt,
                    Instant updatedAt) {
        this.ticker = ticker;
        this.exchange = exchange;
        this.name = name;
        this.isDelisted = isDelisted;
        this.lastVendorCheck = lastVendorCheck;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    String getTicker() {
        return ticker;
    }

    String getExchange() {
        return exchange;
    }

    String getName() {
        return name;
    }

    boolean isDelisted() {
        return isDelisted;
    }

    Instant getLastVendorCheck() {
        return lastVendorCheck;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
