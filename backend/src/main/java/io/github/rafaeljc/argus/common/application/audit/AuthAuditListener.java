package io.github.rafaeljc.argus.common.application.audit;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.AccountDeleted;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.AccountDeletionFailed;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.EmailVerificationFailed;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.EmailVerified;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.Failure;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.LoginFailed;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.LoginSucceeded;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.LogoutSucceeded;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.PasswordResetCompleted;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.PasswordResetCompletionFailed;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.PasswordResetRequestFailed;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.PasswordResetRequested;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.SignupFailed;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.SignupSucceeded;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent.Success;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditListener.class);

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILURE = "failure";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSuccess(Success event) {
        String code = codeOf(event);
        StructuredArgument[] variable = successFields(event);
        emit(code, STATUS_SUCCESS, null, variable);
    }

    @EventListener
    public void onFailure(Failure event) {
        String code = codeOf(event);
        FailurePayload payload = failurePayload(event);
        emit(code, STATUS_FAILURE, payload.reason(), payload.variable());
    }

    private static String codeOf(AuthAuditEvent event) {
        return switch (event) {
            case SignupSucceeded ignored -> "auth.signup";
            case SignupFailed ignored -> "auth.signup";
            case LoginSucceeded ignored -> "auth.login";
            case LoginFailed ignored -> "auth.login";
            case LogoutSucceeded ignored -> "auth.logout";
            case EmailVerified ignored -> "auth.verify_email";
            case EmailVerificationFailed ignored -> "auth.verify_email";
            case PasswordResetRequested ignored -> "auth.request_password_reset";
            case PasswordResetRequestFailed ignored -> "auth.request_password_reset";
            case PasswordResetCompleted ignored -> "auth.complete_password_reset";
            case PasswordResetCompletionFailed ignored -> "auth.complete_password_reset";
            case AccountDeleted ignored -> "auth.delete_account";
            case AccountDeletionFailed ignored -> "auth.delete_account";
        };
    }

    private static StructuredArgument[] successFields(Success event) {
        return switch (event) {
            case SignupSucceeded e -> new StructuredArgument[]{
                    kv("user_id", e.userId().value()), kv("email", e.email())};
            case LoginSucceeded e -> new StructuredArgument[]{
                    kv("user_id", e.userId().value()), kv("email", e.email())};
            case LogoutSucceeded e -> new StructuredArgument[]{
                    kv("user_id", e.userId().value())};
            case EmailVerified e -> new StructuredArgument[]{
                    kv("user_id", e.userId().value()), kv("email", e.email())};
            case PasswordResetRequested e -> new StructuredArgument[]{
                    kv("email", e.email())};
            case PasswordResetCompleted e -> new StructuredArgument[]{
                    kv("user_id", e.userId().value()), kv("email", e.email())};
            case AccountDeleted e -> new StructuredArgument[]{
                    kv("user_id", e.userId().value()), kv("email", e.email())};
        };
    }

    private static FailurePayload failurePayload(Failure event) {
        return switch (event) {
            case SignupFailed e -> new FailurePayload(e.reason(), new StructuredArgument[]{
                    kv("email", e.email())});
            case LoginFailed e -> new FailurePayload(e.reason(), new StructuredArgument[]{
                    kv("email", e.email())});
            case EmailVerificationFailed e -> new FailurePayload(e.reason(), new StructuredArgument[]{});
            case PasswordResetRequestFailed e -> new FailurePayload(e.reason(), new StructuredArgument[]{
                    kv("email", e.email())});
            case PasswordResetCompletionFailed e -> new FailurePayload(e.reason(), new StructuredArgument[]{});
            case AccountDeletionFailed e -> new FailurePayload(e.reason(), new StructuredArgument[]{
                    kv("user_id", e.userId().value())});
        };
    }

    private static void emit(String code, String status, String reason, StructuredArgument[] variable) {
        int extras = reason == null ? 2 : 3;
        StructuredArgument[] args = new StructuredArgument[variable.length + extras];
        int i = 0;
        args[i++] = kv("event", code);
        for (StructuredArgument v : variable) {
            args[i++] = v;
        }
        args[i++] = kv("status", status);
        if (reason != null) {
            args[i] = kv("reason", reason);
        }
        log.info(code, (Object[]) args);
    }

    private record FailurePayload(String reason, StructuredArgument[] variable) {}
}
