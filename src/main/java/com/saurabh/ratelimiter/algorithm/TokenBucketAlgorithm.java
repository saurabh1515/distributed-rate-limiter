package com.saurabh.ratelimiter.algorithm;

import com.saurabh.ratelimiter.model.RateLimitResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Token Bucket Algorithm Implementation
 *
 * How it works:
 * 1. Tokens are added to bucket at fixed rate (refill rate)
 * 2. Each request consumes 1 token
 * 3. If no tokens available, request is rejected
 * 4. Bucket has maximum capacity to handle bursts
 *
 * Redis Keys:
 * - {key}:tokens - current number of tokens
 * - {key}:last_refill - last refill timestamp
 */
@Component
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    public TokenBucketAlgorithm(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = loadLuaScript();
    }

    @Override
    public RateLimitResult checkLimit(String key, int limit, int windowSeconds) {
        long now = Instant.now().getEpochSecond();

        // Calculate refill rate (tokens per second)
        double refillRate = (double) limit / windowSeconds;

        // Execute Lua script for atomic operation
        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                Arrays.asList(key + ":tokens", key + ":last_refill"),
                String.valueOf(limit),        // max capacity
                String.valueOf(refillRate),   // refill rate
                String.valueOf(now),          // current timestamp
                String.valueOf(windowSeconds) // TTL
        );

        boolean allowed = result.get(0) == 1;
        long remaining = result.get(1);
        long resetAt = result.get(2);

        return RateLimitResult.builder()
                .allowed(allowed)
                .remaining(remaining)
                .resetAt(Instant.ofEpochSecond(resetAt))
                .retryAfter(allowed ? null : calculateRetryAfter(refillRate))
                .build();
    }

    private long calculateRetryAfter(double refillRate) {
        // Time to refill 1 token
        return (long) Math.ceil(1.0 / refillRate);
    }

    /**
     * Lua script for atomic token bucket operation
     * This ensures thread-safety and consistency in distributed environment
     */
    private RedisScript<List> loadLuaScript() {
        String script = """
            local tokens_key = KEYS[1]
            local timestamp_key = KEYS[2]
            
            local max_tokens = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            
            -- Get current tokens and last refill time
            local tokens = tonumber(redis.call('GET', tokens_key))
            local last_refill = tonumber(redis.call('GET', timestamp_key))
            
            -- Initialize if first request
            if tokens == nil then
                tokens = max_tokens
                last_refill = now
            end
            
            -- Calculate tokens to add based on time elapsed
            local time_elapsed = now - last_refill
            local tokens_to_add = time_elapsed * refill_rate
            tokens = math.min(max_tokens, tokens + tokens_to_add)
            
            -- Update last refill time
            last_refill = now
            
            local allowed = 0
            local remaining = tokens
            
            -- Check if request can be allowed
            if tokens >= 1 then
                tokens = tokens - 1
                remaining = tokens
                allowed = 1
            end
            
            -- Update Redis
            redis.call('SET', tokens_key, tokens, 'EX', ttl)
            redis.call('SET', timestamp_key, last_refill, 'EX', ttl)
            
            -- Calculate reset time (when bucket will be full)
            local tokens_needed = max_tokens - tokens
            local reset_at = now + math.ceil(tokens_needed / refill_rate)
            
            return {allowed, math.floor(remaining), reset_at}
            """;

        return RedisScript.of(script, List.class);
    }
}