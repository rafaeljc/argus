package io.github.rafaeljc.argus.email.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.github.rafaeljc.argus.email.application.PollOutboxOnce;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPollerSchedulerTest {

    @Mock
    private PollOutboxOnce pollOutboxOnce;

    @Test
    void poll_delegatesToPollOutboxOnceWithWorkerId() {
        OutboxPollerScheduler scheduler = new OutboxPollerScheduler(pollOutboxOnce);

        scheduler.poll();

        verify(pollOutboxOnce).pollOnce(anyString());
    }

    @Test
    void poll_swallowsRuntimeExceptionsSoSchedulerThreadKeepsRunning() {
        doThrow(new RuntimeException("boom")).when(pollOutboxOnce).pollOnce(anyString());
        OutboxPollerScheduler scheduler = new OutboxPollerScheduler(pollOutboxOnce);

        assertThatCode(scheduler::poll).doesNotThrowAnyException();
    }
}
