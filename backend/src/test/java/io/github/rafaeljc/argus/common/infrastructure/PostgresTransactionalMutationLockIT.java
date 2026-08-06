package io.github.rafaeljc.argus.common.infrastructure;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class PostgresTransactionalMutationLockIT {

    private static final String RESOURCE = "test-resource";

    @Autowired
    private TransactionalMutationLock lock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void acquireResourceById_withinTransaction_doesNotThrow() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatNoException().isThrownBy(
                () -> template.executeWithoutResult(status -> lock.acquireResourceById(RESOURCE, userId.value())));
    }

    @Test
    void acquireResourceById_calledTwiceForSameResourceAndIdInSameTransaction_doesNotDeadlock() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatNoException().isThrownBy(() -> template.executeWithoutResult(status -> {
            lock.acquireResourceById(RESOURCE, userId.value());
            lock.acquireResourceById(RESOURCE, userId.value());
        }));
    }

    @Test
    void acquireResourceById_outsideActiveTransaction_throwsIllegalState() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        assertThatThrownBy(() -> lock.acquireResourceById(RESOURCE, userId.value()))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
