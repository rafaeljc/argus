package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.common.domain.UserId;

public record SignUpResult(UserId userId, boolean verificationSent) {}
