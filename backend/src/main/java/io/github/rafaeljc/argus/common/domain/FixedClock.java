package io.github.rafaeljc.argus.common.domain;

import java.time.Instant;

public final class FixedClock extends Clock {

    private final Instant now;

    public FixedClock(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("FixedClock now must not be null");
        }
        this.now = now;
    }

    @Override
    public Instant now() {
        return now;
    }
}
