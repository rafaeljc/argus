package io.github.rafaeljc.argus.eodpipeline.application.port;

import io.github.rafaeljc.argus.common.domain.RunId;

public interface RunDispatcher {

    void dispatch(RunId id);
}
