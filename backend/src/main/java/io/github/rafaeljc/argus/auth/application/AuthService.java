package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final SignUp signUp;
    private final Login login;
    private final Logout logout;
    private final VerifyEmail verifyEmail;
    private final RequestPasswordReset requestPasswordReset;
    private final CompletePasswordReset completePasswordReset;

    public AuthService(SignUp signUp,
                       Login login,
                       Logout logout,
                       VerifyEmail verifyEmail,
                       RequestPasswordReset requestPasswordReset,
                       CompletePasswordReset completePasswordReset) {
        this.signUp = signUp;
        this.login = login;
        this.logout = logout;
        this.verifyEmail = verifyEmail;
        this.requestPasswordReset = requestPasswordReset;
        this.completePasswordReset = completePasswordReset;
    }

    @Transactional
    public SignUpResult signUp(String email, String password) {
        return signUp.execute(email, password);
    }

    @Transactional
    public LoginResult login(String email, String password) {
        return login.execute(email, password);
    }

    @Transactional
    public void logout(UserId userId) {
        logout.execute(userId);
    }

    @Transactional
    public void verifyEmail(String plainToken) {
        verifyEmail.execute(plainToken);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        requestPasswordReset.execute(email);
    }

    @Transactional
    public void completePasswordReset(String plainToken, String newPassword) {
        completePasswordReset.execute(plainToken, newPassword);
    }
}
