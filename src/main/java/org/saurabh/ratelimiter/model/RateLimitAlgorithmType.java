package org.saurabh.ratelimiter.model;

public enum RateLimitAlgorithmType {
    TOKEN_BUCKET,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    FIXED_WINDOW
}
