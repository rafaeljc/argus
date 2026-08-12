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
            "argus.marketdata.massive.api-key=test-api-key",
            "argus.marketdata.massive.base-url=https://api.massive.example",
            "ARGUS_DB_URL=unused",
            "ARGUS_DB_USER=unused",
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
