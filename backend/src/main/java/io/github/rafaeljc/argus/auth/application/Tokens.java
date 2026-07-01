package io.github.rafaeljc.argus.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Shared token helpers for the auth use cases. Plain tokens (session, CSRF, email verification,
// password reset) leave the boundary once — in a cookie or an email — and never touch the DB.
// Only their SHA-256 hex hash is persisted, so a DB read cannot reveal a live token.
final class Tokens {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private Tokens() {
    }

    static String plain(SecureRandom rng) {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static SecureRandom strongSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("no strong SecureRandom available", e);
        }
    }
}
