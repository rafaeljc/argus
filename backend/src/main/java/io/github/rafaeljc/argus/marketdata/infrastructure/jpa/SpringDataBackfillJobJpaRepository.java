package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBackfillJobJpaRepository extends JpaRepository<BackfillJobJpaEntity, UUID> {

    Optional<BackfillJobJpaEntity> findByTickerAndStatusIn(String ticker, Collection<String> statuses);
}
