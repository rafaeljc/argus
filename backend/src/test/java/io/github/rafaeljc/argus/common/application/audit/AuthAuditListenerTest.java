package io.github.rafaeljc.argus.common.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AuthAuditListenerTest {

    private static final String EMAIL = "alice@example.com";

    private AuthAuditListener listener;
    private ListAppender<ILoggingEvent> appender;
    private Logger sink;

    @BeforeEach
    void setUp() {
        listener = new AuthAuditListener();
        sink = (Logger) LoggerFactory.getLogger(AuthAuditListener.class);
        appender = new ListAppender<>();
        appender.start();
        sink.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        sink.detachAppender(appender);
    }

    @Test
    void onSuccess_signupSucceeded_emitsAuthSignupWithUserIdEmailAndSuccessStatus() {
        UserId userId = newUserId();

        listener.onSuccess(new AuthAuditEvent.SignupSucceeded(userId, EMAIL));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMessage()).isEqualTo("auth.signup");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.signup",
                "user_id=" + userId.value(),
                "email=" + EMAIL,
                "status=success");
    }

    @Test
    void onFailure_signupFailed_emitsAuthSignupWithEmailReasonAndFailureStatus() {
        listener.onFailure(new AuthAuditEvent.SignupFailed(EMAIL, "EMAIL_ALREADY_TAKEN"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.signup");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.signup",
                "email=" + EMAIL,
                "status=failure",
                "reason=EMAIL_ALREADY_TAKEN");
    }

    @Test
    void onSuccess_loginSucceeded_emitsAuthLoginWithUserIdEmailAndSuccessStatus() {
        UserId userId = newUserId();

        listener.onSuccess(new AuthAuditEvent.LoginSucceeded(userId, EMAIL));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.login");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.login",
                "user_id=" + userId.value(),
                "email=" + EMAIL,
                "status=success");
    }

    @Test
    void onFailure_loginFailed_emitsAuthLoginWithEmailReasonAndFailureStatus() {
        listener.onFailure(new AuthAuditEvent.LoginFailed(EMAIL, "INVALID_CREDENTIALS"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.login");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.login",
                "email=" + EMAIL,
                "status=failure",
                "reason=INVALID_CREDENTIALS");
    }

    @Test
    void onSuccess_logoutSucceeded_emitsAuthLogoutWithUserIdAndSuccessStatus() {
        UserId userId = newUserId();

        listener.onSuccess(new AuthAuditEvent.LogoutSucceeded(userId));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.logout");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.logout",
                "user_id=" + userId.value(),
                "status=success");
    }

    @Test
    void onSuccess_emailVerified_emitsAuthVerifyEmailWithUserIdEmailAndSuccessStatus() {
        UserId userId = newUserId();

        listener.onSuccess(new AuthAuditEvent.EmailVerified(userId, EMAIL));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.verify_email");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.verify_email",
                "user_id=" + userId.value(),
                "email=" + EMAIL,
                "status=success");
    }

    @Test
    void onFailure_emailVerificationFailed_emitsAuthVerifyEmailWithReasonAndFailureStatus() {
        listener.onFailure(new AuthAuditEvent.EmailVerificationFailed("INVALID_TOKEN"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.verify_email");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.verify_email",
                "status=failure",
                "reason=INVALID_TOKEN");
    }

    @Test
    void onSuccess_passwordResetRequested_emitsAuthRequestPasswordResetWithEmailAndSuccessStatus() {
        listener.onSuccess(new AuthAuditEvent.PasswordResetRequested(EMAIL));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.request_password_reset");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.request_password_reset",
                "email=" + EMAIL,
                "status=success");
    }

    @Test
    void onFailure_passwordResetRequestFailed_emitsAuthRequestPasswordResetWithEmailReasonAndFailureStatus() {
        listener.onFailure(new AuthAuditEvent.PasswordResetRequestFailed(EMAIL, "RATE_LIMIT_EXCEEDED"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.request_password_reset");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.request_password_reset",
                "email=" + EMAIL,
                "status=failure",
                "reason=RATE_LIMIT_EXCEEDED");
    }

    @Test
    void onSuccess_passwordResetCompleted_emitsAuthCompletePasswordResetWithUserIdEmailAndSuccessStatus() {
        UserId userId = newUserId();

        listener.onSuccess(new AuthAuditEvent.PasswordResetCompleted(userId, EMAIL));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.complete_password_reset");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.complete_password_reset",
                "user_id=" + userId.value(),
                "email=" + EMAIL,
                "status=success");
    }

    @Test
    void onFailure_passwordResetCompletionFailed_emitsAuthCompletePasswordResetWithReasonAndFailureStatus() {
        listener.onFailure(new AuthAuditEvent.PasswordResetCompletionFailed("INVALID_TOKEN"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.complete_password_reset");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.complete_password_reset",
                "status=failure",
                "reason=INVALID_TOKEN");
    }

    @Test
    void onSuccess_accountDeleted_emitsAuthDeleteAccountWithUserIdEmailAndSuccessStatus() {
        UserId userId = newUserId();

        listener.onSuccess(new AuthAuditEvent.AccountDeleted(userId, EMAIL));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.delete_account");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.delete_account",
                "user_id=" + userId.value(),
                "email=" + EMAIL,
                "status=success");
    }

    @Test
    void onFailure_accountDeletionFailed_emitsAuthDeleteAccountWithUserIdReasonAndFailureStatus() {
        UserId userId = newUserId();

        listener.onFailure(new AuthAuditEvent.AccountDeletionFailed(userId, "INVALID_CURRENT_PASSWORD"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMessage()).isEqualTo("auth.delete_account");
        assertThat(argStrings(event)).containsExactlyInAnyOrder(
                "event=auth.delete_account",
                "user_id=" + userId.value(),
                "status=failure",
                "reason=INVALID_CURRENT_PASSWORD");
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0);
    }

    private static List<String> argStrings(ILoggingEvent event) {
        Object[] args = event.getArgumentArray();
        return Arrays.stream(args).map(Object::toString).toList();
    }

    private static UserId newUserId() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        return new UserId(id);
    }
}
