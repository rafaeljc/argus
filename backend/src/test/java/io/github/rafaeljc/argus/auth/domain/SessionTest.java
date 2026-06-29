package io.github.rafaeljc.argus.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionTest {

    private static final SessionId ID = new SessionId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final String TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant CREATED = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(30L * 24 * 60 * 60);

    private static Session newSession() {
        return new Session(ID, USER_ID, TOKEN_HASH, "127.0.0.1", "JUnit/5", CREATED, EXPIRES, CREATED);
    }

    @Test
    void constructor_validInput_setsAllFields() {
        Session session = newSession();

        assertThat(session.id()).isEqualTo(ID);
        assertThat(session.userId()).isEqualTo(USER_ID);
        assertThat(session.sessionTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(session.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(session.userAgent()).isEqualTo("JUnit/5");
        assertThat(session.createdAt()).isEqualTo(CREATED);
        assertThat(session.expiresAt()).isEqualTo(EXPIRES);
        assertThat(session.lastActivityAt()).isEqualTo(CREATED);
    }

    @Test
    void constructor_nullableIpAndUserAgent_arePermitted() {
        Session session = new Session(ID, USER_ID, TOKEN_HASH, null, null, CREATED, EXPIRES, CREATED);

        assertThat(session.ipAddress()).isNull();
        assertThat(session.userAgent()).isNull();
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new Session(null, USER_ID, TOKEN_HASH, null, null, CREATED, EXPIRES, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new Session(ID, null, TOKEN_HASH, null, null, CREATED, EXPIRES, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void constructor_blankTokenHash_throwsIllegalArgument(String tokenHash) {
        assertThatThrownBy(() ->
                new Session(ID, USER_ID, tokenHash, null, null, CREATED, EXPIRES, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionTokenHash");
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new Session(ID, USER_ID, TOKEN_HASH, null, null, null, EXPIRES, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void constructor_nullExpiresAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new Session(ID, USER_ID, TOKEN_HASH, null, null, CREATED, null, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void constructor_nullLastActivityAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new Session(ID, USER_ID, TOKEN_HASH, null, null, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastActivityAt");
    }

    @Test
    void constructor_expiresAtEqualToCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new Session(ID, USER_ID, TOKEN_HASH, null, null, CREATED, CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void constructor_expiresAtBeforeCreatedAt_throwsIllegalArgument() {
        Instant earlier = CREATED.minusSeconds(1);
        assertThatThrownBy(() ->
                new Session(ID, USER_ID, TOKEN_HASH, null, null, CREATED, earlier, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }
}
