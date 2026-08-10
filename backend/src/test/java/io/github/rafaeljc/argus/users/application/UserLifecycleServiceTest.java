package io.github.rafaeljc.argus.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.event.UserSoftDeleted;
import io.github.rafaeljc.argus.users.application.event.UserSuspended;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class UserLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-06-01T00:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String HASH = "$argon2id$v=19$m=65536,t=3,p=1$encoded";

    private UserRepository repository;
    private FixedClock clock;
    private ApplicationEventPublisher events;
    private UserLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserRepository.class);
        clock = new FixedClock(NOW);
        events = Mockito.mock(ApplicationEventPublisher.class);
        service = new UserLifecycleService(repository, clock, events);
    }

    private static User user(UserId id, boolean verified, boolean suspended, boolean deleted, Instant deletedAt) {
        return new User(id, EMAIL, HASH, verified, suspended, deleted, false, CREATED, CREATED, deletedAt);
    }

    private static UserId newUserId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // --- suspend ---------------------------------------------------------------------------

    @Test
    void suspend_activeUser_flipsFlagPublishesEventAndReturnsChanged() {
        UserId id = newUserId();
        User active = user(id, true, false, false, null);
        when(repository.findById(id)).thenReturn(Optional.of(active));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserStateChange result = service.suspend(id);

        assertThat(result.changed()).isTrue();
        assertThat(result.user().isSuspended()).isTrue();
        assertThat(result.user().updatedAt()).isEqualTo(NOW);
        ArgumentCaptor<UserSuspended> captor = ArgumentCaptor.forClass(UserSuspended.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(id);
    }

    @Test
    void suspend_alreadySuspendedUser_returnsUnchangedAndDoesNotSaveOrPublish() {
        UserId id = newUserId();
        User suspended = user(id, true, true, false, null);
        when(repository.findById(id)).thenReturn(Optional.of(suspended));

        UserStateChange result = service.suspend(id);

        assertThat(result.changed()).isFalse();
        assertThat(result.user()).isSameAs(suspended);
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void suspend_missingUser_throwsResourceNotFound() {
        UserId id = newUserId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    // --- unsuspend -------------------------------------------------------------------------

    @Test
    void unsuspend_suspendedUser_flipsFlagAndReturnsChangedWithoutPublishing() {
        UserId id = newUserId();
        User suspended = user(id, true, true, false, null);
        when(repository.findById(id)).thenReturn(Optional.of(suspended));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserStateChange result = service.unsuspend(id);

        assertThat(result.changed()).isTrue();
        assertThat(result.user().isSuspended()).isFalse();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unsuspend_alreadyActiveUser_returnsUnchangedAndDoesNotSave() {
        UserId id = newUserId();
        User active = user(id, true, false, false, null);
        when(repository.findById(id)).thenReturn(Optional.of(active));

        UserStateChange result = service.unsuspend(id);

        assertThat(result.changed()).isFalse();
        assertThat(result.user()).isSameAs(active);
        verify(repository, never()).save(any());
    }

    @Test
    void unsuspend_missingUser_throwsResourceNotFound() {
        UserId id = newUserId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsuspend(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- softDelete --------------------------------------------------------------------------

    @Test
    void softDelete_activeUser_flipsFlagStampsDeletedAtAndPublishesEvent() {
        UserId id = newUserId();
        User active = user(id, true, false, false, null);
        when(repository.findById(id)).thenReturn(Optional.of(active));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserStateChange result = service.softDelete(id);

        assertThat(result.changed()).isTrue();
        assertThat(result.user().isDeleted()).isTrue();
        assertThat(result.user().deletedAt()).isEqualTo(NOW);
        ArgumentCaptor<UserSoftDeleted> captor = ArgumentCaptor.forClass(UserSoftDeleted.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(id);
    }

    @Test
    void softDelete_alreadyDeletedUser_returnsUnchangedAndDoesNotRestampDeletedAt() {
        UserId id = newUserId();
        Instant originalDeletedAt = Instant.parse("2026-06-10T00:00:00Z");
        User deleted = user(id, true, false, true, originalDeletedAt);
        when(repository.findById(id)).thenReturn(Optional.of(deleted));

        UserStateChange result = service.softDelete(id);

        assertThat(result.changed()).isFalse();
        assertThat(result.user()).isSameAs(deleted);
        assertThat(result.user().deletedAt()).isEqualTo(originalDeletedAt);
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void softDelete_missingUser_throwsResourceNotFound() {
        UserId id = newUserId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }
}
