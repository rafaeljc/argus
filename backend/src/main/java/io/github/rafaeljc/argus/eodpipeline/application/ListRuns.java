package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import org.springframework.stereotype.Service;

@Service
public class ListRuns {

    private final EodPipelineRunRepository repository;

    public ListRuns(EodPipelineRunRepository repository) {
        this.repository = repository;
    }

    public PageResult<EodPipelineRun> list(int page, int perPage) {
        return new PageResult<>(repository.listPaged(page, perPage), repository.count(), page, perPage);
    }
}
