package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSymbolJpaRepository extends JpaRepository<SymbolJpaEntity, String> {
}
