# Redis Caching Strategy Guide

## Table of Contents
1. [Caching Concepts](#caching-concepts)
2. [Redis Setup](#redis-setup)
3. [Cache Annotations](#cache-annotations)
4. [Cache Configuration](#cache-configuration)
5. [Cache Invalidation](#cache-invalidation)
6. [Cache Keys](#cache-keys)
7. [TTL Strategy](#ttl-strategy)
8. [Testing Cache](#testing-cache)

---

## Caching Concepts

### Why Cache?

**Problem:** Database queries are slow and expensive
- MongoDB query: ~50-100ms
- Redis cache hit: ~1-2ms (50x faster!)

**Example Without Caching:**
```
Request 1: Get portfolio "p123" → MongoDB (50ms)
Request 2: Get portfolio "p123" → MongoDB (50ms)  
Request 3: Get portfolio "p123" → MongoDB (50ms)
Total: 150ms for same data
```

**Example With Caching:**
```
Request 1: Get portfolio "p123" → MongoDB (50ms) → Store in cache
Request 2: Get portfolio "p123" → Redis cache (1ms)
Request 3: Get portfolio "p123" → Redis cache (1ms)
Total: 52ms (66% faster!)
```

### Cache-Aside Pattern

**How it works:**
1. **Read:** Check cache first, if miss → query database → store in cache
2. **Write:** Update database → evict/update cache

```
┌─────────────┐
│ Application │
└──────┬──────┘
       │
   1. Check cache
       ├──────────┐
       │          ↓
   ┌───▼───┐  ┌─────┐
   │ Redis │  │ DB  │
   └───────┘  └─────┘
       ↑          │
       │          │
   4. Store   2. If miss, query
       └──────────┘
          3. Return data
```

### Benefits

✅ **Performance** - 50-100x faster reads  
✅ **Scalability** - Reduce database load  
✅ **Cost** - Fewer database queries = lower costs  
✅ **Availability** - Cache can serve requests if DB is slow  

### Trade-offs

❌ **Complexity** - Additional infrastructure  
❌ **Consistency** - Cache can be stale  
❌ **Memory** - Cache storage costs  
❌ **Cache Invalidation** - "There are only two hard things in Computer Science: cache invalidation and naming things"

### When to Cache

✅ **Read-heavy data** - Portfolios queried frequently  
✅ **Expensive queries** - Complex calculations  
✅ **Relatively static** - Portfolio data doesn't change constantly  

❌ **Frequently changing** - Real-time stock prices  
❌ **Unique queries** - Each query is different  
❌ **Simple lookups** - Already fast

## Redis Setup

### Redis in Docker

**Location:** `docker-compose.yml`

```yaml
redis:
  image: redis:7-alpine
  container_name: redis-cache
  ports:
    - "6379:6379"
  volumes:
    - redis-data:/data
  networks:
    - pms-network
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
```

**Key Points:**
- **Image:** Redis 7 Alpine (lightweight)
- **Port:** 6379 (default Redis port)
- **Volume:** Persist cache data (survives container restarts)
- **Health Check:** Verify Redis is responsive

### Spring Boot Dependencies

**Location:** `portfolio-service/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### Application Configuration

**Location:** `application.yml`

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
  
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes in milliseconds
```

### Connection Factory

Spring Boot auto-configures:
- `RedisConnectionFactory` - Manages connections to Redis
- `RedisTemplate` - Operations on Redis (get, set, delete)
- `CacheManager` - Spring's caching abstraction

## Cache Annotations
[TO BE FILLED: @Cacheable, @CachePut, @CacheEvict explained]

## Cache Configuration
[TO BE FILLED: RedisCacheConfiguration, CacheManager setup]

## Cache Invalidation
[TO BE FILLED: When to evict, update strategies]

## Cache Keys
[TO BE FILLED: Key generation, custom key generators]

## TTL Strategy
[TO BE FILLED: Time-to-live configuration, expiration policies]

## Testing Cache
[TO BE FILLED: Redis CLI commands, verify caching works]
