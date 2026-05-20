package com.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${payments.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${payments.rate-limit.burst-size:20}")
    private int burstSize;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:merchant:";

    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local window = tonumber(ARGV[4])
            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1]) or capacity
            local last_refill = tonumber(bucket[2]) or now
            local elapsed = now - last_refill
            local refill = math.floor(elapsed * refill_rate / 1000)
            tokens = math.min(capacity, tokens + refill)
            local allowed = 0
            local retry_after = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                retry_after = math.ceil(1000 / refill_rate)
            end
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('PEXPIRE', key, window)
            return {allowed, tokens, retry_after}
            """;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult checkRateLimit(String merchantId) {
        String key = RATE_LIMIT_PREFIX + merchantId;
        long now = System.currentTimeMillis();
        long windowMs = 60_000L;
        double refillRatePerMs = (double) requestsPerMinute / 60_000.0;

        try {
            RedisScript<List> script = RedisScript.of(TOKEN_BUCKET_SCRIPT, List.class);
            @SuppressWarnings("unchecked")
            List<Long> result = redisTemplate.execute(script, List.of(key),
                    String.valueOf(burstSize), String.valueOf(refillRatePerMs),
                    String.valueOf(now), String.valueOf(windowMs));

            if (result == null || result.isEmpty()) return new RateLimitResult(true, requestsPerMinute, 0);

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);
            long retryAfterMs = result.get(2);
            return new RateLimitResult(allowed, (int) remaining, (int) (retryAfterMs / 1000));
        } catch (Exception e) {
            log.error("Rate limit check failed for {}, allowing: {}", merchantId, e.getMessage());
            return new RateLimitResult(true, requestsPerMinute, 0);
        }
    }

    public record RateLimitResult(boolean allowed, int remaining, int retryAfterSeconds) {}
}
