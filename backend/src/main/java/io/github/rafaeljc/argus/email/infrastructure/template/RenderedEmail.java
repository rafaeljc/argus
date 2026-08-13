package io.github.rafaeljc.argus.email.infrastructure.template;

// Both bodies are always populated: HTML for the ordinary case, plain text for clients that refuse
// it, and a multipart message carrying both is materially less likely to be filed as spam.
public record RenderedEmail(String to, String subject, String html, String text) {

    public RenderedEmail {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("RenderedEmail to must not be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("RenderedEmail subject must not be blank");
        }
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("RenderedEmail html must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("RenderedEmail text must not be blank");
        }
    }
}
