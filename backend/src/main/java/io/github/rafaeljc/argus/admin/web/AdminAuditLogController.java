package io.github.rafaeljc.argus.admin.web;

import io.github.rafaeljc.argus.admin.application.AdminService;
import io.github.rafaeljc.argus.admin.application.AuditLogEntryView;
import io.github.rafaeljc.argus.admin.application.AuditLogFilter;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CollectionEnvelope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/admin/audit-log")
class AdminAuditLogController {

    private final AdminService adminService;

    AdminAuditLogController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    ResponseEntity<CollectionEnvelope<AuditLogEntryResponse>> list(
            @RequestParam(name = "actor_id", required = false) UUID actorId,
            @RequestParam(name = "target_user_id", required = false) UUID targetUserId,
            @RequestParam(name = "action", required = false) AdminAction action,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(100_000) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage) {
        AuditLogFilter filter = new AuditLogFilter(
                actorId == null ? null : new UserId(actorId),
                targetUserId == null ? null : new UserId(targetUserId),
                action, from, to);

        PageResult<AuditLogEntryView> result = adminService.listAuditLog(filter, page, perPage);
        int totalPages = result.totalPages();

        CollectionEnvelope.Meta meta =
                new CollectionEnvelope.Meta(result.total(), page, perPage, totalPages);
        CollectionEnvelope.Links links = new CollectionEnvelope.Links(
                pageUri(page, perPage),
                page < totalPages ? pageUri(page + 1, perPage) : null,
                page > 1 ? pageUri(page - 1, perPage) : null,
                pageUri(Math.max(totalPages, 1), perPage));

        return ResponseEntity.ok(new CollectionEnvelope<>(
                result.items().stream().map(AuditLogEntryResponse::from).toList(), meta, links));
    }

    private static String pageUri(int page, int perPage) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("per_page", perPage)
                .build()
                .toUriString();
    }
}
