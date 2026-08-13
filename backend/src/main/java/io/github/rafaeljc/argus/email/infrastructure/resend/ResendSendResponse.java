package io.github.rafaeljc.argus.email.infrastructure.resend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Only the vendor's message id is read, and only to log it: nothing downstream keys off it, so the
// rest of the response is ignored rather than modelled.
@JsonIgnoreProperties(ignoreUnknown = true)
record ResendSendResponse(String id) {}
