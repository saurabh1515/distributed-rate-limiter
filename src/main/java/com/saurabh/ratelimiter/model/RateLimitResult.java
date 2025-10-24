package com.saurabh.ratelimiter.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RateLimitResult {
    private boolean allowed;
    private long remaining;
    private Instant resetAt;
    private Long retryAfter;
}