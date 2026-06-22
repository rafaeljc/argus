package io.github.rafaeljc.argus.users.application.port;

public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedHash);
}
