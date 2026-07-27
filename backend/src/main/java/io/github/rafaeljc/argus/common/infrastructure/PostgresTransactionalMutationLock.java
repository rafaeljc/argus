package io.github.rafaeljc.argus.common.infrastructure;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
class PostgresTransactionalMutationLock implements TransactionalMutationLock {

    private static final String ACQUIRE_SQL = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";

    private final NamedParameterJdbcTemplate jdbc;

    PostgresTransactionalMutationLock(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void acquireResourceForUser(String resource, UserId userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "acquireResourceForUser must be called within an active transaction: "
                            + "pg_advisory_xact_lock auto-releases at transaction end, "
                            + "so calling it outside one acquires and releases the lock immediately, "
                            + "silently providing no protection");
        }
        String key = resource + ":user:" + userId.value();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("key", key);
        jdbc.query(ACQUIRE_SQL, params, (ResultSetExtractor<Void>) rs -> null);
    }
}
