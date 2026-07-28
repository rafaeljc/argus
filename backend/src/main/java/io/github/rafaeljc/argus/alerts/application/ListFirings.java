package io.github.rafaeljc.argus.alerts.application;

import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.stereotype.Service;

@Service
public class ListFirings {

    private final AlertFiringRepository repository;

    public ListFirings(AlertFiringRepository repository) {
        this.repository = repository;
    }

    public PageResult<AlertFiring> list(UserId userId, int page, int perPage) {
        return new PageResult<>(
                repository.listByUserOrderedByFiredAtDesc(userId, page, perPage),
                repository.countByUser(userId),
                page,
                perPage);
    }
}
