package io.github.rafaeljc.argus.common.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class SystemClock extends Clock {

    private final java.time.Clock delegate = java.time.Clock.systemUTC();

    // Postgres TIMESTAMPTZ is microsecond-precision; truncate at the source so in-memory
    // Instants match what JPA reads back, eliminating roundtrip precision drift.
    @Override
    public Instant now() {
        return delegate.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
