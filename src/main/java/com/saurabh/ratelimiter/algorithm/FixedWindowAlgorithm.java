package com.saurabh.ratelimiter.algorithm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.saurabh.ratelimiter.model.RateLimitResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> fixedWindowScript;

    public FixedWindowAlgorithm(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = createLuaScript();
    }

    @Override
    public RateLimitResult checkLimit(String key, int limit, int windowSeconds) {
        long now = Instant.now().getEpochSecond();

        // Calculate current window ID
        // Example: If windowSeconds=60 and now=125, windowId=2
        long windowId = now / windowSeconds;

        // Create unique key for this window
        String windowKey = String.format("%s:window:%d", key, windowId);

        log.debug("Checking fixed window rate limit: key={}, window={}, limit={}",
                key, windowId, limit);

        // Execute Lua script for atomic increment and check
        List<Long> result = redisTemplate.execute(
                fixedWindowScript,
                Collections.singletonList(windowKey),
                String.valueOf(limit),
                String.valueOf(windowSeconds)
        );

        if (result == null || result.size() < 2) {
            log.error("Unexpected result from Redis Lua script");
            // Fail open - allow request if Redis fails
            return createFailOpenResult(limit, windowSeconds);
        }

        boolean allowed = result.get(0) == 1;
        long currentCount = result.get(1);

        // Calculate when window resets
        long resetAt = (windowId + 1) * windowSeconds;
        long retryAfter = allowed ? null : (resetAt - now);

        log.debug("Fixed window result: allowed={}, count={}/{}, resetAt={}",
                allowed, currentCount, limit, resetAt);

        return RateLimitResult.builder()
                .allowed(allowed)
                .remaining(Math.max(0, limit - currentCount))
                .resetAt(Instant.ofEpochSecond(resetAt))
                .retryAfter(retryAfter)
                .build();
    }

    /**
     * Lua script for atomic fixed window operation
     *
     * This script:
     * 1. Increments the counter for current window
     * 2. Sets TTL if this is the first request in window
     * 3. Returns whether request is allowed and current count
     *
     * Using Lua ensures atomicity - no race conditions between
     * increment, TTL set, and limit check
     */
    private RedisScript<List> createLuaScript() {
        String script = """
            local window_key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            
            -- Increment counter
            local current = redis.call('INCR', window_key)
            
            -- Set TTL only on first request (when counter = 1)
            -- This ensures the key expires at end of window
            if current == 1 then
                redis.call('EXPIRE', window_key, ttl)
            end
            
            -- Check if request is allowed
            local allowed = 0
            if current <= limit then
                allowed = 1
            end
            
            -- Return: [allowed (0 or 1), current_count]
            return {allowed, current}
            """;

        return RedisScript.of(script, List.class);
    }

    /**
     * Create a fail-open result when Redis operation fails
     * This ensures high availability - we allow requests if rate limiter fails
     */
    private RateLimitResult createFailOpenResult(int limit, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long windowId = now / windowSeconds;
        long resetAt = (windowId + 1) * windowSeconds;

        log.warn("Rate limiter failed, allowing request (fail-open)");

        return RateLimitResult.builder()
                .allowed(true)
                .remaining(limit)
                .resetAt(Instant.ofEpochSecond(resetAt))
                .retryAfter(null)
                .build();
    }

}
