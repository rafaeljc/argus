package io.github.rafaeljc.argus.common.domain;

import java.time.Instant;

public final class SystemClock extends Clock {

    private final java.time.Clock delegate = java.time.Clock.systemUTC();

    @Override
    public Instant now() {
        return delegate.instant();
    }
}
