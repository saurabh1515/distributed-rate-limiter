package org.saurabh.ratelimiter.controller;


import lombok.RequiredArgsConstructor;
import org.saurabh.ratelimiter.model.RateLimitAlgorithmType;
import org.saurabh.ratelimiter.model.RateLimitRequest;
import org.saurabh.ratelimiter.model.RateLimitResponse;
import org.saurabh.ratelimiter.service.RateLimiterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

/**
 * REST Controller for Rate Limiting API
 */
@RestController
@RequestMapping("/api/v1/ratelimit")
@RequiredArgsConstructor
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    /**
     * Check if request is allowed under rate limit
     */
    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(
            @RequestBody RateLimitRequest request) {

        RateLimitResponse response = rateLimiterService.checkLimit(
                request.getKey(),
                request.getLimit(),
                request.getWindowSeconds(),
                request.getAlgorithm()
        );

        if (!response.isAllowed()) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Limit", String.valueOf(request.getLimit()))
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset", response.getResetAt().toString())
                    .header("Retry-After", String.valueOf(response.getRetryAfter()))
                    .body(response);
        }

        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(request.getLimit()))
                .header("X-RateLimit-Remaining", String.valueOf(response.getRemaining()))
                .header("X-RateLimit-Reset", response.getResetAt().toString())
                .body(response);
    }

    /**
     * Get current rate limit status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestParam String key,
            @RequestParam RateLimitAlgorithmType algorithm) {

        Map<String, Object> status = rateLimiterService.getStatus(key, algorithm);
        return ResponseEntity.ok(status);
    }

    /**
     * Reset rate limit for a key
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Void> resetLimit(@RequestParam String key) {
        rateLimiterService.reset(key);
        return ResponseEntity.noContent().build();
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Rate Limiter",
                "timestamp", Instant.now().toString()
        ));
    }
}