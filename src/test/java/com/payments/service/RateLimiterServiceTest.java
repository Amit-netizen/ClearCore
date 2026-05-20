package com.payments.service;

import com.payments.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock StringRedisTemplate redisTemplate;

    @InjectMocks RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimiterService, "requestsPerMinute", 10);
        ReflectionTestUtils.setField(rateLimiterService, "burstSize", 5);
    }

    @Test
    @DisplayName("Returns allowed=true when tokens available")
    void checkRateLimit_tokensAvailable_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(),
                anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, 0L));  // allowed=1, remaining=4, retry=0

        RateLimiterService.RateLimitResult result =
                rateLimiterService.checkRateLimit("merchant-123");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("Returns allowed=false with retry-after when tokens exhausted")
    void checkRateLimit_noTokens_denied() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(),
                anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 0L, 6000L));  // denied, 0 remaining, retry in 6s

        RateLimiterService.RateLimitResult result =
                rateLimiterService.checkRateLimit("merchant-123");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(6);
    }

    @Test
    @DisplayName("Allows request when Redis is unavailable (fail-open)")
    void checkRateLimit_redisDown_allowsRequest() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(),
                anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RateLimiterService.RateLimitResult result =
                rateLimiterService.checkRateLimit("merchant-123");

        // Fail open: don't block requests when Redis is down
        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("100 concurrent requests — at most burst-size succeed before throttling")
    void concurrent100Requests_throttlesCorrectly() throws InterruptedException {
        int totalRequests = 100;
        int burstSize = 5;
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger tokenCounter = new AtomicInteger(burstSize);

        // Simulate decrementing token bucket atomically
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(),
                anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    int tokens = tokenCounter.decrementAndGet();
                    if (tokens >= 0) {
                        return List.of(1L, (long) tokens, 0L);
                    } else {
                        tokenCounter.set(0);
                        return List.of(0L, 0L, 6000L);
                    }
                });

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimiterService.RateLimitResult result =
                            rateLimiterService.checkRateLimit("merchant-test");
                    if (result.allowed()) allowedCount.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(allowedCount.get())
                .as("Only burst-size requests should be allowed before throttling")
                .isLessThanOrEqualTo(burstSize);
    }
}
