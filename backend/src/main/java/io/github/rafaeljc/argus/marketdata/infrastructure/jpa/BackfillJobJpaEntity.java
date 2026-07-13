package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "backfill_jobs")
class BackfillJobJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ticker", nullable = false, updatable = false)
    private String ticker;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "price_count")
    private Integer priceCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected BackfillJobJpaEntity() {
        // Hibernate
    }

    BackfillJobJpaEntity(UUID id,
                         String ticker,
                         UUID userId,
                         String status,
                         LocalDate startDate,
                         LocalDate endDate,
                         Integer priceCount,
                         String errorMessage,
                         Instant createdAt,
                         Instant startedAt,
                         Instant completedAt) {
        this.id = id;
        this.ticker = ticker;
        this.userId = userId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.priceCount = priceCount;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    UUID getId() {
        return id;
    }

    String getTicker() {
        return ticker;
    }

    UUID getUserId() {
        return userId;
    }

    String getStatus() {
        return status;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    Integer getPriceCount() {
        return priceCount;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
