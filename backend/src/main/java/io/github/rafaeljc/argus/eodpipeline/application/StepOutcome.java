package io.github.rafaeljc.argus.eodpipeline.application;

public record StepOutcome(boolean succeeded, String errorMessage) {

    private static final StepOutcome SUCCESS = new StepOutcome(true, null);

    public StepOutcome {
        if (!succeeded && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("a failed StepOutcome must carry an error message");
        }
    }

    public static StepOutcome success() {
        return SUCCESS;
    }

    public static StepOutcome failure(String errorMessage) {
        return new StepOutcome(false, errorMessage);
    }
}
