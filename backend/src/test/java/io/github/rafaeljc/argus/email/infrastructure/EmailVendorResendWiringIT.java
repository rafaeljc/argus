package io.github.rafaeljc.argus.email.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.resend.ResendEmailGateway;
import io.github.rafaeljc.argus.support.annotations.NoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// The vendor is selected by property rather than by profile, so that validating against a real
// inbox does not also require the prod datasource, prod logging, and the rest of that profile.
@NoDatabase
@SpringBootTest(
        properties = {
            "argus.email.vendor=resend",
            "argus.email.address=argus@argus.example",
            "argus.app-base-url=https://app.argus.example",
            "argus.email.resend.api-key=re_test_key",
            "argus.email.resend.api-url=https://api.resend.example",
            "argus.email.resend.connect-timeout=5s",
            "argus.email.resend.read-timeout=30s"
        })
class EmailVendorResendWiringIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void emailGateway_withResendSelected_isTheRealVendorAdapter() {
        assertThat(applicationContext.getBeanNamesForType(EmailGateway.class)).hasSize(1);
        assertThat(applicationContext.getBean(EmailGateway.class)).isInstanceOf(ResendEmailGateway.class);
    }
}
