package io.github.rafaeljc.argus.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.domain.VerificationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailVerificationTest {

    private static final VerificationId ID = new VerificationId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final String TOKEN_HASH = "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";
    private static final Instant CREATED = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(24L * 60 * 60);

    @Test
    void constructor_validInput_setsAllFields() {
        EmailVerification ev = new EmailVerification(ID, USER_ID, TOKEN_HASH, CREATED, EXPIRES, null);

        assertThat(ev.id()).isEqualTo(ID);
        assertThat(ev.userId()).isEqualTo(USER_ID);
        assertThat(ev.tokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(ev.createdAt()).isEqualTo(CREATED);
        assertThat(ev.expiresAt()).isEqualTo(EXPIRES);
        assertThat(ev.verifiedAt()).isNull();
    }

    @Test
    void constructor_verifiedAtPopulated_isAccepted() {
        Instant verified = CREATED.plusSeconds(60);
        EmailVerification ev = new EmailVerification(ID, USER_ID, TOKEN_HASH, CREATED, EXPIRES, verified);

        assertThat(ev.verifiedAt()).isEqualTo(verified);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new EmailVerification(null, USER_ID, TOKEN_HASH, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new EmailVerification(ID, null, TOKEN_HASH, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void constructor_blankTokenHash_throwsIllegalArgument(String tokenHash) {
        assertThatThrownBy(() ->
                new EmailVerification(ID, USER_ID, tokenHash, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenHash");
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new EmailVerification(ID, USER_ID, TOKEN_HASH, null, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void constructor_nullExpiresAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new EmailVerification(ID, USER_ID, TOKEN_HASH, CREATED, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void constructor_expiresAtNotAfterCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new EmailVerification(ID, USER_ID, TOKEN_HASH, CREATED, CREATED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }
}
