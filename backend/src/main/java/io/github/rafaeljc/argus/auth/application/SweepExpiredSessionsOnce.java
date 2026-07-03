package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// No class-level @Transactional (unlike stateful facades): each batch's DELETE auto-commits on its
// own connection so a mid-sweep crash never rolls back rows already reclaimed.
@Service
public class SweepExpiredSessionsOnce {

    // Chosen so a single delete statement stays short enough not to hold a table-wide lock for
    // long, while still draining a healthy backlog in a few round-trips.
    static final int BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(SweepExpiredSessionsOnce.class);

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public SweepExpiredSessionsOnce(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public void execute() {
        // Cutoff is fixed at sweep start so sessions expiring mid-loop can't push termination past
        // the current run; they get picked up by the next scheduled sweep.
        Instant cutoff = clock.now();
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = sessionRepository.deleteExpiredBefore(cutoff, BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);

        if (totalDeleted > 0) {
            log.info("session sweep deleted {} expired rows", totalDeleted);
        }
    }
}
