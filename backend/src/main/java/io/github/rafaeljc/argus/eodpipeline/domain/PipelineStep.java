package io.github.rafaeljc.argus.eodpipeline.domain;

import java.util.Optional;

public enum PipelineStep {

    SYMBOLS("symbols"),
    PRICES("prices"),
    EVALUATE("evaluate");

    private final String wireValue;

    PipelineStep(String wireValue) {
        if (wireValue == null || wireValue.isBlank()) {
            throw new IllegalArgumentException("PipelineStep wireValue must not be blank");
        }
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Optional<PipelineStep> fromWireValue(String value) {
        for (PipelineStep step : values()) {
            if (step.wireValue.equals(value)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    public boolean isAtOrAfter(PipelineStep other) {
        return ordinal() >= other.ordinal();
    }
}
