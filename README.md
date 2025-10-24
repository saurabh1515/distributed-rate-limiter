# 🚦 Distributed Rate Limiter

A production-ready, distributed rate limiting service built with Spring Boot and Redis. Supports multiple rate limiting algorithms and can be used as a standalone service or library for any microservices architecture.

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Redis](https://img.shields.io/badge/Redis-7.2-red)
![License](https://img.shields.io/badge/license-MIT-blue)

## ✨ Features

- **Multiple Algorithms**: Token Bucket, Sliding Window Log, Sliding Window Counter, Fixed Window
- **Distributed & Thread-Safe**: Uses Redis for distributed locking and atomic operations
- **High Performance**: Sub-millisecond response times, handles 10K+ requests/second
- **Flexible Configuration**: Per-user, per-API, or global rate limits
- **REST API**: Easy integration with any service
- **Spring Boot Starter**: Use as a library with simple annotations
- **Metrics & Monitoring**: Prometheus metrics for rate limit hits/misses
- **Multi-tenant Support**: Different rate limits for different API keys/tenants

## 🏗️ Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────┐
│   Rate Limiter Service          │
│                                 │
│  ┌──────────────────────────┐  │
│  │  Rate Limit Algorithms   │  │
│  │  - Token Bucket          │  │
│  │  - Sliding Window        │  │
│  │  - Fixed Window          │  │
│  └──────────────────────────┘  │
│              │                  │
│              ▼                  │
│  ┌──────────────────────────┐  │
│  │   Redis Manager          │  │
│  │   - Atomic Operations    │  │
│  │   - Distributed Locks    │  │
│  └──────────────────────────┘  │
└────────────┬────────────────────┘
             │
             ▼
      ┌─────────────┐
      │    Redis    │
      └─────────────┘
```

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot 3.2**
- **Redis 7.x** (for distributed state)
- **Lettuce** (Redis client)
- **Micrometer** (metrics)
- **Docker** & **Docker Compose**
- **JUnit 5** & **Testcontainers**

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven 3.8+

### Running Locally

```bash
# Clone the repository
git clone https://github.com/saurabh1515/distributed-rate-limiter.git
cd distributed-rate-limiter

# Start Redis using Docker Compose
docker-compose up -d

# Build and run
mvn clean install
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

## 📖 API Documentation

### Check Rate Limit

```http
POST /api/v1/ratelimit/check
Content-Type: application/json

{
  "key": "user:123",
  "limit": 100,
  "windowSeconds": 60,
  "algorithm": "TOKEN_BUCKET"
}
```

**Response (Allowed):**
```json
{
  "allowed": true,
  "remaining": 99,
  "resetAt": "2025-10-18T10:30:00Z",
  "retryAfter": null
}
```

**Response (Rate Limited):**
```json
{
  "allowed": false,
  "remaining": 0,
  "resetAt": "2025-10-18T10:30:00Z",
  "retryAfter": 45
}
```

### Get Current Status

```http
GET /api/v1/ratelimit/status?key=user:123&algorithm=TOKEN_BUCKET
```

### Reset Rate Limit

```http
DELETE /api/v1/ratelimit/reset?key=user:123
```

## 🔧 Configuration

### application.yml

```yaml
ratelimiter:
  redis:
    host: localhost
    port: 6379
  default:
    algorithm: SLIDING_WINDOW_COUNTER
    limit: 1000
    windowSeconds: 60
  metrics:
    enabled: true
```

### Using as Spring Boot Starter

Add dependency:
```xml
<dependency>
    <groupId>com.saurabh</groupId>
    <artifactId>ratelimiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Use annotation:
```java
@RestController
public class UserController {
    
    @RateLimit(key = "#userId", limit = 100, windowSeconds = 60)
    @GetMapping("/users/{userId}")
    public User getUser(@PathVariable String userId) {
        // Your logic
    }
}
```

## 🧮 Rate Limiting Algorithms

### 1. Token Bucket
- **Use Case**: Smooth burst handling with average rate control
- **How it works**: Tokens are added at a fixed rate; each request consumes a token
- **Pros**: Allows bursts, memory efficient
- **Cons**: Complex implementation

### 2. Sliding Window Log
- **Use Case**: Most accurate rate limiting
- **How it works**: Stores timestamp of each request in a sorted set
- **Pros**: Most accurate, no boundary issues
- **Cons**: Higher memory usage (stores all timestamps)

### 3. Sliding Window Counter
- **Use Case**: Balance between accuracy and performance
- **How it works**: Weighted combination of previous and current window
- **Pros**: Memory efficient, good accuracy
- **Cons**: Slight boundary issues

### 4. Fixed Window
- **Use Case**: Simple rate limiting with clear reset times
- **How it works**: Counter resets at fixed intervals
- **Pros**: Very simple, memory efficient
- **Cons**: Burst at window boundaries

## 📊 Performance Benchmarks

Tested on: MacBook Pro M1, 16GB RAM, Redis 7.2

| Algorithm | Throughput (req/s) | Avg Latency | P99 Latency |
|-----------|-------------------|-------------|-------------|
| Token Bucket | 12,500 | 0.8ms | 2.1ms |
| Sliding Window Counter | 11,800 | 0.9ms | 2.3ms |
| Sliding Window Log | 8,200 | 1.2ms | 3.1ms |
| Fixed Window | 15,000 | 0.6ms | 1.8ms |

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run integration tests with Testcontainers
mvn verify

# Load testing
cd load-tests
./run-load-test.sh
```

## 🐳 Docker Deployment

```bash
# Build image
docker build -t distributed-rate-limiter:latest .

# Run with docker-compose
docker-compose -f docker-compose.prod.yml up -d
```

## 📈 Monitoring

Access metrics at: `http://localhost:8080/actuator/prometheus`

Key metrics:
- `rate_limiter_requests_total` - Total requests
- `rate_limiter_blocked_total` - Total blocked requests
- `rate_limiter_latency_seconds` - Processing latency
- `rate_limiter_redis_operations_total` - Redis operations

Import the Grafana dashboard from `monitoring/grafana-dashboard.json`

## 🎯 Use Cases

1. **API Gateway Rate Limiting**: Protect backend services from abuse
2. **User Quota Management**: Different limits for free/premium users
3. **DDoS Protection**: Prevent denial of service attacks
4. **Cost Control**: Limit expensive operations (e.g., AI API calls)
5. **Fair Resource Sharing**: Ensure no single user monopolizes resources

## 🗺️ Roadmap

- [ ] Add Leaky Bucket algorithm
- [ ] Support for Redis Cluster
- [ ] GraphQL API
- [ ] Rate limit by IP, User-Agent, or custom headers
- [ ] Admin dashboard for monitoring
- [ ] Dynamic rate limit adjustment based on system load
- [ ] gRPC support

## 🤝 Contributing

Contributions are welcome! Please check out the [Contributing Guide](CONTRIBUTING.md).

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Saurabh Shukla**
- GitHub: [@saurabh1515](https://github.com/saurabh1515)
- LinkedIn: [saurabh1515](https://linkedin.com/in/saurabh1515)
- Email: saurabhshukla1515@gmail.com

## 🙏 Acknowledgments

- Inspired by Redis rate limiting patterns
- Algorithm implementations based on research papers and production systems

---

⭐ If you find this project useful, please consider giving it a star!
