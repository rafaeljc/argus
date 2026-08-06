package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PipelineStepTest {

    @ParameterizedTest
    @CsvSource({
            "SYMBOLS,symbols",
            "PRICES,prices",
            "EVALUATE,evaluate"
    })
    void wireValue_matchesContractEnum(PipelineStep step, String expected) {
        assertThat(step.wireValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "symbols,SYMBOLS",
            "prices,PRICES",
            "evaluate,EVALUATE"
    })
    void fromWireValue_knownValue_returnsMatchingConstant(String wireValue, PipelineStep expected) {
        assertThat(PipelineStep.fromWireValue(wireValue)).contains(expected);
    }

    @Test
    void fromWireValue_unknown_returnsEmpty() {
        assertThat(PipelineStep.fromWireValue("bogus")).isEmpty();
    }

    @Test
    void fromWireValue_null_returnsEmpty() {
        assertThat(PipelineStep.fromWireValue(null)).isEmpty();
    }

    @Test
    void fromWireValue_isCaseSensitive() {
        assertThat(PipelineStep.fromWireValue("SYMBOLS")).isEmpty();
    }

    @Test
    void isAtOrAfter_earlierStep_returnsFalse() {
        assertThat(PipelineStep.SYMBOLS.isAtOrAfter(PipelineStep.PRICES)).isFalse();
    }

    @Test
    void isAtOrAfter_sameStep_returnsTrue() {
        assertThat(PipelineStep.PRICES.isAtOrAfter(PipelineStep.PRICES)).isTrue();
    }

    @Test
    void isAtOrAfter_laterStep_returnsTrue() {
        assertThat(PipelineStep.EVALUATE.isAtOrAfter(PipelineStep.SYMBOLS)).isTrue();
    }
}
