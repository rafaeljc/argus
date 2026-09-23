package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Mock
    private SignUp signUp;

    @Mock
    private Login login;

    @Mock
    private Logout logout;

    @Mock
    private VerifyEmail verifyEmail;

    @Mock
    private RequestPasswordReset requestPasswordReset;

    @Mock
    private CompletePasswordReset completePasswordReset;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(signUp, login, logout, verifyEmail, requestPasswordReset, completePasswordReset);
    }

    @Test
    void signUp_delegatesToSignUpUseCase_returnsResultUnchanged() {
        SignUpResult expected = new SignUpResult(USER_ID, true);
        when(signUp.execute(EMAIL, PASSWORD)).thenReturn(expected);

        SignUpResult result = service.signUp(EMAIL, PASSWORD);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void login_delegatesToLoginUseCase_returnsResultUnchanged() {
        LoginResult expected = new LoginResult(USER_ID, true);
        when(login.execute(EMAIL, PASSWORD)).thenReturn(expected);

        LoginResult result = service.login(EMAIL, PASSWORD);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void logout_delegatesToLogoutUseCase() {
        service.logout(USER_ID);

        verify(logout).execute(USER_ID);
    }

    @Test
    void verifyEmail_delegatesToVerifyEmailUseCase() {
        service.verifyEmail("plain-token");

        verify(verifyEmail).execute("plain-token");
    }

    @Test
    void requestPasswordReset_delegatesToRequestPasswordResetUseCase() {
        service.requestPasswordReset(EMAIL);

        verify(requestPasswordReset).execute(EMAIL);
    }

    @Test
    void completePasswordReset_delegatesToCompletePasswordResetUseCase() {
        service.completePasswordReset("plain-token", "new-password");

        verify(completePasswordReset).execute("plain-token", "new-password");
    }
}
