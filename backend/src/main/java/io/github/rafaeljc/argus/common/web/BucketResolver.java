package io.github.rafaeljc.argus.common.web;

import io.github.rafaeljc.argus.common.application.ratelimit.BucketSelection;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BucketResolver {

    private static final String SIGNUP_PATH = "/api/v1/auth/signup";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String PASSWORD_RESET_REQUEST_PATH = "/api/v1/auth/password-reset-requests";

    // Public POST endpoints reachable without a session, all bucketed under the shared
    // pre-login global limit and keyed by remote IP.
    private static final Set<String> UNAUTH_PUBLIC_POSTS = Set.of(
            "/api/v1/auth/verify-email",
            "/api/v1/auth/password-resets");

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

    public BucketSelection resolve(HttpServletRequest request, Optional<String> userId) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // IP-keyed buckets read request.getRemoteAddr(). Behind the upstream LB this resolves to
        // the LB's address, so IP buckets currently degenerate to a single global bucket per
        // endpoint. Trusting X-Forwarded-For is deferred to the deployment-hardening PR.
        if ("POST".equals(method)) {
            if (SIGNUP_PATH.equals(uri)) {
                return new BucketSelection("RL.auth.signup", request.getRemoteAddr());
            }
            if (LOGIN_PATH.equals(uri)) {
                return new BucketSelection("RL.auth.login", request.getRemoteAddr());
            }
            if (PASSWORD_RESET_REQUEST_PATH.equals(uri)) {
                return new BucketSelection("RL.auth.reset", request.getRemoteAddr());
            }
            if (UNAUTH_PUBLIC_POSTS.contains(uri)) {
                return new BucketSelection("RL.unauth.global", request.getRemoteAddr());
            }
        }

        if (userId.isPresent()) {
            String bucketName = READ_METHODS.contains(method) ? "RL.read" : "RL.write";
            return new BucketSelection(bucketName, userId.get());
        }

        return new BucketSelection("RL.unauth.global", request.getRemoteAddr());
    }
}
