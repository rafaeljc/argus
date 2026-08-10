package io.github.rafaeljc.argus.admin.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserStateChange;
import io.github.rafaeljc.argus.users.application.port.UserLifecycle;
import io.github.rafaeljc.argus.users.domain.User;
import org.springframework.stereotype.Service;

@Service
public class SuspendUser {

    private final UserLifecycle userLifecycle;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public SuspendUser(UserLifecycle userLifecycle, AuditLogRepository auditLogRepository, Clock clock) {
        this.userLifecycle = userLifecycle;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    public User suspend(UserId targetId, UserId actorId, String reason) {
        UserStateChange change = userLifecycle.suspend(targetId);
        if (change.changed()) {
            auditLogRepository.insert(new AuditLogEntry(
                    new AuditEntryId(UuidCreator.getTimeOrderedEpoch()),
                    actorId,
                    AdminAction.SUSPEND,
                    targetId,
                    new AuditMetadata.UserAction(reason),
                    clock.now()));
        }
        return change.user();
    }
}
