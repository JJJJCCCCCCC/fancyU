package com.sysudate.api.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisVerificationCodeStoreTest {
    private static final String HMAC_SECRET =
        "test-only-hmac-secret-with-at-least-32-bytes";

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private VerificationCodeStore store;

    @BeforeAll
    void createStore() {
        connectionFactory =
            new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();

        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        store = new RedisVerificationCodeStore(redis, HMAC_SECRET);
    }

    @AfterEach
    void clearRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @AfterAll
    void closeConnections() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void consumesCorrectCodeOnlyOnce() {
        store.save(
            "student@sysu.edu.cn",
            "123456",
            Duration.ofMinutes(5));

        assertThat(store.consume("student@sysu.edu.cn", "123456")).isTrue();
        assertThat(store.consume("student@sysu.edu.cn", "123456")).isFalse();
    }

    @Test
    void wrongCodeDoesNotDestroyCorrectCode() {
        store.save(
            "student@sysu.edu.cn",
            "123456",
            Duration.ofMinutes(5));

        assertThat(store.consume("student@sysu.edu.cn", "000000")).isFalse();
        assertThat(store.consume("student@sysu.edu.cn", "123456")).isTrue();
    }

    @Test
    void newerCodeOverwritesOlderCode() {
        store.save(
            "student@sysu.edu.cn",
            "123456",
            Duration.ofMinutes(5));
        store.save(
            "student@sysu.edu.cn",
            "654321",
            Duration.ofMinutes(5));

        assertThat(store.consume("student@sysu.edu.cn", "123456")).isFalse();
        assertThat(store.consume("student@sysu.edu.cn", "654321")).isTrue();
    }

    @Test
    void normalizesEmailForSaveAndConsume() {
        store.save(
            "  Student@SYSU.EDU.CN  ",
            "123456",
            Duration.ofMinutes(5));

        assertThat(store.consume("student@sysu.edu.cn", "123456")).isTrue();
    }

    @Test
    void expiresCodeUsingRedisTtl() {
        store.save(
            "student@sysu.edu.cn",
            "123456",
            Duration.ofMillis(200));

        await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> redis.keys("*").isEmpty());

        assertThat(store.consume("student@sysu.edu.cn", "123456")).isFalse();
    }

    @Test
    void redisKeyAndValueDoNotExposeEmailOrPlaintextCode() {
        String email = "Student@SYSU.EDU.CN";
        String code = "123456";

        store.save(email, code, Duration.ofMinutes(5));

        Set<String> keys = redis.keys("*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        String value = redis.opsForValue().get(key);
        String storedRecord = key + "=" + value;

        assertThat(storedRecord)
            .doesNotContain(email)
            .doesNotContain(email.toLowerCase(Locale.ROOT))
            .doesNotContain(code);
    }

    @Test
    void storedDigestIsBoundToNormalizedEmail() {
        String firstEmail = "first@sysu.edu.cn";
        String secondEmail = "second@sysu.edu.cn";
        String code = "123456";

        store.save(firstEmail, code, Duration.ofMinutes(5));
        String firstKey = redis.keys("*").iterator().next();
        String firstDigest = redis.opsForValue().get(firstKey);

        store.save(secondEmail, code, Duration.ofMinutes(5));
        String secondKey = redis.keys("*").stream()
            .filter(key -> !key.equals(firstKey))
            .findFirst()
            .orElseThrow();
        redis.opsForValue().set(
            secondKey,
            firstDigest,
            Duration.ofMinutes(5));

        assertThat(store.consume(secondEmail, code)).isFalse();
        assertThat(redis.hasKey(secondKey)).isTrue();
    }

    @Test
    void onlyOneConcurrentConsumerSucceeds() throws Exception {
        store.save(
            "student@sysu.edu.cn",
            "123456",
            Duration.ofMinutes(5));
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                results.add(executor.submit(
                    () -> store.consume("student@sysu.edu.cn", "123456")));
            }

            long successfulConsumers = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successfulConsumers++;
                }
            }

            assertThat(successfulConsumers).isOne();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void saveRejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> store.save(
            email,
            "123456",
            Duration.ofMinutes(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void saveRejectsInvalidCode(String code) {
        assertThatThrownBy(() -> store.save(
            "student@sysu.edu.cn",
            code,
            Duration.ofMinutes(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidTtls")
    void saveRejectsInvalidTtl(Duration ttl) {
        assertThatThrownBy(() -> store.save(
            "student@sysu.edu.cn",
            "123456",
            ttl))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void consumeRejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> store.consume(email, "123456"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void consumeRejectsInvalidCode(String code) {
        assertThatThrownBy(
            () -> store.consume("student@sysu.edu.cn", code))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidSecrets")
    void constructorRejectsInvalidHmacSecret(String secret) {
        assertThatThrownBy(() -> new RedisVerificationCodeStore(redis, secret))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidTtls() {
        return Stream.of(
            Arguments.of((Duration) null),
            Arguments.of(Duration.ZERO),
            Arguments.of(Duration.ofSeconds(-1)));
    }

    private static Stream<Arguments> invalidSecrets() {
        return Stream.of(
            Arguments.of((String) null),
            Arguments.of(""),
            Arguments.of("   "),
            Arguments.of("too-short"));
    }
}
