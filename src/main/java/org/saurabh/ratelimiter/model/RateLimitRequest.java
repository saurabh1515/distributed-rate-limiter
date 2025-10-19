package org.saurabh.ratelimiter.model;

import lombok.Data;

@Data
public class RateLimitRequest {
    private String key;
    private int limit;
    private int windowSeconds;
    private RateLimitAlgorithmType algorithm = RateLimitAlgorithmType.TOKEN_BUCKET;
}
