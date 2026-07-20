package io.github.rafaeljc.argus.transactions.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;

public interface TransactionMutationLock {

    void acquireForUser(UserId userId);
}
