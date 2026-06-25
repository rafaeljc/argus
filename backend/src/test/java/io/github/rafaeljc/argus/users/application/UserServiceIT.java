package io.github.rafaeljc.argus.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class UserServiceIT {

    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createUnverified_persistsUser_andLookupReturnsIt() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        User loaded = userService.lookup(created.id());

        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.email()).isEqualTo(EMAIL);
        assertThat(loaded.passwordHash()).startsWith("$argon2id$");
        assertThat(loaded.passwordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(loaded.isVerified()).isFalse();
        assertThat(loaded.isSuspended()).isFalse();
        assertThat(loaded.isDeleted()).isFalse();
        assertThat(loaded.isAdmin()).isFalse();
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.deletedAt()).isNull();
    }

    @Test
    void createUnverified_normalizesEmailToLowercase() {
        User created = userService.createUnverified("Alice@Example.COM", RAW_PASSWORD);

        assertThat(userService.lookup(created.id()).email()).isEqualTo("alice@example.com");
    }

    @Test
    void lookup_unknownId_throwsResourceNotFound() {
        UserId unknown = new UserId(java.util.UUID.randomUUID());

        assertThatThrownBy(() -> userService.lookup(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markVerified_persistsFlag() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        userService.markVerified(created.id());

        User reloaded = userService.lookup(created.id());
        assertThat(reloaded.isVerified()).isTrue();
        assertThat(reloaded.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    @Test
    void verifyPassword_correctPassword_returnsTrue() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        assertThat(userService.verifyPassword(created.id(), RAW_PASSWORD)).isTrue();
    }

    @Test
    void verifyPassword_wrongPassword_returnsFalse() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        assertThat(userService.verifyPassword(created.id(), "wrong password")).isFalse();
    }

    @Test
    void softDelete_persistsFlagAndDeletedAt_andRawLookupStillFindsRow() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        userService.softDelete(created.id(), RAW_PASSWORD);

        User reloaded = userService.lookup(created.id());
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.deletedAt()).isNotNull();
        assertThat(reloaded.updatedAt()).isEqualTo(reloaded.deletedAt());
    }

    @Test
    void softDelete_secondCallOnDeletedUser_throwsResourceNotFoundAndPreservesDeletedAt() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);
        userService.softDelete(created.id(), RAW_PASSWORD);
        Instant firstDeletedAt = userService.lookup(created.id()).deletedAt();

        assertThatThrownBy(() -> userService.softDelete(created.id(), RAW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(userService.lookup(created.id()).deletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void findActiveById_existingActiveUser_returnsUser() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        assertThat(userRepository.findActiveById(created.id()))
                .map(User::id)
                .contains(created.id());
    }

    @Test
    void findActiveById_softDeletedUser_returnsEmpty() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);
        userService.softDelete(created.id(), RAW_PASSWORD);

        assertThat(userRepository.findActiveById(created.id())).isEmpty();
    }

    @Test
    void findByEmail_caseInsensitiveLookup_returnsActiveUser() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);

        assertThat(userRepository.findActiveByEmail("ALICE@EXAMPLE.COM"))
                .map(User::id)
                .contains(created.id());
    }

    @Test
    void findByEmail_softDeletedUser_returnsEmpty() {
        User created = userService.createUnverified(EMAIL, RAW_PASSWORD);
        userService.softDelete(created.id(), RAW_PASSWORD);

        assertThat(userRepository.findActiveByEmail(EMAIL)).isEmpty();
    }

    @Test
    void emailUniquenessAllowsReSignupAfterSoftDelete() {
        User first = userService.createUnverified(EMAIL, RAW_PASSWORD);
        userService.softDelete(first.id(), RAW_PASSWORD);

        User second = userService.createUnverified(EMAIL, RAW_PASSWORD);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(userService.lookup(first.id()).isDeleted()).isTrue();
        assertThat(userService.lookup(second.id()).isDeleted()).isFalse();
    }
}
