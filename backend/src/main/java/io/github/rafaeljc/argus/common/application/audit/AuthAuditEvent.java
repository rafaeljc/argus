package io.github.rafaeljc.argus.common.application.audit;

import io.github.rafaeljc.argus.common.domain.UserId;

public sealed interface AuthAuditEvent {

    sealed interface Success extends AuthAuditEvent
            permits SignupSucceeded, LoginSucceeded, LogoutSucceeded, EmailVerified,
                    PasswordResetRequested, PasswordResetCompleted, AccountDeleted {}

    sealed interface Failure extends AuthAuditEvent
            permits SignupFailed, LoginFailed, EmailVerificationFailed,
                    PasswordResetRequestFailed, PasswordResetCompletionFailed,
                    AccountDeletionFailed {}

    record SignupSucceeded(UserId userId, String email) implements Success {}

    record SignupFailed(String email, String reason) implements Failure {}

    record LoginSucceeded(UserId userId, String email) implements Success {}

    record LoginFailed(String email, String reason) implements Failure {}

    record LogoutSucceeded(UserId userId) implements Success {}

    record EmailVerified(UserId userId, String email) implements Success {}

    record EmailVerificationFailed(String reason) implements Failure {}

    record PasswordResetRequested(String email) implements Success {}

    record PasswordResetRequestFailed(String email, String reason) implements Failure {}

    record PasswordResetCompleted(UserId userId, String email) implements Success {}

    record PasswordResetCompletionFailed(String reason) implements Failure {}

    record AccountDeleted(UserId userId, String email) implements Success {}

    record AccountDeletionFailed(UserId userId, String reason) implements Failure {}
}
