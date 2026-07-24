package io.github.rafaeljc.argus.alerts.domain;

import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

public record AlertFiring(
        FiringId id,
        UserId userId,
        RuleId ruleId,
        Direction direction,
        Percentage threshold,
        AlertLookbackWindow window,
        Instant firedAt,
        Money portfolioValueStart,
        Money portfolioValueEnd,
        BigDecimal percentChange,
        LocalDate windowStartDate,
        LocalDate windowEndDate) {

    private static final int PERCENT_CHANGE_SCALE = 2;

    public AlertFiring {
        if (id == null) {
            throw new IllegalArgumentException("AlertFiring id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("AlertFiring userId must not be null");
        }
        if (ruleId == null) {
            throw new IllegalArgumentException("AlertFiring ruleId must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("AlertFiring direction must not be null");
        }
        if (threshold == null) {
            throw new IllegalArgumentException("AlertFiring threshold must not be null");
        }
        if (window == null) {
            throw new IllegalArgumentException("AlertFiring window must not be null");
        }
        if (firedAt == null) {
            throw new IllegalArgumentException("AlertFiring firedAt must not be null");
        }
        if (portfolioValueStart == null) {
            throw new IllegalArgumentException("AlertFiring portfolioValueStart must not be null");
        }
        if (portfolioValueEnd == null) {
            throw new IllegalArgumentException("AlertFiring portfolioValueEnd must not be null");
        }
        if (percentChange == null) {
            throw new IllegalArgumentException("AlertFiring percentChange must not be null");
        }
        if (windowStartDate == null) {
            throw new IllegalArgumentException("AlertFiring windowStartDate must not be null");
        }
        if (windowEndDate == null) {
            throw new IllegalArgumentException("AlertFiring windowEndDate must not be null");
        }
        if (windowEndDate.isBefore(windowStartDate)) {
            throw new IllegalArgumentException(
                    "AlertFiring windowEndDate must not be before windowStartDate: "
                            + windowEndDate + " < " + windowStartDate);
        }
        percentChange = percentChange.setScale(PERCENT_CHANGE_SCALE, RoundingMode.HALF_EVEN);
    }
}
