package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

// Separate Spring context from AuthControllerIT / CsrfFilterIT because the Domain attribute is
// driven by a property override — argus.web.cookie-domain is otherwise absent (same-origin).
@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "argus.web.cookie-domain=argus.example")
class CsrfCookieDomainIT {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";
    private static final String VALID_PASSWORD = "correct horse battery staple";
    private static final String COOKIE_DOMAIN = "argus.example";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserService userService;

    @Test
    void postLogin_configuredCookieDomain_setsDomainOnCsrfCookieOnlyNotSession() {
        String email = "cookie-domain-login@example.com";
        createVerifiedUser(email);

        ResponseEntity<String> response = postLogin(loginBody(email, VALID_PASSWORD));

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies)
                .filteredOn(c -> c.startsWith("argus_csrf="))
                .allSatisfy(c -> assertThat(c).contains("Domain=" + COOKIE_DOMAIN));
        assertThat(setCookies)
                .filteredOn(c -> c.startsWith("argus_session="))
                .allSatisfy(c -> assertThat(c).doesNotContain("Domain="));
    }

    @Test
    void postLogout_configuredCookieDomain_clearsCsrfCookieWithSameDomain() {
        String email = "cookie-domain-logout@example.com";
        createVerifiedUser(email);
        Session session = login(email);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE,
                "argus_session=" + session.sessionCookie + "; argus_csrf=" + session.csrfCookie);
        headers.add("X-CSRF-Token", session.csrfCookie);
        ResponseEntity<String> response = http.exchange(
                url(LOGOUT_ENDPOINT), HttpMethod.POST, new HttpEntity<>(headers), String.class);

        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies)
                .filteredOn(c -> c.startsWith("argus_csrf=;"))
                .allSatisfy(c -> assertThat(c).contains("Domain=" + COOKIE_DOMAIN));
    }

    private void createVerifiedUser(String email) {
        var created = userService.createUnverified(email, VALID_PASSWORD);
        userService.markVerified(created.id());
    }

    private Session login(String email) {
        ResponseEntity<String> response = postLogin(loginBody(email, VALID_PASSWORD));
        var cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .collect(Collectors.toMap(CsrfCookieDomainIT::cookieName, CsrfCookieDomainIT::cookieValue));
        return new Session(cookies.get("argus_session"), cookies.get("argus_csrf"));
    }

    private static String cookieName(String setCookie) {
        return setCookie.substring(0, setCookie.indexOf('='));
    }

    private static String cookieValue(String setCookie) {
        int eq = setCookie.indexOf('=');
        int semi = setCookie.indexOf(';');
        return semi < 0 ? setCookie.substring(eq + 1) : setCookie.substring(eq + 1, semi);
    }

    private ResponseEntity<String> postLogin(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(LOGIN_ENDPOINT), HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record Session(String sessionCookie, String csrfCookie) {}
}
