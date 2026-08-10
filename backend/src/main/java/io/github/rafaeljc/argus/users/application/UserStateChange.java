package io.github.rafaeljc.argus.users.application;

import io.github.rafaeljc.argus.users.domain.User;

public record UserStateChange(User user, boolean changed) {
    public UserStateChange {
        if (user == null) {
            throw new IllegalArgumentException("UserStateChange user must not be null");
        }
    }
}
