package io.github.rafaeljc.argus.alerts.web;

import io.github.rafaeljc.argus.alerts.application.AlertService;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CollectionEnvelope;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/alert-firings")
class AlertFiringController {

    private final AlertService alertService;

    AlertFiringController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    ResponseEntity<CollectionEnvelope<AlertFiringResponse>> list(
            @CurrentUserId UserId userId,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(100_000) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage) {
        PageResult<AlertFiring> result = alertService.listFirings(userId, page, perPage);
        int totalPages = result.totalPages();

        CollectionEnvelope.Meta meta =
                new CollectionEnvelope.Meta(result.total(), page, perPage, totalPages);
        CollectionEnvelope.Links links = new CollectionEnvelope.Links(
                pageUri(page, perPage),
                page < totalPages ? pageUri(page + 1, perPage) : null,
                page > 1 ? pageUri(page - 1, perPage) : null,
                pageUri(Math.max(totalPages, 1), perPage));

        return ResponseEntity.ok(new CollectionEnvelope<>(
                result.items().stream().map(AlertFiringResponse::from).toList(), meta, links));
    }

    private static String pageUri(int page, int perPage) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("per_page", perPage)
                .build()
                .toUriString();
    }
}
