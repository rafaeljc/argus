package io.github.rafaeljc.argus.email.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.resend.ResendEmailGateway;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Boots the real `prod` context to prove a deployment sends real mail without any extra switch.
// The datasource comes from the container via @ServiceConnection; the ARGUS_DB_* values only exist
// so the prod placeholders resolve.
@Import(PostgresContainer.class)
@SpringBootTest(
        properties = {
            "argus.email.from-address=argus@argus.example",
            "argus.email.app-base-url=https://app.argus.example",
            "argus.email.resend.api-key=re_test_key",
            "argus.email.resend.base-url=https://api.resend.example",
            "argus.marketdata.massive.api-key=test-api-key",
            "argus.marketdata.massive.base-url=https://api.massive.example",
            "ARGUS_DB_URL=unused",
            "ARGUS_DB_USER=unused",
            "ARGUS_DB_PASSWORD=unused"
        })
@ActiveProfiles("prod")
class EmailVendorProdWiringIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void emailGateway_underProdProfile_isTheRealVendorAdapter() {
        assertThat(applicationContext.getBeanNamesForType(EmailGateway.class)).hasSize(1);
        assertThat(applicationContext.getBean(EmailGateway.class)).isInstanceOf(ResendEmailGateway.class);
    }
}
