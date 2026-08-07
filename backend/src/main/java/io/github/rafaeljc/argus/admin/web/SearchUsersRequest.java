package io.github.rafaeljc.argus.admin.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record SearchUsersRequest(@JsonProperty("email_contains") @Size(min = 1, max = 254) String emailContains) {
}
