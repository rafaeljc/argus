package io.github.rafaeljc.argus.transactions.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcTransactionMutationLockIT {

    @Autowired
    private TransactionMutationLock lock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void acquireForUser_withinTransaction_doesNotThrow() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatNoException()
                .isThrownBy(() -> template.executeWithoutResult(status -> lock.acquireForUser(userId)));
    }

    @Test
    void acquireForUser_calledTwiceForSameUserInSameTransaction_doesNotDeadlock() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatNoException().isThrownBy(() -> template.executeWithoutResult(status -> {
            lock.acquireForUser(userId);
            lock.acquireForUser(userId);
        }));
    }

    @Test
    void acquireForUser_outsideActiveTransaction_throwsIllegalState() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        assertThatThrownBy(() -> lock.acquireForUser(userId)).hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
