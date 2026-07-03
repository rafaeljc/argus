package io.github.rafaeljc.argus.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-22T13:30:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String ENCODED_HASH = "$argon2id$v=19$m=65536,t=3,p=1$encoded";
    private static final String DUMMY_HASH = "$argon2id$v=19$m=65536,t=3,p=1$dummy";

    private UserRepository repository;
    private PasswordEncoder passwordEncoder;
    private FixedClock clock;
    private ApplicationEventPublisher events;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        // Stub the anti-enumeration seed encode before constructing the service so the pre-
        // computed dummy hash used by verifyPasswordForUnknownUser is deterministic in tests.
        when(passwordEncoder.encode(any())).thenReturn(DUMMY_HASH);
        clock = new FixedClock(NOW);
        events = Mockito.mock(ApplicationEventPublisher.class);
        service = new UserService(repository, passwordEncoder, clock, events);
    }

    private static User existing(UserId id, Instant at) {
        return new User(id, EMAIL, ENCODED_HASH, false, false, false, false, at, at, null);
    }

    // --- lookup --------------------------------------------------------------------------------

    @Test
    void lookup_existingId_returnsUser() {
        UserId id = newUserId();
        User stored = existing(id, NOW);
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        User found = service.lookup(id);

        assertThat(found).isSameAs(stored);
    }

    @Test
    void lookup_missingId_throwsResourceNotFound() {
        UserId id = newUserId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- lookupActiveByEmail -------------------------------------------------------------------

    @Test
    void lookupActiveByEmail_existingActiveUser_returnsUser() {
        UserId id = newUserId();
        User stored = existing(id, NOW);
        when(repository.findActiveByEmail(EMAIL)).thenReturn(Optional.of(stored));

        Optional<User> found = service.lookupActiveByEmail(EMAIL);

        assertThat(found).containsSame(stored);
    }

    @Test
    void lookupActiveByEmail_unknownEmail_returnsEmpty() {
        when(repository.findActiveByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThat(service.lookupActiveByEmail("nobody@example.com")).isEmpty();
    }

    // --- createUnverified ----------------------------------------------------------------------

    @Test
    void createUnverified_validInput_savesEncodedHashWithClockTimestamps() {
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_HASH);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.createUnverified(EMAIL, RAW_PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved).isSameAs(created);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.email()).isEqualTo(EMAIL);
        assertThat(saved.passwordHash()).isEqualTo(ENCODED_HASH);
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.isSuspended()).isFalse();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.isAdmin()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.deletedAt()).isNull();
    }

    @Test
    void createUnverified_neverPersistsRawPassword() {
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_HASH);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createUnverified(EMAIL, RAW_PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().passwordHash()).isNotEqualTo(RAW_PASSWORD);
    }

    // --- markVerified --------------------------------------------------------------------------

    @Test
    void markVerified_existingUnverifiedUser_setsFlagAndBumpsUpdatedAt() {
        UserId id = newUserId();
        User unverified = existing(id, Instant.parse("2026-06-01T00:00:00Z"));
        when(repository.findById(id)).thenReturn(Optional.of(unverified));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        clock = new FixedClock(LATER);
        service = new UserService(repository, passwordEncoder, clock, events);

        User result = service.markVerified(id);

        assertThat(result.isVerified()).isTrue();
        assertThat(result.updatedAt()).isEqualTo(LATER);
        assertThat(result.createdAt()).isEqualTo(unverified.createdAt());
        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void markVerified_alreadyVerifiedUser_returnsUnchangedAndDoesNotSave() {
        UserId id = newUserId();
        Instant originalUpdatedAt = Instant.parse("2026-06-01T00:00:00Z");
        User alreadyVerified = new User(
                id, EMAIL, ENCODED_HASH, true, false, false, false,
                originalUpdatedAt, originalUpdatedAt, null);
        when(repository.findById(id)).thenReturn(Optional.of(alreadyVerified));
        clock = new FixedClock(LATER);
        service = new UserService(repository, passwordEncoder, clock, events);

        User result = service.markVerified(id);

        assertThat(result).isSameAs(alreadyVerified);
        assertThat(result.updatedAt()).isEqualTo(originalUpdatedAt);
        verify(repository, never()).save(any());
    }

    @Test
    void markVerified_missingUser_throwsResourceNotFound() {
        UserId id = newUserId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markVerified(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    // --- softDelete ----------------------------------------------------------------------------

    @Test
    void softDelete_correctPassword_softDeletesUser() {
        UserId id = newUserId();
        User active = existing(id, Instant.parse("2026-06-01T00:00:00Z"));
        when(repository.findActiveById(id)).thenReturn(Optional.of(active));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_HASH)).thenReturn(true);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        clock = new FixedClock(LATER);
        service = new UserService(repository, passwordEncoder, clock, events);

        User result = service.softDelete(id, RAW_PASSWORD);

        assertThat(result.isDeleted()).isTrue();
        assertThat(result.deletedAt()).isEqualTo(LATER);
        assertThat(result.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void softDelete_wrongPassword_throwsInvalidCurrentPasswordAndDoesNotSave() {
        UserId id = newUserId();
        User active = existing(id, Instant.parse("2026-06-01T00:00:00Z"));
        when(repository.findActiveById(id)).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("wrong", ENCODED_HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.softDelete(id, "wrong"))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void softDelete_alreadyDeletedUser_throwsResourceNotFoundWithoutCheckingPassword() {
        UserId id = newUserId();
        when(repository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(id, RAW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(repository, never()).save(any());
    }

    // --- verifyPassword ------------------------------------------------------------------------

    @Test
    void verifyPassword_correctPassword_returnsTrue() {
        UserId id = newUserId();
        User user = existing(id, NOW);
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_HASH)).thenReturn(true);

        boolean result = service.verifyPassword(id, RAW_PASSWORD);

        assertThat(result).isTrue();
        verify(passwordEncoder).matches(eq(RAW_PASSWORD), eq(ENCODED_HASH));
    }

    @Test
    void verifyPassword_wrongPassword_returnsFalse() {
        UserId id = newUserId();
        User user = existing(id, NOW);
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", ENCODED_HASH)).thenReturn(false);

        boolean result = service.verifyPassword(id, "wrong");

        assertThat(result).isFalse();
    }

    @Test
    void verifyPassword_missingUser_throwsResourceNotFound() {
        UserId id = newUserId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyPassword(id, RAW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updatePassword ------------------------------------------------------------------------

    @Test
    void updatePassword_activeUser_replacesHashAndBumpsUpdatedAt() {
        UserId id = newUserId();
        User active = existing(id, Instant.parse("2026-06-01T00:00:00Z"));
        String newHash = "$argon2id$v=19$m=65536,t=3,p=1$new";
        when(repository.findActiveById(id)).thenReturn(Optional.of(active));
        when(passwordEncoder.encode("new-secret")).thenReturn(newHash);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        clock = new FixedClock(LATER);
        service = new UserService(repository, passwordEncoder, clock, events);

        User result = service.updatePassword(id, "new-secret");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved).isSameAs(result);
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.email()).isEqualTo(active.email());
        assertThat(saved.passwordHash()).isEqualTo(newHash);
        assertThat(saved.isVerified()).isEqualTo(active.isVerified());
        assertThat(saved.isSuspended()).isFalse();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.isAdmin()).isEqualTo(active.isAdmin());
        assertThat(saved.createdAt()).isEqualTo(active.createdAt());
        assertThat(saved.updatedAt()).isEqualTo(LATER);
        assertThat(saved.deletedAt()).isNull();
    }

    @Test
    void updatePassword_suspendedUser_throwsAccountSuspendedAndDoesNotSave() {
        UserId id = newUserId();
        User suspended = new User(
                id, EMAIL, ENCODED_HASH, true, true, false, false,
                NOW, NOW, null);
        when(repository.findActiveById(id)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.updatePassword(id, "new-secret"))
                .isInstanceOf(AccountSuspendedException.class);
        verify(passwordEncoder, never()).encode("new-secret");
        verify(repository, never()).save(any());
    }

    @Test
    void updatePassword_missingOrDeletedUser_throwsResourceNotFoundAndDoesNotSave() {
        UserId id = newUserId();
        when(repository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePassword(id, "new-secret"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(passwordEncoder, never()).encode("new-secret");
        verify(repository, never()).save(any());
    }

    // --- verifyPasswordForUnknownUser ----------------------------------------------------------

    @Test
    void verifyPasswordForUnknownUser_matchesAgainstPreComputedDummyHash() {
        service.verifyPasswordForUnknownUser(RAW_PASSWORD);

        // The dummy hash is computed once in the constructor and reused per invocation, so the
        // wall-clock cost of the "unknown email" branch matches the "wrong password" branch.
        verify(passwordEncoder).matches(RAW_PASSWORD, DUMMY_HASH);
        verify(repository, never()).findById(any());
    }

    private static UserId newUserId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }
}
