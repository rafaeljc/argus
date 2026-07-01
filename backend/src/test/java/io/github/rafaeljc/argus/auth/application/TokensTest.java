package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void plain_producesBase64UrlWithoutPaddingFromThirtyTwoRandomBytes() {
        // Fixed-seed SecureRandom keeps the test deterministic without exercising the OS entropy pool.
        SecureRandom rng = new SecureRandom(new byte[] {1, 2, 3, 4});

        String token = Tokens.plain(rng);

        // 32 bytes → ceil(32*8/6) = 43 chars, no padding.
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void plain_calledTwice_yieldsDifferentTokens() {
        SecureRandom rng = new SecureRandom();

        assertThat(Tokens.plain(rng)).isNotEqualTo(Tokens.plain(rng));
    }

    @Test
    void sha256Hex_producesLowercase64CharHexDigest() {
        String hash = Tokens.sha256Hex("hello");

        // Known SHA-256("hello").
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256Hex_sameInput_isStable() {
        assertThat(Tokens.sha256Hex("argus")).isEqualTo(Tokens.sha256Hex("argus"));
    }

    @Test
    void strongSecureRandom_returnsUsableInstance() {
        SecureRandom rng = Tokens.strongSecureRandom();

        assertThat(rng).isNotNull();
        // Sanity: it produces different bytes on successive calls.
        byte[] a = new byte[16];
        byte[] b = new byte[16];
        rng.nextBytes(a);
        rng.nextBytes(b);
        assertThat(a).isNotEqualTo(b);
    }
}
