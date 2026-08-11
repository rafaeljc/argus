package io.github.rafaeljc.argus.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RebuildJobStatusTest {

    @Test
    void fromDbValue_knownValue_returnsMatchingStatus() {
        assertThat(RebuildJobStatus.fromDbValue("pending")).isEqualTo(RebuildJobStatus.PENDING);
        assertThat(RebuildJobStatus.fromDbValue("in_progress")).isEqualTo(RebuildJobStatus.IN_PROGRESS);
        assertThat(RebuildJobStatus.fromDbValue("completed")).isEqualTo(RebuildJobStatus.COMPLETED);
        assertThat(RebuildJobStatus.fromDbValue("failed")).isEqualTo(RebuildJobStatus.FAILED);
    }

    @Test
    void fromDbValue_unknownValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> RebuildJobStatus.fromDbValue("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
