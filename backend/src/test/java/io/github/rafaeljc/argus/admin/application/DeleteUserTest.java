package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserStateChange;
import io.github.rafaeljc.argus.users.application.port.UserLifecycle;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DeleteUserTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String HASH = "$argon2id$v=19$m=65536,t=3,p=1$encoded";

    private UserLifecycle userLifecycle;
    private AuditLogRepository auditLogRepository;
    private FixedClock clock;
    private DeleteUser useCase;

    @BeforeEach
    void setUp() {
        userLifecycle = Mockito.mock(UserLifecycle.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        clock = new FixedClock(NOW);
        useCase = new DeleteUser(userLifecycle, auditLogRepository, clock);
    }

    private static User user(UserId id, boolean deleted, Instant deletedAt) {
        return new User(id, EMAIL, HASH, true, false, deleted, false, NOW, NOW, deletedAt);
    }

    @Test
    void delete_stateChanged_insertsAuditRowWithReason() {
        UserId targetId = newUserId();
        UserId actorId = newUserId();
        User deleted = user(targetId, true, NOW);
        when(userLifecycle.softDelete(targetId)).thenReturn(new UserStateChange(deleted, true));

        User result = useCase.delete(targetId, actorId, "policy violation");

        assertThat(result).isSameAs(deleted);
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).insert(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.actorId()).isEqualTo(actorId);
        assertThat(entry.action()).isEqualTo(AdminAction.DELETE);
        assertThat(entry.targetUserId()).isEqualTo(targetId);
        assertThat(entry.metadata()).isEqualTo(new AuditMetadata.UserAction("policy violation"));
        assertThat(entry.createdAt()).isEqualTo(NOW);
    }

    @Test
    void delete_noOp_doesNotInsertAuditRow() {
        UserId targetId = newUserId();
        UserId actorId = newUserId();
        Instant originalDeletedAt = Instant.parse("2026-06-10T00:00:00Z");
        User alreadyDeleted = user(targetId, true, originalDeletedAt);
        when(userLifecycle.softDelete(targetId)).thenReturn(new UserStateChange(alreadyDeleted, false));

        User result = useCase.delete(targetId, actorId, "policy violation");

        assertThat(result).isSameAs(alreadyDeleted);
        verify(auditLogRepository, never()).insert(any());
    }

    private static UserId newUserId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }
}
