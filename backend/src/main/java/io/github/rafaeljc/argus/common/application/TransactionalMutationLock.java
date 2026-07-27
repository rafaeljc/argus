package io.github.rafaeljc.argus.common.application;

import io.github.rafaeljc.argus.common.domain.UserId;

public interface TransactionalMutationLock {

    void acquireResourceForUser(String resource, UserId userId);
}
