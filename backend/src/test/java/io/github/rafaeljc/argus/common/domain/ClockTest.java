package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClockTest {

    @Test
    void today_default_derivesNyLocalDateFromNow() {
        // 2026-06-16T03:00:00Z is 2026-06-15 23:00 in America/New_York (EDT, UTC-4).
        Instant instant = Instant.parse("2026-06-16T03:00:00Z");
        Clock clock = new Clock() {
            @Override
            public Instant now() {
                return instant;
            }
        };

        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 6, 15));
    }
}
