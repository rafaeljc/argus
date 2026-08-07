package io.github.rafaeljc.argus.users.application;

// Nullable Boolean fields, not boolean: an absent filter and an explicit `false` filter are
// different requests. emailContains is a substring, matched case-insensitively.
public record AdminUserSearchCriteria(
        String emailContains,
        Boolean isSuspended,
        Boolean isDeleted,
        Boolean isVerified) {
}
