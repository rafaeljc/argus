package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SnapshotRangeTest {

    private static final LocalDate ANCHOR = LocalDate.parse("2026-07-22");

    @ParameterizedTest
    @CsvSource({
        "1m, M1",
        "3m, M3",
        "6m, M6",
        "1y, Y1",
        "3y, Y3",
        "5y, Y5",
        "1Y, Y1",
    })
    void fromWire_knownValue_returnsMatchingConstant(String wire, SnapshotRange expected) {
        assertThat(SnapshotRange.fromWire(wire)).isEqualTo(expected);
    }

    @Test
    void fromWire_unknownValue_throwsInvalidSnapshotRangeException() {
        assertThatThrownBy(() -> SnapshotRange.fromWire("2y"))
                .isInstanceOfSatisfying(InvalidSnapshotRangeException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(ex.status()).isEqualTo(422);
                });
    }

    @ParameterizedTest
    @CsvSource({
        "M1, 2026-06-22",
        "M3, 2026-04-22",
        "M6, 2026-01-22",
        "Y1, 2025-07-22",
        "Y3, 2023-07-22",
        "Y5, 2021-07-22",
    })
    void from_anchor_subtractsRangePeriod(SnapshotRange range, LocalDate expected) {
        assertThat(range.from(ANCHOR)).isEqualTo(expected);
    }
}
