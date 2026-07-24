package io.github.rafaeljc.argus.alerts.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Percentage;

public final class DuplicateAlertRuleException extends DomainException {

    private final Direction direction;
    private final Percentage threshold;
    private final AlertLookbackWindow window;

    public DuplicateAlertRuleException(Direction direction, Percentage threshold, AlertLookbackWindow window) {
        super("duplicate active alert rule: %s %s over %s days".formatted(direction, threshold, window.days()));
        this.direction = direction;
        this.threshold = threshold;
        this.window = window;
    }

    public Direction direction() {
        return direction;
    }

    public Percentage threshold() {
        return threshold;
    }

    public AlertLookbackWindow window() {
        return window;
    }

    @Override
    public String code() {
        return "DUPLICATE_RULE";
    }

    @Override
    public int status() {
        return 409;
    }
}
