package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEmailVerificationRepository implements EmailVerificationRepository {

    private final SpringDataEmailVerificationJpaRepository jpa;

    JpaEmailVerificationRepository(SpringDataEmailVerificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EmailVerification save(EmailVerification verification) {
        EmailVerificationJpaEntity persisted = jpa.save(EmailVerificationEntityMapper.toEntity(verification));
        return EmailVerificationEntityMapper.toDomain(persisted);
    }

    @Override
    public Optional<EmailVerification> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(EmailVerificationEntityMapper::toDomain);
    }
}
