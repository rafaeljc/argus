package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FixedClockTest {

    @Test
    void now_returnsStoredInstantVerbatim() {
        Instant instant = Instant.parse("2026-06-16T12:34:56Z");
        FixedClock clock = new FixedClock(instant);

        assertThat(clock.now()).isEqualTo(instant);
    }

    @Test
    void today_derivesNyLocalDateFromStoredInstant() {
        // 2026-06-16T03:00:00Z is 2026-06-15 23:00 in America/New_York (EDT, UTC-4).
        FixedClock clock = new FixedClock(Instant.parse("2026-06-16T03:00:00Z"));

        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void constructor_nullInstant_throwsIllegalArgument() {
        assertThatThrownBy(() -> new FixedClock(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
