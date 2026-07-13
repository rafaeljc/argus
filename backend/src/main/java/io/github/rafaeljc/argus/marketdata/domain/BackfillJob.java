package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;

public record BackfillJob(JobId id,
                          Ticker ticker,
                          UserId userId,
                          JobStatus status,
                          LocalDate startDate,
                          LocalDate endDate,
                          Integer priceCount,
                          String errorMessage,
                          Instant createdAt,
                          Instant startedAt,
                          Instant completedAt) {

    public BackfillJob {
        if (id == null) {
            throw new IllegalArgumentException("BackfillJob id must not be null");
        }
        if (ticker == null) {
            throw new IllegalArgumentException("BackfillJob ticker must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("BackfillJob userId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("BackfillJob status must not be null");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("BackfillJob startDate must not be null");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("BackfillJob endDate must not be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "BackfillJob startDate must be <= endDate, got start=" + startDate + " end=" + endDate);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("BackfillJob createdAt must not be null");
        }
        if (priceCount != null && priceCount < 0) {
            throw new IllegalArgumentException("BackfillJob priceCount must be >= 0, got: " + priceCount);
        }
        if (startedAt != null && startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("BackfillJob startedAt must not be before createdAt");
        }
        if (completedAt != null && (startedAt == null || completedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException(
                    "BackfillJob completedAt requires startedAt to be set and to be <= completedAt");
        }
    }
}
