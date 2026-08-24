package io.github.rafaeljc.argus.users.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.EnsureSoleAdmin;
import io.github.rafaeljc.argus.users.infrastructure.config.AdminAssignmentProperties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SoleAdminInitializerTest {

    private EnsureSoleAdmin ensureSoleAdmin;

    @BeforeEach
    void setUp() {
        ensureSoleAdmin = Mockito.mock(EnsureSoleAdmin.class);
    }

    private SoleAdminInitializer initializerFor(String configuredUserId) {
        return new SoleAdminInitializer(new AdminAssignmentProperties(configuredUserId), ensureSoleAdmin);
    }

    @Test
    void assignConfiguredAdmin_configuredUserId_delegatesToUseCase() {
        UUID id = UuidCreator.getTimeOrderedEpoch();

        initializerFor(id.toString()).assignConfiguredAdmin();

        verify(ensureSoleAdmin).execute(new UserId(id));
    }

    @Test
    void assignConfiguredAdmin_blankUserId_skips() {
        initializerFor("  ").assignConfiguredAdmin();

        verifyNoInteractions(ensureSoleAdmin);
    }

    @Test
    void assignConfiguredAdmin_nullUserId_skips() {
        initializerFor(null).assignConfiguredAdmin();

        verifyNoInteractions(ensureSoleAdmin);
    }

    // The parameter cannot be absent or empty in the deployed environment, so a sentinel carries
    // "no admin configured" instead.
    @Test
    void assignConfiguredAdmin_disabledSentinel_skips() {
        initializerFor("none").assignConfiguredAdmin();

        verifyNoInteractions(ensureSoleAdmin);
    }

    @Test
    void assignConfiguredAdmin_malformedUserId_skipsWithoutThrowing() {
        SoleAdminInitializer initializer = initializerFor("not-a-uuid");

        assertThatCode(initializer::assignConfiguredAdmin).doesNotThrowAnyException();

        verifyNoInteractions(ensureSoleAdmin);
    }

    // A misconfigured or ineligible account must not stop the application from serving traffic:
    // the assignment already in the database is durable and survives the failed attempt.
    @Test
    void assignConfiguredAdmin_useCaseFails_swallowsSoStartupSucceeds() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        doThrow(new IllegalStateException("boom")).when(ensureSoleAdmin).execute(any());

        assertThatCode(initializerFor(id.toString())::assignConfiguredAdmin).doesNotThrowAnyException();

        verify(ensureSoleAdmin).execute(new UserId(id));
    }
}
