package io.github.rafaeljc.argus.transactions.web;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CollectionEnvelope;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.transactions.application.PageResult;
import io.github.rafaeljc.argus.transactions.application.TransactionService;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    ResponseEntity<CollectionEnvelope<TransactionResponse>> list(
            @CurrentUserId UserId userId,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(100_000) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage) {
        PageResult<Transaction> result = transactionService.list(userId, page, perPage);
        int totalPages = result.totalPages();

        CollectionEnvelope.Meta meta =
                new CollectionEnvelope.Meta(result.total(), page, perPage, totalPages);
        CollectionEnvelope.Links links = new CollectionEnvelope.Links(
                pageUri(page, perPage),
                page < totalPages ? pageUri(page + 1, perPage) : null,
                page > 1 ? pageUri(page - 1, perPage) : null,
                pageUri(Math.max(totalPages, 1), perPage));

        return ResponseEntity.ok(new CollectionEnvelope<>(
                result.items().stream().map(TransactionResponse::from).toList(), meta, links));
    }

    @GetMapping("/{id}")
    ResponseEntity<SuccessEnvelope<TransactionResponse>> get(
            @CurrentUserId UserId userId, @PathVariable UUID id) {
        Transaction transaction = transactionService.get(userId, new TransactionId(id));
        return ResponseEntity.ok(new SuccessEnvelope<>(TransactionResponse.from(transaction)));
    }

    @PatchMapping("/{id}")
    ResponseEntity<SuccessEnvelope<TransactionResponse>> edit(
            @CurrentUserId UserId userId, @PathVariable UUID id, @Valid @RequestBody EditTransactionRequest body) {
        Transaction updated = transactionService.edit(
                userId,
                new TransactionId(id),
                body.operation(),
                body.quantity() != null ? new Quantity(body.quantity()) : null,
                body.tradeDate());
        return ResponseEntity.ok(new SuccessEnvelope<>(TransactionResponse.from(updated)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId UserId userId, @PathVariable UUID id) {
        transactionService.delete(userId, new TransactionId(id));
        return ResponseEntity.noContent().build();
    }

    private static String pageUri(int page, int perPage) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("per_page", perPage)
                .build()
                .toUriString();
    }
}
