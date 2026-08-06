package io.github.rafaeljc.argus.common.application;

import java.util.UUID;

public interface TransactionalMutationLock {

    void acquireResourceById(String resource, UUID id);
}
