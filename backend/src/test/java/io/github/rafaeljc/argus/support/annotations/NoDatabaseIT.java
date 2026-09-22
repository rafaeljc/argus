package io.github.rafaeljc.argus.support.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@NoDatabase
@SpringBootTest
class NoDatabaseIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void context_doesNotContainAnyDataSourceBean() {
        String[] dataSourceBeans = applicationContext.getBeanNamesForType(DataSource.class);

        assertThat(dataSourceBeans)
                .as("@NoDatabase failed to exclude DataSource autoconfig; FQNs likely stale after Spring Boot upgrade")
                .isEmpty();
    }

    @Test
    void context_doesNotContainAnyRedisConnectionFactoryBean() {
        String[] redisBeans = applicationContext.getBeanNamesForType(RedisConnectionFactory.class);

        assertThat(redisBeans)
                .as("@NoDatabase failed to exclude Redis autoconfig; FQNs likely stale after Spring Boot upgrade")
                .isEmpty();
    }
}
