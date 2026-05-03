package com.saurabh.ratelimiter.algorithm;

import com.saurabh.ratelimiter.model.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlidingWindowLogAlgorithm implements RateLimitAlgorithm {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public RateLimitResult checkLimit(String key, int limit, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;

        String logKey = key + ":sliding_log";

        // Remove expired entries outside the window
        redisTemplate.opsForZSet().removeRangeByScore(logKey, 0, windowStart);

        // Count requests in current window
        Long currentCount = redisTemplate.opsForZSet().count(logKey, windowStart, now);
        currentCount = currentCount == null ? 0 : currentCount;

        log.debug("Sliding window log: key={}, window_start={}, now={}, count={}",
                key, windowStart, now, currentCount);

        boolean allowed = currentCount < limit;

        if (allowed) {
            // Add current request to the log with timestamp as score
            redisTemplate.opsForZSet().add(logKey, String.valueOf(now), now);
            // Set expiry to window size to auto-cleanup old entries
            redisTemplate.expire(logKey, windowSeconds, java.util.concurrent.TimeUnit.SECONDS);
        }

        long remaining = Math.max(0, limit - currentCount - (allowed ? 1 : 0));
        long nextRequestAt = allowed ? 0 : (windowStart + windowSeconds);

        return RateLimitResult.builder()
                .allowed(allowed)
                .remaining(remaining)
                .resetAt(Instant.ofEpochSecond(nextRequestAt))
                .retryAfter(allowed ? null : (nextRequestAt - now))
                .build();
    }
}
