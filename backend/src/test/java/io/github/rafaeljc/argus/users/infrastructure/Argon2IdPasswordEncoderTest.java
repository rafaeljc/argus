package io.github.rafaeljc.argus.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import org.junit.jupiter.api.Test;

class Argon2IdPasswordEncoderTest {

    private final PasswordEncoder encoder = new Argon2IdPasswordEncoder();

    @Test
    void encode_producesArgon2idPhcEncoding() {
        String hash = encoder.encode("correct horse battery staple");

        assertThat(hash).startsWith("$argon2id$");
    }

    @Test
    void encode_sameInputTwice_producesDifferentHashes() {
        String first = encoder.encode("same-password");
        String second = encoder.encode("same-password");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void matches_correctPassword_returnsTrue() {
        String hash = encoder.encode("correct horse battery staple");

        assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
    }

    @Test
    void matches_wrongPassword_returnsFalse() {
        String hash = encoder.encode("correct horse battery staple");

        assertThat(encoder.matches("wrong password", hash)).isFalse();
    }
}
