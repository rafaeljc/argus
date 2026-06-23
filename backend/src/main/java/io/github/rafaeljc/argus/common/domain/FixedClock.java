package io.github.rafaeljc.argus.common.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class FixedClock extends Clock {

    private final Instant now;

    public FixedClock(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("FixedClock now must not be null");
        }
        // Match SystemClock precision so tests see the same shape as production reads.
        this.now = now.truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public Instant now() {
        return now;
    }
}
