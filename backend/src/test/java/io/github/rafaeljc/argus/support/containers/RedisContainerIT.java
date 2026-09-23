package io.github.rafaeljc.argus.support.containers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import({PostgresContainer.class, RedisContainer.class})
class RedisContainerIT {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @RepeatedTest(2)
    void eachRun_startsWithRedisFlushed() {
        assertThat(redisTemplate.keys("*")).isEmpty();

        redisTemplate.opsForValue().set("probe-key", "probe-value");

        assertThat(redisTemplate.opsForValue().get("probe-key")).isEqualTo("probe-value");
    }
}
