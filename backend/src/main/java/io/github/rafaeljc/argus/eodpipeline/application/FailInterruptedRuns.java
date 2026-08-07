package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A step commits in_progress before it starts working, so a process that dies mid-step leaves that
// state behind with nothing executing it. Left alone it would reject every later rerun of the run
// and, through the partial unique index on (run_date), every later run for the same date.
@Service
public class FailInterruptedRuns {

    private static final String ERROR_MESSAGE = "interrupted by restart";

    private static final Logger log = LoggerFactory.getLogger(FailInterruptedRuns.class);

    private final EodPipelineRunRepository runs;
    private final Clock clock;

    public FailInterruptedRuns(EodPipelineRunRepository runs, Clock clock) {
        this.runs = runs;
        this.clock = clock;
    }

    // Safe to fail every non-terminal run outright only because Argus deploys as a single app
    // instance (NFR-C3): nothing else can be executing a run while this starts up. Revisit when
    // NFR-S8 trips and a second instance appears — a second instance booting would otherwise fail
    // the first instance's live run.
    @Transactional
    public int execute() {
        int failed = runs.failNonTerminalRuns(clock.now(), ERROR_MESSAGE);
        if (failed > 0) {
            log.warn("failed {} eod pipeline run(s) left in progress by a previous process", failed);
        }
        return failed;
    }
}
