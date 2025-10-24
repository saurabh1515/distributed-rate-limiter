package com.saurabh.ratelimiter.algorithm;

import com.saurabh.ratelimiter.model.RateLimitResult;

public interface RateLimitAlgorithm {

    RateLimitResult checkLimit(String key, int limit, int windowSeconds);
}
