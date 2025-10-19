package org.saurabh.ratelimiter.algorithm;

import org.saurabh.ratelimiter.model.RateLimitResult;

public interface RateLimitAlgorithm {

    RateLimitResult checkLimit(String key, int limit, int windowSeconds);
}
