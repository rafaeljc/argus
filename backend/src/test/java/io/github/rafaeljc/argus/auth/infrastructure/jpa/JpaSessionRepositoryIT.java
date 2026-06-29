package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@Import(PostgresContainer.class)
@SpringBootTest
class JpaSessionRepositoryIT {

    private static final String TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant CREATED = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(30L * 24 * 60 * 60);

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserService userService;

    @Test
    void save_thenFindByTokenHash_returnsPersistedSession() {
        UserId userId = newUser();
        Session saved = sessionRepository.save(newSession(userId, TOKEN_HASH, "10.0.0.1", "JUnit/5"));

        Optional<Session> loaded = sessionRepository.findByTokenHash(TOKEN_HASH);

        assertThat(loaded).isPresent();
        Session session = loaded.get();
        assertThat(session.id()).isEqualTo(saved.id());
        assertThat(session.userId()).isEqualTo(userId);
        assertThat(session.sessionTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(session.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(session.userAgent()).isEqualTo("JUnit/5");
        assertThat(session.createdAt()).isEqualTo(CREATED);
        assertThat(session.expiresAt()).isEqualTo(EXPIRES);
        assertThat(session.lastActivityAt()).isEqualTo(CREATED);
    }

    @Test
    void save_nullableIpAndUserAgent_persistAsNull() {
        UserId userId = newUser();
        sessionRepository.save(newSession(userId, TOKEN_HASH, null, null));

        Session loaded = sessionRepository.findByTokenHash(TOKEN_HASH).orElseThrow();
        assertThat(loaded.ipAddress()).isNull();
        assertThat(loaded.userAgent()).isNull();
    }

    @Test
    void findByTokenHash_unknownHash_returnsEmpty() {
        assertThat(sessionRepository.findByTokenHash("does-not-exist")).isEmpty();
    }

    @Test
    void save_duplicateTokenHash_throwsDataIntegrityViolation() {
        UserId userId = newUser();
        sessionRepository.save(newSession(userId, TOKEN_HASH, null, null));

        Session duplicate = new Session(
                new SessionId(UuidCreator.getTimeOrderedEpoch()),
                userId, TOKEN_HASH, null, null, CREATED, EXPIRES, CREATED);

        assertThatThrownBy(() -> sessionRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByUserId_returnsAllSessionsForUser() {
        UserId userId = newUser();
        Session first = sessionRepository.save(newSession(userId, TOKEN_HASH, null, null));
        Session second = sessionRepository.save(newSession(userId, TOKEN_HASH + "-2", null, null));

        List<Session> sessions = sessionRepository.findByUserId(userId);

        assertThat(sessions)
                .extracting(Session::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void findByUserId_unknownUser_returnsEmptyList() {
        UserId unknown = new UserId(UuidCreator.getTimeOrderedEpoch());

        assertThat(sessionRepository.findByUserId(unknown)).isEmpty();
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static Session newSession(UserId userId, String tokenHash, String ip, String ua) {
        return new Session(
                new SessionId(UuidCreator.getTimeOrderedEpoch()),
                userId, tokenHash, ip, ua, CREATED, EXPIRES, CREATED);
    }
}
