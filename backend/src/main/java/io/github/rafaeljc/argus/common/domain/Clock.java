package io.github.rafaeljc.argus.common.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public abstract class Clock {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    public abstract Instant now();

    public LocalDate today() {
        return now().atZone(ZONE).toLocalDate();
    }
}
