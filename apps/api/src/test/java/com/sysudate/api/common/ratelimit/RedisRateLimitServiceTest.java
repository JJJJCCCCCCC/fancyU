package com.sysudate.api.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
class RedisRateLimitServiceTest {
    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private RateLimitService limiter;

    @BeforeAll
    void createLimiter() {
        connectionFactory =
            new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        limiter = new RedisRateLimitService(redis);
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
    void allowsRequestsUntilLimitThenReturnsRetryDelay() {
        RateLimitDecision first =
            limiter.check("login:127.0.0.1", 2, Duration.ofMinutes(1));
        RateLimitDecision second =
            limiter.check("login:127.0.0.1", 2, Duration.ofMinutes(1));
        RateLimitDecision rejected =
            limiter.check("login:127.0.0.1", 2, Duration.ofMinutes(1));

        assertThat(first)
            .isEqualTo(new RateLimitDecision(true, 1, 0));
        assertThat(second)
            .isEqualTo(new RateLimitDecision(true, 0, 0));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void keepsIndependentCountersPerBucket() {
        limiter.check("login:127.0.0.1", 1, Duration.ofMinutes(1));

        assertThat(
            limiter.check("login:127.0.0.1", 1, Duration.ofMinutes(1))
                .allowed())
            .isFalse();
        assertThat(
            limiter.check("login:127.0.0.2", 1, Duration.ofMinutes(1))
                .allowed())
            .isTrue();
    }

    @Test
    void redisKeyDoesNotExposeRawBucket() {
        String bucket = "send-code:student@sysu.edu.cn";

        limiter.check(bucket, 1, Duration.ofMinutes(1));

        assertThat(redis.keys("*"))
            .singleElement()
            .asString()
            .doesNotContain(bucket)
            .doesNotContain("student@sysu.edu.cn");
    }

    @Test
    void resetsCounterAfterRedisWindowExpires() {
        Duration window = Duration.ofMillis(200);
        limiter.check("login:127.0.0.1", 1, window);
        assertThat(limiter.check("login:127.0.0.1", 1, window).allowed())
            .isFalse();

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(
                limiter.check("login:127.0.0.1", 1, window).allowed())
                .isTrue());
    }

    @Test
    void concurrentRequestsShareOneAtomicLimit() throws Exception {
        int requestCount = 24;
        int limit = 7;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<RateLimitDecision>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return limiter.check(
                        "send-code:student",
                        limit,
                        Duration.ofMinutes(1));
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<RateLimitDecision> future : futures) {
                if (future.get(10, TimeUnit.SECONDS).allowed()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(limit);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsInvalidBucket(String bucket) {
        assertThatThrownBy(() ->
            limiter.check(bucket, 1, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveLimit(int limit) {
        assertThatThrownBy(() ->
            limiter.check("login:127.0.0.1", limit, Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidWindows")
    void rejectsInvalidWindow(Duration window) {
        assertThatThrownBy(() ->
            limiter.check("login:127.0.0.1", 1, window))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidWindows() {
        return Stream.of(
            Arguments.of((Duration) null),
            Arguments.of(Duration.ZERO),
            Arguments.of(Duration.ofNanos(1)),
            Arguments.of(Duration.ofSeconds(-1)));
    }
}
