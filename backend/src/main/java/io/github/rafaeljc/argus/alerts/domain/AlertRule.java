package io.github.rafaeljc.argus.alerts.domain;

import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public record AlertRule(
        RuleId id,
        UserId userId,
        Direction direction,
        Percentage threshold,
        AlertLookbackWindow window,
        Instant createdAt) {

    public AlertRule {
        if (id == null) {
            throw new IllegalArgumentException("AlertRule id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("AlertRule userId must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("AlertRule direction must not be null");
        }
        if (threshold == null) {
            throw new IllegalArgumentException("AlertRule threshold must not be null");
        }
        if (window == null) {
            throw new IllegalArgumentException("AlertRule window must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("AlertRule createdAt must not be null");
        }
    }
}
