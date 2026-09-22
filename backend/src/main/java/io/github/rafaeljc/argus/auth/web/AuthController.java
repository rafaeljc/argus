package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.application.AuthService;
import io.github.rafaeljc.argus.auth.application.LoginResult;
import io.github.rafaeljc.argus.auth.application.SignUpResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private static final URI ACCOUNT_ME_LOCATION = URI.create("/api/v1/account/me");
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    AuthController(AuthService authService, SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
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
        LoginResult result = authService.login(body.email(), body.password());

        // Session fixation: a session created before authentication (there shouldn't be one on
        // this endpoint, but a client could send a stale cookie) must not carry over as the
        // authenticated session.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }

        List<GrantedAuthority> authorities = result.admin()
                ? List.of(new SimpleGrantedAuthority(ROLE_ADMIN))
                : List.of();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(result.userId().value()), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession(true);
        return ResponseEntity.ok(new SuccessEnvelope<>(new SessionResponse(
                result.userId().value().toString(), expiresAt(session))));
    }

    @GetMapping("/status")
    ResponseEntity<SuccessEnvelope<SessionResponse>> status(@CurrentUserId UserId userId, HttpSession session) {
        return ResponseEntity.ok(new SuccessEnvelope<>(new SessionResponse(
                userId.value().toString(), expiresAt(session))));
    }

    private static Instant expiresAt(HttpSession session) {
        return Instant.ofEpochMilli(session.getLastAccessedTime()).plusSeconds(session.getMaxInactiveInterval());
    }
}
