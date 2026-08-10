package io.github.rafaeljc.argus.admin.web;

import io.github.rafaeljc.argus.admin.application.AdminService;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CollectionEnvelope;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/admin/users")
class AdminUserController {

    private final AdminService adminService;

    AdminUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    ResponseEntity<CollectionEnvelope<AdminUserResponse>> search(
            @RequestParam(name = "is_suspended", required = false) Boolean isSuspended,
            @RequestParam(name = "is_deleted", required = false) Boolean isDeleted,
            @RequestParam(name = "is_verified", required = false) Boolean isVerified,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(100_000) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage,
            @Valid @RequestBody(required = false) SearchUsersRequest body) {
        String emailContains = body != null ? body.emailContains() : null;
        AdminUserSearchCriteria criteria =
                new AdminUserSearchCriteria(emailContains, isSuspended, isDeleted, isVerified);

        PageResult<User> result = adminService.searchUsers(criteria, page, perPage);
        int totalPages = result.totalPages();

        CollectionEnvelope.Meta meta =
                new CollectionEnvelope.Meta(result.total(), page, perPage, totalPages);
        CollectionEnvelope.Links links = new CollectionEnvelope.Links(
                pageUri(page, perPage),
                page < totalPages ? pageUri(page + 1, perPage) : null,
                page > 1 ? pageUri(page - 1, perPage) : null,
                pageUri(Math.max(totalPages, 1), perPage));

        return ResponseEntity.ok(new CollectionEnvelope<>(
                result.items().stream().map(AdminUserResponse::from).toList(), meta, links));
    }

    @GetMapping("/{id}")
    ResponseEntity<SuccessEnvelope<AdminUserResponse>> get(@PathVariable UUID id) {
        User user = adminService.getUser(new UserId(id));
        return ResponseEntity.ok(new SuccessEnvelope<>(AdminUserResponse.from(user)));
    }

    @PostMapping("/{id}/suspend")
    ResponseEntity<SuccessEnvelope<AdminUserActionResponse>> suspend(
            @PathVariable UUID id, @Valid @RequestBody(required = false) AdminUserActionRequest body) {
        User user = adminService.suspendUser(new UserId(id), currentAdminId(), reasonOf(body));
        return ResponseEntity.ok(new SuccessEnvelope<>(AdminUserActionResponse.from(user)));
    }

    @PostMapping("/{id}/unsuspend")
    ResponseEntity<SuccessEnvelope<AdminUserActionResponse>> unsuspend(
            @PathVariable UUID id, @Valid @RequestBody(required = false) AdminUserActionRequest body) {
        User user = adminService.unsuspendUser(new UserId(id), currentAdminId(), reasonOf(body));
        return ResponseEntity.ok(new SuccessEnvelope<>(AdminUserActionResponse.from(user)));
    }

    @PostMapping("/{id}/delete")
    ResponseEntity<SuccessEnvelope<AdminUserActionResponse>> delete(
            @PathVariable UUID id, @Valid @RequestBody(required = false) AdminUserActionRequest body) {
        User user = adminService.deleteUser(new UserId(id), currentAdminId(), reasonOf(body));
        return ResponseEntity.ok(new SuccessEnvelope<>(AdminUserActionResponse.from(user)));
    }

    private static String reasonOf(AdminUserActionRequest body) {
        return body == null ? null : body.reason();
    }

    private static UserId currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new UserId(UUID.fromString(auth.getName()));
    }

    private static String pageUri(int page, int perPage) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("per_page", perPage)
                .build()
                .toUriString();
    }
}
