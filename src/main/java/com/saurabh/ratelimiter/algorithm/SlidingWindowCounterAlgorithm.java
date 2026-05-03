package com.saurabh.ratelimiter.algorithm;

import com.saurabh.ratelimiter.model.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlidingWindowCounterAlgorithm implements RateLimitAlgorithm {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> slidingWindowCounterScript;

    public SlidingWindowCounterAlgorithm(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowCounterScript = createLuaScript();
    }

    @Override
    public RateLimitResult checkLimit(String key, int limit, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        long currentWindow = now / windowSeconds;
        long previousWindow = currentWindow - 1;

        String currentWindowKey = key + ":counter:window:" + currentWindow;
        String previousWindowKey = key + ":counter:window:" + previousWindow;

        // Execute Lua script for atomic operation
        List<Long> result = redisTemplate.execute(
                slidingWindowCounterScript,
                Arrays.asList(currentWindowKey, previousWindowKey),
                String.valueOf(limit),
                String.valueOf(windowSeconds),
                String.valueOf(now),
                String.valueOf(currentWindow)
        );

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(1);
        long resetAt = (currentWindow + 1) * windowSeconds;

        log.debug("Sliding window counter: key={}, allowed={}, remaining={}, reset_at={}",
                key, allowed, remaining, resetAt);

        return RateLimitResult.builder()
                .allowed(allowed)
                .remaining(remaining)
                .resetAt(Instant.ofEpochSecond(resetAt))
                .retryAfter(allowed ? null : (resetAt - now))
                .build();
    }

    private RedisScript<List> createLuaScript() {
        String script = """
            local current_window_key = KEYS[1]
            local previous_window_key = KEYS[2]

            local limit = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local current_window = tonumber(ARGV[4])

            -- Get counts from current and previous windows
            local current_count = tonumber(redis.call('GET', current_window_key)) or 0
            local previous_count = tonumber(redis.call('GET', previous_window_key)) or 0

            -- Calculate position in current window (0 to 1)
            local window_start = current_window * window_size
            local position_in_window = (now - window_start) / window_size

            -- Weighted count from previous window
            local previous_weight = math.max(0, 1 - position_in_window)
            local weighted_previous = math.floor(previous_count * previous_weight)

            -- Total weighted requests
            local total = current_count + weighted_previous

            local allowed = 0
            local remaining = 0

            -- Check if request is allowed
            if total < limit then
                allowed = 1
                redis.call('INCR', current_window_key)
                redis.call('EXPIRE', current_window_key, window_size)
                remaining = limit - total - 1
            else
                remaining = 0
            end

            return {allowed, remaining}
            """;

        return RedisScript.of(script, List.class);
    }
}
