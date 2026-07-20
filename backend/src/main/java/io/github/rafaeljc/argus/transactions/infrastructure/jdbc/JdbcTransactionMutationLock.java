package io.github.rafaeljc.argus.transactions.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
class JdbcTransactionMutationLock implements TransactionMutationLock {

    private static final String ACQUIRE_SQL = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcTransactionMutationLock(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void acquireForUser(UserId userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "acquireForUser must be called within an active transaction: "
                            + "pg_advisory_xact_lock auto-releases at transaction end, "
                            + "so calling it outside one acquires and releases the lock immediately, "
                            + "silently providing no protection");
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("key", "tx:user:" + userId.value());
        jdbc.query(ACQUIRE_SQL, params, (ResultSetExtractor<Void>) rs -> null);
    }
}
