package io.github.rafaeljc.argus.portfolio.web;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.portfolio.application.PortfolioService;
import io.github.rafaeljc.argus.portfolio.application.PortfolioView;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
class PortfolioController {

    private final PortfolioService portfolioService;

    PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    ResponseEntity<SuccessEnvelope<PortfolioViewResponse>> get(
            @CurrentUserId UserId userId,
            @RequestParam(name = "as_of", required = false) LocalDate asOf) {
        PortfolioView view = portfolioService.getPortfolio(userId);
        return ResponseEntity.ok(new SuccessEnvelope<>(PortfolioViewResponse.from(view)));
    }
}
