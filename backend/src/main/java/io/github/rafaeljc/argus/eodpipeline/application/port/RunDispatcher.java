package io.github.rafaeljc.argus.eodpipeline.application.port;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;

public interface RunDispatcher {

    void dispatch(RunId id);

    void dispatchFrom(RunId id, PipelineStep entryStep);
}
