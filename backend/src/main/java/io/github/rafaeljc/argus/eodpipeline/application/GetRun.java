package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import org.springframework.stereotype.Service;

@Service
public class GetRun {

    private final EodPipelineRunRepository repository;

    public GetRun(EodPipelineRunRepository repository) {
        this.repository = repository;
    }

    public EodPipelineRun get(RunId id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));
    }
}
