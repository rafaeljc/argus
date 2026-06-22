package io.github.rafaeljc.argus.users.infrastructure;

import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class Argon2IdPasswordEncoder implements PasswordEncoder {

    // Cost parameters per NFR-Sec3 and OWASP 2023 argon2id guidance (64 MiB memory, 3 iterations).
    // Embedded in the PHC string by the encoder, so legacy hashes verify after a parameter bump.
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 64 * 1024;
    private static final int ITERATIONS = 3;

    private final Argon2PasswordEncoder delegate = new Argon2PasswordEncoder(
            SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        return delegate.matches(rawPassword, encodedHash);
    }
}
