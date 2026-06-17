package io.github.rafaeljc.argus.common.web;

public record SuccessEnvelope<T>(T data) {

    public SuccessEnvelope {
        if (data == null) {
            throw new IllegalArgumentException("SuccessEnvelope data must not be null");
        }
    }
}
