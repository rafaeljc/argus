package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.application.GetSessionStatus;
import io.github.rafaeljc.argus.auth.application.Login;
import io.github.rafaeljc.argus.auth.application.LoginResult;
import io.github.rafaeljc.argus.auth.application.Logout;
import io.github.rafaeljc.argus.auth.application.SessionStatusResult;
import io.github.rafaeljc.argus.auth.application.SignUp;
import io.github.rafaeljc.argus.auth.application.SignUpResult;
import io.github.rafaeljc.argus.auth.application.VerifyEmail;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private static final URI ACCOUNT_ME_LOCATION = URI.create("/api/v1/account/me");
    private static final int USER_AGENT_MAX_CHARS = 512;
    private static final String USER_AGENT_HEADER = "User-Agent";

    private final SignUp signUp;
    private final Login login;
    private final Logout logout;
    private final GetSessionStatus getSessionStatus;
    private final VerifyEmail verifyEmail;
    private final SessionCookieFactory sessionCookieFactory;
    private final CsrfCookieFactory csrfCookieFactory;

    AuthController(SignUp signUp,
                   Login login,
                   Logout logout,
                   GetSessionStatus getSessionStatus,
                   VerifyEmail verifyEmail,
                   SessionCookieFactory sessionCookieFactory,
                   CsrfCookieFactory csrfCookieFactory) {
        this.signUp = signUp;
        this.login = login;
        this.logout = logout;
        this.getSessionStatus = getSessionStatus;
        this.verifyEmail = verifyEmail;
        this.sessionCookieFactory = sessionCookieFactory;
        this.csrfCookieFactory = csrfCookieFactory;
    }

    @PostMapping("/signup")
    ResponseEntity<SuccessEnvelope<SignUpResponse>> signup(@Valid @RequestBody SignUpRequest body) {
        SignUpResult result = signUp.execute(body.email(), body.password());
        SignUpResponse response = new SignUpResponse(
                result.userId().value().toString(), result.verificationSent());
        return ResponseEntity.created(ACCOUNT_ME_LOCATION).body(new SuccessEnvelope<>(response));
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        verifyEmail.execute(body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<SuccessEnvelope<SessionResponse>> login(@Valid @RequestBody LoginRequest body,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        LoginResult result = login.execute(
                body.email(),
                body.password(),
                request.getRemoteAddr(),
                truncatedUserAgent(request));

        response.addCookie(sessionCookieFactory.forToken(result.sessionToken()));
        response.addCookie(csrfCookieFactory.forToken(result.csrfToken()));

        return ResponseEntity.ok(new SuccessEnvelope<>(new SessionResponse(
                result.userId().value().toString(), result.expiresAt())));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedSession principal,
                                HttpServletResponse response) {
        logout.execute(principal.sessionId());

        // SessionResolutionFilter has already added refreshed cookies to this response; append
        // the cleared cookies so the browser applies Max-Age=0 last for both names.
        response.addCookie(sessionCookieFactory.cleared());
        response.addCookie(csrfCookieFactory.cleared());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    ResponseEntity<SuccessEnvelope<SessionResponse>> status(
            @AuthenticationPrincipal AuthenticatedSession principal) {
        SessionStatusResult result = getSessionStatus.execute(principal.sessionId());
        return ResponseEntity.ok(new SuccessEnvelope<>(new SessionResponse(
                result.userId().value().toString(), result.expiresAt())));
    }

    private static String truncatedUserAgent(HttpServletRequest request) {
        String value = request.getHeader(USER_AGENT_HEADER);
        if (value == null) {
            return null;
        }
        return value.length() <= USER_AGENT_MAX_CHARS ? value : value.substring(0, USER_AGENT_MAX_CHARS);
    }
}
