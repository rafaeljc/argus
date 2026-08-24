package io.github.rafaeljc.argus.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.AdminAssignment;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.EmailNotVerifiedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class EnsureSoleAdminTest {

    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String ENCODED_HASH = "$argon2id$v=19$m=65536,t=3,p=1$encoded";

    private UserService userService;
    private AdminAssignment adminAssignment;
    private ApplicationEventPublisher events;
    private EnsureSoleAdmin useCase;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        adminAssignment = Mockito.mock(AdminAssignment.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        useCase = new EnsureSoleAdmin(userService, adminAssignment, new FixedClock(NOW), events);
    }

    private static UserId newUserId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    private static User user(UserId id, boolean verified, boolean suspended, boolean admin) {
        return new User(id, EMAIL, ENCODED_HASH, verified, suspended, false, admin, NOW, NOW, null);
    }

    @Test
    void execute_eligibleUser_assignsAndPublishesEvent() {
        UserId id = newUserId();
        when(userService.lookupActive(id)).thenReturn(user(id, true, false, false));
        when(adminAssignment.makeSoleAdmin(id, NOW)).thenReturn(1);

        useCase.execute(id);

        verify(adminAssignment).makeSoleAdmin(id, NOW);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue())
                .isInstanceOf(AuthAuditEvent.AdminAssigned.class)
                .extracting("userId", "email")
                .containsExactly(id, EMAIL);
    }

    // A restart with the configuration unchanged is the common case. The update matches no rows,
    // and an audit line there would claim an assignment that did not happen.
    @Test
    void execute_alreadySoleAdmin_publishesNoEvent() {
        UserId id = newUserId();
        when(userService.lookupActive(id)).thenReturn(user(id, true, false, true));
        when(adminAssignment.makeSoleAdmin(id, NOW)).thenReturn(0);

        useCase.execute(id);

        verify(adminAssignment).makeSoleAdmin(id, NOW);
        verifyNoInteractions(events);
    }

    @Test
    void execute_unknownUser_throwsResourceNotFound() {
        UserId id = newUserId();
        when(userService.lookupActive(id)).thenThrow(new ResourceNotFoundException("user not found: " + id.value()));

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(adminAssignment, never()).makeSoleAdmin(any(), any());
        verifyNoInteractions(events);
    }

    @Test
    void execute_suspendedUser_throwsAccountSuspended() {
        UserId id = newUserId();
        when(userService.lookupActive(id)).thenReturn(user(id, true, true, false));

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(AccountSuspendedException.class)
                .extracting("userId", "email")
                .containsExactly(id, EMAIL);

        verify(adminAssignment, never()).makeSoleAdmin(any(), any());
        verifyNoInteractions(events);
    }

    // AccountStateGateFilter rejects unverified accounts on every authenticated request, so an
    // unverified admin could not reach the admin surface even with the flag set.
    @Test
    void execute_unverifiedUser_throwsEmailNotVerified() {
        UserId id = newUserId();
        when(userService.lookupActive(id)).thenReturn(user(id, false, false, false));

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(EmailNotVerifiedException.class)
                .extracting("userId", "email")
                .containsExactly(id, EMAIL);

        verify(adminAssignment, never()).makeSoleAdmin(any(), any());
        verifyNoInteractions(events);
    }
}
