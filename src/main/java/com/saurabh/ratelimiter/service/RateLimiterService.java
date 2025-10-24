package com.saurabh.ratelimiter.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.saurabh.ratelimiter.algorithm.RateLimitAlgorithm;
import com.saurabh.ratelimiter.model.RateLimitAlgorithmType;
import com.saurabh.ratelimiter.model.RateLimitResponse;
import com.saurabh.ratelimiter.model.RateLimitResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final Map<RateLimitAlgorithmType, RateLimitAlgorithm> algorithms;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    public RateLimitResponse checkLimit(
            String key,
            int limit,
            int windowSeconds,
            RateLimitAlgorithmType algorithmType) {

        long startTime = System.currentTimeMillis();

        try {
            // Get the appropriate algorithm
            RateLimitAlgorithm algorithm = algorithms.get(algorithmType);
            if (algorithm == null) {
                throw new IllegalArgumentException("Unknown algorithm: " + algorithmType);
            }

            // Check rate limit
            RateLimitResult result = algorithm.checkLimit(key, limit, windowSeconds);

            // Record metrics
            recordMetrics(algorithmType, result.isAllowed(), startTime);

            // Log if rate limited
            if (!result.isAllowed()) {
                log.warn("Rate limit exceeded for key: {}, algorithm: {}", key, algorithmType);
            }

            return RateLimitResponse.builder()
                    .allowed(result.isAllowed())
                    .remaining(result.getRemaining())
                    .resetAt(result.getResetAt())
                    .retryAfter(result.getRetryAfter())
                    .build();

        } catch (Exception e) {
            log.error("Error checking rate limit for key: {}", key, e);
            // Fail open - allow request if rate limiter fails
            return RateLimitResponse.builder()
                    .allowed(true)
                    .remaining(limit)
                    .resetAt(Instant.now().plusSeconds(windowSeconds))
                    .build();
        }
    }

    public Map<String, Object> getStatus(String key, RateLimitAlgorithmType algorithm) {
        // Get current state from Redis
        String tokensKey = key + ":tokens";
        String timestampKey = key + ":last_refill";

        String tokens = redisTemplate.opsForValue().get(tokensKey);
        String lastRefill = redisTemplate.opsForValue().get(timestampKey);

        return Map.of(
                "key", key,
                "algorithm", algorithm.name(),
                "remainingTokens", tokens != null ? tokens : "N/A",
                "lastRefill", lastRefill != null ?
                        Instant.ofEpochSecond(Long.parseLong(lastRefill)).toString() : "N/A",
                "timestamp", Instant.now().toString()
        );
    }

    public void reset(String key) {
        // Delete all keys associated with this rate limit key
        redisTemplate.delete(key + ":tokens");
        redisTemplate.delete(key + ":last_refill");
        redisTemplate.delete(key + ":window");
        redisTemplate.delete(key + ":log");

        log.info("Reset rate limit for key: {}", key);
    }

    private void recordMetrics(RateLimitAlgorithmType algorithm, boolean allowed, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        // Record total requests
        meterRegistry.counter("rate_limiter_requests_total",
                "algorithm", algorithm.name(),
                "allowed", String.valueOf(allowed)
        ).increment();

        // Record blocked requests
        if (!allowed) {
            meterRegistry.counter("rate_limiter_blocked_total",
                    "algorithm", algorithm.name()
            ).increment();
        }

        // Record latency
        meterRegistry.timer("rate_limiter_latency_seconds",
                "algorithm", algorithm.name()
        ).record(duration, TimeUnit.MILLISECONDS);
    }
}