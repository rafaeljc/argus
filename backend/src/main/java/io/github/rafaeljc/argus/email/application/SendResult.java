package io.github.rafaeljc.argus.email.application;

public record SendResult(boolean success, String errorMessage) {

    public SendResult {
        if (success && errorMessage != null) {
            throw new IllegalArgumentException("SendResult success=true must have null errorMessage");
        }
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("SendResult success=false must have non-blank errorMessage");
        }
    }
}
