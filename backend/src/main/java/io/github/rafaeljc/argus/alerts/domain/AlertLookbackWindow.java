package io.github.rafaeljc.argus.alerts.domain;

import java.util.Set;

public record AlertLookbackWindow(int days) {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 7, 30, 90, 365, 1095, 1825);

    public AlertLookbackWindow {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new IllegalArgumentException(
                    "AlertLookbackWindow days must be one of " + ALLOWED_DAYS + ", got: " + days);
        }
    }
}
