package org.saurabh.ratelimiter.algorithm;

import org.saurabh.ratelimiter.model.RateLimitResult;
import org.springframework.stereotype.Component;

@Component
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    @Override
    public RateLimitResult checkLimit(String key, int limit, int windowSeconds) {
        return null;
    }
}
