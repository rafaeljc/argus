package io.github.rafaeljc.argus.common.web;

public record ErrorEnvelope(ApiError error) {

    public ErrorEnvelope {
        if (error == null) {
            throw new IllegalArgumentException("ErrorEnvelope error must not be null");
        }
    }
}
