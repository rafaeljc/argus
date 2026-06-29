package io.github.rafaeljc.argus.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.ResetId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordResetTest {

    private static final ResetId ID = new ResetId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final String TOKEN_HASH = "fedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedc";
    private static final Instant CREATED = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(60L * 60);

    @Test
    void constructor_validInput_setsAllFields() {
        PasswordReset pr = new PasswordReset(ID, USER_ID, TOKEN_HASH, CREATED, EXPIRES, null);

        assertThat(pr.id()).isEqualTo(ID);
        assertThat(pr.userId()).isEqualTo(USER_ID);
        assertThat(pr.tokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(pr.createdAt()).isEqualTo(CREATED);
        assertThat(pr.expiresAt()).isEqualTo(EXPIRES);
        assertThat(pr.claimedAt()).isNull();
    }

    @Test
    void constructor_claimedAtPopulated_isAccepted() {
        Instant claimed = CREATED.plusSeconds(120);
        PasswordReset pr = new PasswordReset(ID, USER_ID, TOKEN_HASH, CREATED, EXPIRES, claimed);

        assertThat(pr.claimedAt()).isEqualTo(claimed);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new PasswordReset(null, USER_ID, TOKEN_HASH, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new PasswordReset(ID, null, TOKEN_HASH, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void constructor_blankTokenHash_throwsIllegalArgument(String tokenHash) {
        assertThatThrownBy(() ->
                new PasswordReset(ID, USER_ID, tokenHash, CREATED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenHash");
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new PasswordReset(ID, USER_ID, TOKEN_HASH, null, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void constructor_nullExpiresAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new PasswordReset(ID, USER_ID, TOKEN_HASH, CREATED, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void constructor_expiresAtNotAfterCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                new PasswordReset(ID, USER_ID, TOKEN_HASH, CREATED, CREATED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }
}
