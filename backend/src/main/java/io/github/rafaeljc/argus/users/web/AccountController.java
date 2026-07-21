package io.github.rafaeljc.argus.users.web;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SessionCookies;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
class AccountController {

    private final UserService userService;

    AccountController(UserService userService) {
        this.userService = userService;
    }

    // lookupActive rather than lookup so a soft-deleted user with a stale session cookie can't
    // fetch their own profile via the state-gate-exempt /account/me route.
    @GetMapping("/me")
    ResponseEntity<SuccessEnvelope<UserAccountResponse>> getMyAccount(@CurrentUserId UserId userId) {
        User user = userService.lookupActive(userId);
        return ResponseEntity.ok(new SuccessEnvelope<>(UserAccountResponse.from(user)));
    }

    @DeleteMapping("/me")
    ResponseEntity<Void> deleteMyAccount(@CurrentUserId UserId userId,
                                        @Valid @RequestBody DeleteMyAccountRequest body,
                                        HttpServletResponse response) {
        userService.softDelete(userId, body.currentPassword());
        // Session rows are removed in-transaction by InvalidateSessionsOnUserSoftDeleted; drop
        // the browser cookies here so the response leaves no client-side auth state behind.
        response.addCookie(SessionCookies.clearedSession());
        response.addCookie(SessionCookies.clearedCsrf());
        return ResponseEntity.noContent().build();
    }
}
