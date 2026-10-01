package io.github.rafaeljc.argus.support.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @TestConfiguration keeps this out of every other test's context, not just out of production:
// Spring Boot's component-scan exclusion filter checks for @TestConfiguration directly on the
// class, regardless of what other stereotype annotation -- @RestController here -- sits
// alongside it. A nested class wouldn't get that exclusion (only the enclosing class is
// checked), so this stays a single, non-nested class reachable only via an explicit @Import.
@TestConfiguration(proxyBeanMethods = false)
@RestController
public class ClientIpEchoController {

    @GetMapping("/test/client-ip")
    public String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
