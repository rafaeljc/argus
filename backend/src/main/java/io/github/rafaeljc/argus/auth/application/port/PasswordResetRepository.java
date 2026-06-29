package io.github.rafaeljc.argus.auth.application.port;

import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import java.util.Optional;

public interface PasswordResetRepository {

    PasswordReset save(PasswordReset reset);

    Optional<PasswordReset> findByTokenHash(String tokenHash);
}
