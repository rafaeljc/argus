package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.application.SignUp;
import io.github.rafaeljc.argus.auth.application.SignUpResult;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private static final URI ACCOUNT_ME_LOCATION = URI.create("/api/v1/account/me");

    private final SignUp signUp;

    AuthController(SignUp signUp) {
        this.signUp = signUp;
    }

    @PostMapping("/signup")
    ResponseEntity<SuccessEnvelope<SignUpResponse>> signup(@Valid @RequestBody SignUpRequest body) {
        SignUpResult result = signUp.execute(body.email(), body.password());
        SignUpResponse response = new SignUpResponse(
                result.userId().value().toString(), result.verificationSent());
        return ResponseEntity.created(ACCOUNT_ME_LOCATION).body(new SuccessEnvelope<>(response));
    }
}
