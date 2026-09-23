package io.github.rafaeljc.argus.users.application;

import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.event.AdminAssignmentChanged;
import io.github.rafaeljc.argus.users.application.port.AdminAssignment;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.EmailNotVerifiedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnsureSoleAdmin {

    private final UserService userService;
    private final AdminAssignment adminAssignment;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public EnsureSoleAdmin(UserService userService,
                           AdminAssignment adminAssignment,
                           Clock clock,
                           ApplicationEventPublisher events) {
        this.userService = userService;
        this.adminAssignment = adminAssignment;
        this.clock = clock;
        this.events = events;
    }

    // The target must be reachable through the same gates as any other authenticated request:
    // AccountStateGateFilter rejects suspended and unverified accounts, so granting either the
    // flag would produce an administrator who cannot use the admin surface.
    @Transactional
    public void execute(UserId adminId) {
        User target = userService.lookupActive(adminId);
        if (target.isSuspended()) {
            throw new AccountSuspendedException(target.id(), target.email());
        }
        if (!target.isVerified()) {
            throw new EmailNotVerifiedException(target.id(), target.email());
        }

        List<UserId> changed = adminAssignment.makeSoleAdmin(adminId, clock.now());
        if (!changed.isEmpty()) {
            events.publishEvent(new AuthAuditEvent.AdminAssigned(target.id(), target.email()));
            events.publishEvent(new AdminAssignmentChanged(changed));
        }
    }
}
