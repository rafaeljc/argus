package io.github.rafaeljc.argus.common.domain;

import java.util.regex.Pattern;

public record Ticker(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z.]{1,10}$");

    public Ticker {
        if (value == null) {
            throw new IllegalArgumentException("Ticker value must not be null");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Ticker must match " + PATTERN.pattern() + ", got: " + value);
        }
    }
}
