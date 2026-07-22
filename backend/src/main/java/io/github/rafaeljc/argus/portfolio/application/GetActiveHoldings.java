package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetActiveHoldings {

    private final HoldingRepository repository;

    public GetActiveHoldings(HoldingRepository repository) {
        this.repository = repository;
    }

    public List<Holding> forUser(UserId userId) {
        return repository.findByUser(userId);
    }
}
