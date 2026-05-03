package com.saurabh.ratelimiter.config;

import com.saurabh.ratelimiter.algorithm.*;
import com.saurabh.ratelimiter.model.RateLimitAlgorithmType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RateLimiterConfig {

    @Bean
    public Map<RateLimitAlgorithmType, RateLimitAlgorithm> algorithmMap(
            FixedWindowAlgorithm fixedWindow,
            TokenBucketAlgorithm tokenBucket,
            SlidingWindowLogAlgorithm slidingWindowLog,
            SlidingWindowCounterAlgorithm slidingWindowCounter) {

        return Map.of(
                RateLimitAlgorithmType.FIXED_WINDOW, fixedWindow,
                RateLimitAlgorithmType.TOKEN_BUCKET, tokenBucket,
                RateLimitAlgorithmType.SLIDING_WINDOW_LOG, slidingWindowLog,
                RateLimitAlgorithmType.SLIDING_WINDOW_COUNTER, slidingWindowCounter
        );
    }
}
