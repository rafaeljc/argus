package io.github.rafaeljc.argus.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.infrastructure.massive.MassivePriceGateway;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Boots the real `prod` context. The datasource comes from the container via @ServiceConnection;
// the ARGUS_DB_* values only exist so the prod placeholders resolve.
@Import(PostgresContainer.class)
@SpringBootTest(
        properties = {
            "ARGUS_APP_BASE_URL=https://app.argus.example",
            "ARGUS_EMAIL_ADDRESS=argus@argus.example",
            "ARGUS_EMAIL_RESEND_API_KEY=re_test_key",
            "ARGUS_EMAIL_RESEND_API_URL=https://api.resend.example",
            "ARGUS_MARKETDATA_MASSIVE_API_KEY=test-api-key",
            "ARGUS_MARKETDATA_MASSIVE_API_URL=https://api.massive.example",
            "ARGUS_DB_HOST=unused",
            "ARGUS_DB_PORT=unused",
            "ARGUS_DB_NAME=unused",
            "ARGUS_DB_USERNAME=unused",
            "ARGUS_DB_PASSWORD=unused"
        })
@ActiveProfiles("prod")
class MarketdataVendorProdWiringIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void vendorPriceGateway_underProdProfile_isTheRealVendorAdapter() {
        assertThat(applicationContext.getBeanNamesForType(VendorPriceGateway.class)).hasSize(1);
        assertThat(applicationContext.getBean(VendorPriceGateway.class)).isInstanceOf(MassivePriceGateway.class);
    }
}
