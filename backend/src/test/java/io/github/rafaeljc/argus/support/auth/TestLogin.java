package io.github.rafaeljc.argus.support.auth;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// Drives a real signup-verify-login flow over HTTP instead of poking session storage directly.
// One instance per test class, built in a @BeforeEach from that class's own @Autowired
// TestRestTemplate/@LocalServerPort.
public final class TestLogin {

    public static final String PASSWORD = "correct horse battery staple";

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String SESSION_COOKIE = "argus_session";
    private static final String CSRF_COOKIE = "argus_csrf";

    private final UserService userService;
    private final UserRepository userRepository;
    private final TestRestTemplate http;
    private final int port;

    public TestLogin(UserService userService, UserRepository userRepository, TestRestTemplate http, int port) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.http = http;
        this.port = port;
    }

    public TestSession login(String email) {
        return login(email, false);
    }

    // The admin flag must be set before login: Spring Security grants ROLE_ADMIN at
    // authentication time, so a flag flipped after login has no effect on the returned session.
    public TestSession login(String email, boolean admin) {
        User user = userService.createUnverified(email, PASSWORD);
        user = userService.markVerified(user.id());
        if (admin) {
            user = userRepository.save(new User(user.id(), user.email(), user.passwordHash(),
                    user.isVerified(), user.isSuspended(), user.isDeleted(), true,
                    user.createdAt(), user.updatedAt(), user.deletedAt()));
        }
        return authenticate(user.id(), email);
    }

    private TestSession authenticate(UserId userId, String email) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";

        ResponseEntity<String> response = http.exchange(url(LOGIN_PATH), HttpMethod.POST,
                new HttpEntity<>(body, requestHeaders), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "TestLogin failed for " + email + ": " + response.getStatusCode() + " " + response.getBody());
        }

        Map<String, String> cookies = setCookiesByName(response);
        String sessionCookie = cookies.get(SESSION_COOKIE);
        String csrfCookie = cookies.get(CSRF_COOKIE);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SESSION_COOKIE + "=" + sessionCookie + "; " + CSRF_COOKIE + "=" + csrfCookie);
        headers.add("X-CSRF-Token", csrfCookie);
        return new TestSession(userId, headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Map<String, String> setCookiesByName(ResponseEntity<String> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return Map.of();
        }
        return setCookies.stream().collect(Collectors.toMap(
                TestLogin::cookieName, TestLogin::cookieValue, (first, second) -> second));
    }

    private static String cookieName(String setCookie) {
        int eq = setCookie.indexOf('=');
        return setCookie.substring(0, eq);
    }

    private static String cookieValue(String setCookie) {
        int eq = setCookie.indexOf('=');
        int semi = setCookie.indexOf(';');
        return semi < 0 ? setCookie.substring(eq + 1) : setCookie.substring(eq + 1, semi);
    }
}
