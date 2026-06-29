package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.PasswordResetRepository;
import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import io.github.rafaeljc.argus.common.domain.ResetId;
import io.github.rafaeljc.argus.common.domain.UserId;
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
class JpaPasswordResetRepositoryIT {

    private static final String TOKEN_HASH = "fedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedc";
    private static final Instant CREATED = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(60L * 60);

    @Autowired
    private PasswordResetRepository repository;

    @Autowired
    private UserService userService;

    @Test
    void save_thenFindByTokenHash_returnsPersistedReset() {
        UserId userId = newUser();
        PasswordReset saved = repository.save(newReset(userId, TOKEN_HASH, null));

        Optional<PasswordReset> loaded = repository.findByTokenHash(TOKEN_HASH);

        assertThat(loaded).isPresent();
        PasswordReset pr = loaded.get();
        assertThat(pr.id()).isEqualTo(saved.id());
        assertThat(pr.userId()).isEqualTo(userId);
        assertThat(pr.tokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(pr.createdAt()).isEqualTo(CREATED);
        assertThat(pr.expiresAt()).isEqualTo(EXPIRES);
        assertThat(pr.claimedAt()).isNull();
    }

    @Test
    void save_claimedAtPopulated_isPersisted() {
        UserId userId = newUser();
        Instant claimedAt = CREATED.plusSeconds(120);
        repository.save(newReset(userId, TOKEN_HASH, claimedAt));

        PasswordReset loaded = repository.findByTokenHash(TOKEN_HASH).orElseThrow();
        assertThat(loaded.claimedAt()).isEqualTo(claimedAt);
    }

    @Test
    void findByTokenHash_unknownHash_returnsEmpty() {
        assertThat(repository.findByTokenHash("does-not-exist")).isEmpty();
    }

    @Test
    void save_duplicateTokenHash_throwsDataIntegrityViolation() {
        UserId userId = newUser();
        repository.save(newReset(userId, TOKEN_HASH, null));

        PasswordReset duplicate = new PasswordReset(
                new ResetId(UuidCreator.getTimeOrderedEpoch()),
                userId, TOKEN_HASH, CREATED, EXPIRES, null);

        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static PasswordReset newReset(UserId userId, String tokenHash, Instant claimedAt) {
        return new PasswordReset(
                new ResetId(UuidCreator.getTimeOrderedEpoch()),
                userId, tokenHash, CREATED, EXPIRES, claimedAt);
    }
}
