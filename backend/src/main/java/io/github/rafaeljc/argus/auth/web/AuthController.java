package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.application.AuthService;
import io.github.rafaeljc.argus.auth.application.LoginResult;
import io.github.rafaeljc.argus.auth.application.SessionStatusResult;
import io.github.rafaeljc.argus.auth.application.SignUpResult;
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

    private final AuthService authService;
    private final SessionCookieFactory sessionCookieFactory;
    private final CsrfCookieFactory csrfCookieFactory;

    AuthController(AuthService authService,
                   SessionCookieFactory sessionCookieFactory,
                   CsrfCookieFactory csrfCookieFactory) {
        this.authService = authService;
        this.sessionCookieFactory = sessionCookieFactory;
        this.csrfCookieFactory = csrfCookieFactory;
    }

    @PostMapping("/signup")
    ResponseEntity<SuccessEnvelope<SignUpResponse>> signup(@Valid @RequestBody SignUpRequest body) {
        SignUpResult result = authService.signUp(body.email(), body.password());
        SignUpResponse response = new SignUpResponse(
                result.userId().value().toString(), result.verificationSent());
        return ResponseEntity.created(ACCOUNT_ME_LOCATION).body(new SuccessEnvelope<>(response));
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        authService.verifyEmail(body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset-requests")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest body) {
        authService.requestPasswordReset(body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets")
    ResponseEntity<Void> completePasswordReset(@Valid @RequestBody CompletePasswordResetRequest body) {
        authService.completePasswordReset(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<SuccessEnvelope<SessionResponse>> login(@Valid @RequestBody LoginRequest body,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        LoginResult result = authService.login(
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
        authService.logout(principal.sessionId(), principal.userId());

        // SessionResolutionFilter has already added refreshed cookies to this response; append
        // the cleared cookies so the browser applies Max-Age=0 last for both names.
        response.addCookie(sessionCookieFactory.cleared());
        response.addCookie(csrfCookieFactory.cleared());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    ResponseEntity<SuccessEnvelope<SessionResponse>> status(
            @AuthenticationPrincipal AuthenticatedSession principal) {
        SessionStatusResult result = authService.getSessionStatus(principal.sessionId());
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
