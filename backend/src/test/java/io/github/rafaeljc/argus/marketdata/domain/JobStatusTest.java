package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JobStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,pending",
            "IN_PROGRESS,in_progress",
            "COMPLETED,completed",
            "FAILED,failed"
    })
    void dbValue_matchesSchemaCheckConstraint(JobStatus status, String expected) {
        assertThat(status.dbValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "pending,PENDING",
            "in_progress,IN_PROGRESS",
            "completed,COMPLETED",
            "failed,FAILED"
    })
    void fromDbValue_knownValue_returnsMatchingConstant(String dbValue, JobStatus expected) {
        assertThat(JobStatus.fromDbValue(dbValue)).isEqualTo(expected);
    }

    @Test
    void fromDbValue_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> JobStatus.fromDbValue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromDbValue_unknown_throwsIllegalArgument() {
        assertThatThrownBy(() -> JobStatus.fromDbValue("cancelled"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void fromDbValue_isCaseSensitive() {
        assertThatThrownBy(() -> JobStatus.fromDbValue("PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
