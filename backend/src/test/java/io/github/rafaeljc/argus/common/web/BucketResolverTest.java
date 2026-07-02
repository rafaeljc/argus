package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.application.ratelimit.BucketSelection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BucketResolverTest {

    private static final String IP = "203.0.113.7";
    private static final String USER_ID = "user-123";
    private static final Optional<String> UNAUTHENTICATED = Optional.empty();
    private static final Optional<String> AUTHENTICATED = Optional.of(USER_ID);

    private final BucketResolver resolver = new BucketResolver();

    @Test
    void resolve_signupPost_returnsSignupBucketKeyedByIp() {
        BucketSelection selection = resolver.resolve(request("POST", "/api/v1/auth/signup"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.auth.signup");
        assertThat(selection.key()).isEqualTo(IP);
    }

    @Test
    void resolve_loginPost_returnsLoginBucketKeyedByIp() {
        BucketSelection selection = resolver.resolve(request("POST", "/api/v1/auth/login"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.auth.login");
        assertThat(selection.key()).isEqualTo(IP);
    }

    @Test
    void resolve_verifyEmailPost_returnsUnauthGlobalKeyedByIp() {
        BucketSelection selection =
                resolver.resolve(request("POST", "/api/v1/auth/verify-email"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.unauth.global");
        assertThat(selection.key()).isEqualTo(IP);
    }

    @Test
    void resolve_passwordResetRequestsPost_returnsResetBucketKeyedByIp() {
        BucketSelection selection =
                resolver.resolve(request("POST", "/api/v1/auth/password-reset-requests"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.auth.reset");
        assertThat(selection.key()).isEqualTo(IP);
    }

    @Test
    void resolve_passwordResetsPost_returnsUnauthGlobalKeyedByIp() {
        BucketSelection selection =
                resolver.resolve(request("POST", "/api/v1/auth/password-resets"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.unauth.global");
        assertThat(selection.key()).isEqualTo(IP);
    }

    @Test
    void resolve_authenticatedGet_returnsReadBucketKeyedByUserId() {
        BucketSelection selection = resolver.resolve(request("GET", "/api/v1/account/me"), AUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.read");
        assertThat(selection.key()).isEqualTo(USER_ID);
    }

    @Test
    void resolve_authenticatedHead_returnsReadBucketKeyedByUserId() {
        BucketSelection selection = resolver.resolve(request("HEAD", "/api/v1/account/me"), AUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.read");
        assertThat(selection.key()).isEqualTo(USER_ID);
    }

    @Test
    void resolve_authenticatedPost_returnsWriteBucketKeyedByUserId() {
        BucketSelection selection = resolver.resolve(request("POST", "/api/v1/transactions"), AUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.write");
        assertThat(selection.key()).isEqualTo(USER_ID);
    }

    @Test
    void resolve_authenticatedDelete_returnsWriteBucketKeyedByUserId() {
        BucketSelection selection =
                resolver.resolve(request("DELETE", "/api/v1/alert-rules/abc"), AUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.write");
        assertThat(selection.key()).isEqualTo(USER_ID);
    }

    @Test
    void resolve_unauthenticatedGet_returnsUnauthGlobalKeyedByIp() {
        BucketSelection selection = resolver.resolve(request("GET", "/api/v1/auth/status"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.unauth.global");
        assertThat(selection.key()).isEqualTo(IP);
    }

    @Test
    void resolve_unauthenticatedPostToUnknownPath_returnsUnauthGlobalKeyedByIp() {
        BucketSelection selection = resolver.resolve(request("POST", "/api/v1/foo"), UNAUTHENTICATED);

        assertThat(selection.bucketName()).isEqualTo("RL.unauth.global");
        assertThat(selection.key()).isEqualTo(IP);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        request.setRemoteAddr(IP);
        return request;
    }
}
