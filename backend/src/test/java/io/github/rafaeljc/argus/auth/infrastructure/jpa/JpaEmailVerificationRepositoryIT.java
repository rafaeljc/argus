package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.domain.VerificationId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Import(PostgresContainer.class)
@SpringBootTest
class JpaEmailVerificationRepositoryIT {

    private static final String TOKEN_HASH = "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";
    private static final Instant CREATED = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(24L * 60 * 60);

    @Autowired
    private EmailVerificationRepository repository;

    @Autowired
    private UserService userService;

    @Test
    void save_thenFindByTokenHash_returnsPersistedVerification() {
        UserId userId = newUser();
        EmailVerification saved = repository.save(newVerification(userId, TOKEN_HASH, null));

        Optional<EmailVerification> loaded = repository.findByTokenHash(TOKEN_HASH);

        assertThat(loaded).isPresent();
        EmailVerification ev = loaded.get();
        assertThat(ev.id()).isEqualTo(saved.id());
        assertThat(ev.userId()).isEqualTo(userId);
        assertThat(ev.tokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(ev.createdAt()).isEqualTo(CREATED);
        assertThat(ev.expiresAt()).isEqualTo(EXPIRES);
        assertThat(ev.verifiedAt()).isNull();
    }

    @Test
    void save_verifiedAtPopulated_isPersisted() {
        UserId userId = newUser();
        Instant verifiedAt = CREATED.plusSeconds(60);
        repository.save(newVerification(userId, TOKEN_HASH, verifiedAt));

        EmailVerification loaded = repository.findByTokenHash(TOKEN_HASH).orElseThrow();
        assertThat(loaded.verifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void findByTokenHash_unknownHash_returnsEmpty() {
        assertThat(repository.findByTokenHash("does-not-exist")).isEmpty();
    }

    @Test
    void save_duplicateTokenHash_throwsDataIntegrityViolation() {
        UserId userId = newUser();
        repository.save(newVerification(userId, TOKEN_HASH, null));

        EmailVerification duplicate = new EmailVerification(
                new VerificationId(UuidCreator.getTimeOrderedEpoch()),
                userId, TOKEN_HASH, CREATED, EXPIRES, null);

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static EmailVerification newVerification(UserId userId, String tokenHash, Instant verifiedAt) {
        return new EmailVerification(
                new VerificationId(UuidCreator.getTimeOrderedEpoch()),
                userId, tokenHash, CREATED, EXPIRES, verifiedAt);
    }
}
