package io.github.rafaeljc.argus.transactions.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(TransactionId id, UserId userId);

    boolean deleteByIdAndUserId(TransactionId id, UserId userId);

    List<Transaction> listByUserId(UserId userId, int page, int perPage);

    int countByUserId(UserId userId);

    List<Transaction> findLaterSells(UserId userId, Ticker ticker, LocalDate after);

    BigDecimal holdingsAsOf(UserId userId, Ticker ticker, LocalDate asOf);
}
