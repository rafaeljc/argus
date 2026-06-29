package io.github.rafaeljc.argus.auth.application.port;

import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification verification);

    Optional<EmailVerification> findByTokenHash(String tokenHash);
}
