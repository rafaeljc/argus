package io.github.rafaeljc.argus.users.infrastructure.bootstrap;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.EnsureSoleAdmin;
import io.github.rafaeljc.argus.users.infrastructure.config.AdminAssignmentProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public class SoleAdminInitializer {

    // The deployed environment resolves every configured value at task start and fails the task if
    // one is missing, while the parameter store rejects an empty value. "No admin configured" is
    // therefore a sentinel rather than the absence of a value.
    private static final String DISABLED = "none";

    private static final Logger log = LoggerFactory.getLogger(SoleAdminInitializer.class);

    private final AdminAssignmentProperties properties;
    private final EnsureSoleAdmin ensureSoleAdmin;

    public SoleAdminInitializer(AdminAssignmentProperties properties, EnsureSoleAdmin ensureSoleAdmin) {
        this.properties = properties;
        this.ensureSoleAdmin = ensureSoleAdmin;
    }

    // Runs on ApplicationReadyEvent rather than at construction so migrations have been applied
    // and the transaction manager is live. Swallows so a failure never stops the application from
    // coming up: the assignment already in the database stands, and the next restart retries.
    @EventListener(ApplicationReadyEvent.class)
    public void assignConfiguredAdmin() {
        String configured = properties.userId();
        if (configured == null || configured.isBlank() || DISABLED.equals(configured.trim())) {
            log.debug("no admin user configured, leaving the current assignment untouched");
            return;
        }

        UserId adminId;
        try {
            adminId = new UserId(UUID.fromString(configured.trim()));
        } catch (IllegalArgumentException e) {
            log.error("configured admin user id is not a uuid: {}", configured);
            return;
        }

        try {
            ensureSoleAdmin.execute(adminId);
        } catch (RuntimeException e) {
            log.error("failed to assign {} as the sole admin", adminId.value(), e);
        }
    }
}
