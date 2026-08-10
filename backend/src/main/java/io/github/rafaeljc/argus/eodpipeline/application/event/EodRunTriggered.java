package io.github.rafaeljc.argus.eodpipeline.application.event;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.LocalDate;

// Published only when an admin actor triggered the run (never for the cron trigger), so peer
// modules can react without eodpipeline depending on them. admin listens to write an audit row.
public record EodRunTriggered(RunId runId, LocalDate runDate, UserId actorId) {
}
