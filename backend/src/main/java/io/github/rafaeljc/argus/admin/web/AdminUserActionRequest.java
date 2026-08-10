package io.github.rafaeljc.argus.admin.web;

import jakarta.validation.constraints.Size;

public record AdminUserActionRequest(@Size(max = 1000) String reason) {
}
