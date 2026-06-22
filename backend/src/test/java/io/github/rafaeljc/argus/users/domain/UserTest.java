package io.github.rafaeljc.argus.users.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    private static final UserId ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String HASH = "$argon2id$v=19$...";

    private static User newUser(UserId id, String email, String passwordHash, Instant createdAt) {
        return new User(id, email, passwordHash, false, false, false, false, createdAt, createdAt, null);
    }

    // --- Happy path ----------------------------------------------------------------------------

    @Test
    void constructor_validInput_setsAllFields() {
        User user = newUser(ID, EMAIL, HASH, NOW);

        assertThat(user.id()).isEqualTo(ID);
        assertThat(user.email()).isEqualTo(EMAIL);
        assertThat(user.passwordHash()).isEqualTo(HASH);
        assertThat(user.isVerified()).isFalse();
        assertThat(user.isSuspended()).isFalse();
        assertThat(user.isDeleted()).isFalse();
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.createdAt()).isEqualTo(NOW);
        assertThat(user.updatedAt()).isEqualTo(NOW);
        assertThat(user.deletedAt()).isNull();
    }

    // --- Email: normalization (whitespace + case) ----------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "Alice@example.com",
            "ALICE@EXAMPLE.COM",
            "  alice@example.com  ",
            "Alice@Example.COM"
    })
    void constructor_emailWithUppercaseOrPadding_normalizesToTrimmedLowercase(String raw) {
        User user = newUser(ID, raw, HASH, NOW);

        assertThat(user.email()).isEqualTo("alice@example.com");
    }

    // --- Email: invariants ---------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void constructor_emailNullOrBlank_throwsIllegalArgument(String email) {
        assertThatThrownBy(() -> newUser(ID, email, HASH, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void constructor_emailLongerThan254_throwsIllegalArgument() {
        String tooLong = "a".repeat(250) + "@x.io"; // 256 chars
        assertThatThrownBy(() -> newUser(ID, tooLong, HASH, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no-at-sign",
            "no@dot",
            "@nolocal.com",
            "trailing@",
            "white space@example.com",
            "two@@example.com"
    })
    void constructor_emailMalformed_throwsIllegalArgument(String email) {
        assertThatThrownBy(() -> newUser(ID, email, HASH, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    // --- Password hash invariants --------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void constructor_passwordHashNullOrBlank_throwsIllegalArgument(String hash) {
        assertThatThrownBy(() -> newUser(ID, EMAIL, hash, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwordHash");
    }

    // --- ID / timestamp invariants -------------------------------------------------------------

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> newUser(null, EMAIL, HASH, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> newUser(ID, EMAIL, HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Soft-delete consistency invariant -----------------------------------------------------

    @Test
    void constructor_isDeletedTrueButDeletedAtNull_throwsIllegalArgument() {
        assertThatThrownBy(() -> new User(
                ID, EMAIL, HASH, true, false, true, false, NOW, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deletedAt");
    }

    @Test
    void constructor_isDeletedFalseButDeletedAtSet_throwsIllegalArgument() {
        assertThatThrownBy(() -> new User(
                ID, EMAIL, HASH, true, false, false, false, NOW, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deletedAt");
    }

    @Test
    void constructor_isDeletedTrueWithDeletedAt_isValid() {
        User user = new User(
                ID, EMAIL, HASH, true, false, true, false, NOW, NOW, NOW);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.deletedAt()).isEqualTo(NOW);
    }
}
