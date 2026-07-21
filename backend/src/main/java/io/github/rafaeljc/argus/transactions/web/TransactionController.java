package io.github.rafaeljc.argus.transactions.web;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.transactions.application.TransactionService;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/transactions")
class TransactionController {

    private final TransactionService transactionService;

    TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    ResponseEntity<SuccessEnvelope<TransactionResponse>> record(
            @CurrentUserId UserId userId, @Valid @RequestBody RecordTransactionRequest body) {
        Transaction saved = transactionService.record(
                userId,
                new Ticker(body.ticker()),
                body.operation(),
                new Quantity(body.quantity()),
                body.tradeDate());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(saved.id().value())
                        .toUri())
                .body(new SuccessEnvelope<>(TransactionResponse.from(saved)));
    }
}
