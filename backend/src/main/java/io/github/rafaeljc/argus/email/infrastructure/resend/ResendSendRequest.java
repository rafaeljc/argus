package io.github.rafaeljc.argus.email.infrastructure.resend;

import java.util.List;

// The vendor's send payload. `to` is a list because the API only accepts one — Argus always sends
// to exactly one recipient, so the list is always a singleton.
record ResendSendRequest(String from, List<String> to, String subject, String html, String text) {}
