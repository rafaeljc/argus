package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StepStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,pending",
            "IN_PROGRESS,in_progress",
            "SUCCEEDED,succeeded",
            "FAILED,failed",
            "SKIPPED,skipped"
    })
    void dbValue_matchesSchemaCheckConstraint(StepStatus status, String expected) {
        assertThat(status.dbValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "pending,PENDING",
            "in_progress,IN_PROGRESS",
            "succeeded,SUCCEEDED",
            "failed,FAILED",
            "skipped,SKIPPED"
    })
    void fromDbValue_knownValue_returnsMatchingConstant(String dbValue, StepStatus expected) {
        assertThat(StepStatus.fromDbValue(dbValue)).isEqualTo(expected);
    }

    @Test
    void fromDbValue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> StepStatus.fromDbValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDbValue_unknown_throwsIllegalArgument() {
        assertThatThrownBy(() -> StepStatus.fromDbValue("cancelled"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void fromDbValue_isCaseSensitive() {
        assertThatThrownBy(() -> StepStatus.fromDbValue("PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
