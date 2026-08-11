package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobClaimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A job commits in_progress before it starts working, so a process that dies mid-rebuild leaves
// that state behind with nothing executing it. A rebuild is a full, idempotent ledger replay, so
// the interrupted work is not a genuine failure — reverting to pending lets the next scheduler
// tick simply retry it, the same way BackfillJobClaimer.revertToPending recovers from a
// circuit-breaker denial mid-claim.
@Service
public class RequeueInterruptedSnapshotRebuildJobs {

    private static final Logger log = LoggerFactory.getLogger(RequeueInterruptedSnapshotRebuildJobs.class);

    private final SnapshotRebuildJobClaimer claimer;

    public RequeueInterruptedSnapshotRebuildJobs(SnapshotRebuildJobClaimer claimer) {
        this.claimer = claimer;
    }

    // Safe to revert every in-progress job outright only because Argus deploys as a single app
    // instance (NFR-C3): nothing else can be executing a rebuild while this starts up.
    @Transactional
    public int execute() {
        int reverted = claimer.revertInterruptedJobsToPending();
        if (reverted > 0) {
            log.warn("reverted {} snapshot rebuild job(s) to pending, left in progress by a previous process",
                    reverted);
        }
        return reverted;
    }
}
