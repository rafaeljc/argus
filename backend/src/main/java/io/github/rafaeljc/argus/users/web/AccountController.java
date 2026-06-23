package io.github.rafaeljc.argus.users.web;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
class AccountController {

    private final UserService userService;

    AccountController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    ResponseEntity<SuccessEnvelope<UserAccountResponse>> getMyAccount(@CurrentUserId UserId userId) {
        User user = userService.lookup(userId);
        return ResponseEntity.ok(new SuccessEnvelope<>(UserAccountResponse.from(user)));
    }
}
