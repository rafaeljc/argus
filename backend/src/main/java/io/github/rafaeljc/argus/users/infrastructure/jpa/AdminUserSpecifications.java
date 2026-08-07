package io.github.rafaeljc.argus.users.infrastructure.jpa;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class AdminUserSpecifications {

    private static final char ESCAPE_CHAR = '\\';

    private AdminUserSpecifications() {
    }

    static Specification<UserJpaEntity> emailContains(String fragment) {
        String pattern = "%" + escapeLikeWildcards(fragment.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(root.get("email"), pattern, ESCAPE_CHAR);
    }

    static Specification<UserJpaEntity> isSuspended(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("suspended"), value);
    }

    static Specification<UserJpaEntity> isDeleted(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("deleted"), value);
    }

    static Specification<UserJpaEntity> isVerified(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("verified"), value);
    }

    private static String escapeLikeWildcards(String fragment) {
        StringBuilder escaped = new StringBuilder(fragment.length());
        for (int i = 0; i < fragment.length(); i++) {
            char c = fragment.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
