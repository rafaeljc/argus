package io.github.rafaeljc.argus.eodpipeline.application.port;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EodPipelineRunRepository {

    EodPipelineRun insert(EodPipelineRun run);

    EodPipelineRun update(EodPipelineRun run);

    Optional<EodPipelineRun> findById(RunId id);

    Optional<EodPipelineRun> findActiveForDate(LocalDate runDate);

    List<EodPipelineRun> listPaged(int page, int perPage);
}
