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

Spring provides three main caching annotations to declaratively manage caching.

### @Cacheable - Cache Read Operations

**Purpose:** Cache method results. On first call, execute method and store result. On subsequent calls, return cached value.

**Location:** `PortfolioService.java`

```java
@Cacheable(value = "portfolios", key = "#id")
public PortfolioDTO.Response getPortfolioById(String id) {
    log.debug("Fetching portfolio from database: {}", id);
    
    Portfolio portfolio = portfolioRepository.findById(id)
        .orElseThrow(() -> new PortfolioNotFoundException("Not found"));
    
    return portfolioMapper.toResponse(portfolio);
}
```

**How it works:**
```
1st call: getPortfolioById("p123")
  → Cache miss
  → Execute method (query MongoDB)
  → Store in Redis: portfolios::p123 = {portfolio data}
  → Return result

2nd call: getPortfolioById("p123")
  → Cache hit
  → Return from Redis (no database query!)
  → 50x faster
```

**Key Components:**
- `value = "portfolios"` - Cache name (namespace)
- `key = "#id"` - Cache key (use method parameter `id`)
- Final Redis key: `portfolios::p123`

### @CacheEvict - Remove from Cache

**Purpose:** Remove cached data when it becomes stale (after updates/deletes).

**Example:**
```java
@Transactional
@CacheEvict(value = "portfolios", key = "#id")
public PortfolioDTO.Response updatePortfolio(String id, UpdateRequest request) {
    log.info("Updating portfolio: {}", id);
    
    Portfolio portfolio = findPortfolioOrThrow(id);
    portfolioMapper.updateEntityFromRequest(request, portfolio);
    
    Portfolio updated = portfolioRepository.save(portfolio);
    publishEvent(PORTFOLIO_UPDATED, updated);
    
    return portfolioMapper.toResponse(updated);
}
```

**What happens:**
```
Before update:
Redis cache: portfolios::p123 = {old data}

Update call: updatePortfolio("p123", newData)
  → @CacheEvict removes portfolios::p123 from Redis
  → Update database
  → Return updated data

Next read: getPortfolioById("p123")
  → Cache miss (was evicted)
  → Query database (gets fresh data)
  → Cache new data
```

**Why evict?** If we don't, cache returns stale data!

### @CachePut - Update Cache

**Purpose:** Always execute method AND update cache. Used when you want to refresh cache with new value.

**Example:**
```java
@CachePut(value = "portfolios", key = "#result.id")
public PortfolioDTO.Response createPortfolio(CreateRequest request) {
    Portfolio portfolio = portfolioMapper.toEntity(request);
    portfolio.setStatus(PortfolioStatus.ACTIVE);
    
    Portfolio saved = portfolioRepository.save(portfolio);
    publishEvent(PORTFOLIO_CREATED, saved);
    
    return portfolioMapper.toResponse(saved);  // Cached!
}
```

**Difference from @Cacheable:**
- `@Cacheable`: Skip method if cached
- `@CachePut`: Always execute method, update cache

**Key expression:**
- `key = "#result.id"` - Use return value's ID as key
- `#result` refers to method return value

### Multiple Cache Operations

```java
@Caching(
    evict = {
        @CacheEvict(value = "portfolios", key = "#portfolioId"),
        @CacheEvict(value = "portfolioSummaries", allEntries = true)
    }
)
public void addHolding(String portfolioId, HoldingRequest request) {
    // Evicts both portfolio and all summaries
}
```

### Conditional Caching

```java
@Cacheable(
    value = "portfolios",
    key = "#id",
    condition = "#id != null",           // Only cache if ID not null
    unless = "#result.status == 'CLOSED'" // Don't cache closed portfolios
)
public PortfolioDTO.Response getPortfolio(String id) {
    // ...
}
```

**Expressions:**
- `condition` - Evaluated before method (cache lookup)
- `unless` - Evaluated after method (with result)

## Cache Configuration

### Redis Cache Configuration Bean

**Location:** `config/RedisConfig.java`

```java
@Configuration
@EnableCaching  // Activate Spring's caching infrastructure
public class RedisConfig {
    
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            // Cache entries expire after 1 hour
            .entryTtl(Duration.ofHours(1))
            
            // Don't cache null values
            .disableCachingNullValues()
            
            // Serialize keys as strings
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            
            // Serialize values as JSON
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = cacheConfiguration();
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()  // Participate in Spring transactions
            .build();
    }
}
```

### Configuration Explained

#### TTL (Time-to-Live)
```java
.entryTtl(Duration.ofHours(1))
```
- Cached data expires after 1 hour
- Prevents stale data from living forever
- Balances performance vs freshness

#### Null Value Handling
```java
.disableCachingNullValues()
```
- Don't cache `null` results
- Prevents cache pollution
- Forces re-query if entity not found

#### Key Serialization
```java
.serializeKeysWith(StringRedisSerializer())
```
- Keys stored as plain strings in Redis
- Example: `portfolios::p123` (readable)
- Makes debugging easier

#### Value Serialization
```java
.serializeValuesWith(GenericJackson2JsonRedisSerializer())
```
- Values stored as JSON
- Example: `{"id":"p123","clientName":"John",...}`
- Human-readable in Redis CLI

### Per-Cache Custom Configuration

```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory factory) {
    // Default: 1 hour TTL
    RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
        .defaultCacheConfig()
        .entryTtl(Duration.ofHours(1));
    
    // Custom TTL for specific caches
    Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
    
    // Portfolios: 30 minutes (changes frequently)
    cacheConfigs.put("portfolios", 
        defaultConfig.entryTtl(Duration.ofMinutes(30)));
    
    // Summaries: 10 minutes (derived data)
    cacheConfigs.put("portfolioSummaries",
        defaultConfig.entryTtl(Duration.ofMinutes(10)));
    
    return RedisCacheManager.builder(factory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigs)
        .build();
}
```

## Cache Invalidation

### The Two Hard Problems

> "There are only two hard things in Computer Science: cache invalidation and naming things." - Phil Karlton

**The Problem:** When data changes, cache becomes stale.

### Invalidation Strategies

#### 1. Evict on Write (Recommended)

```java
// Update portfolio → Evict cache
@CacheEvict(value = "portfolios", key = "#id")
public PortfolioDTO.Response updatePortfolio(String id, UpdateRequest request) {
    Portfolio updated = portfolioRepository.save(portfolio);
    return portfolioMapper.toResponse(updated);
    // Next read will cache fresh data
}
```

✅ **Pros:** Simple, cache always fresh after write  
❌ **Cons:** Next read is slower (cache miss)

#### 2. Update on Write

```java
// Update portfolio → Update cache
@CachePut(value = "portfolios", key = "#id")
public PortfolioDTO.Response updatePortfolio(String id, UpdateRequest request) {
    Portfolio updated = portfolioRepository.save(portfolio);
    return portfolioMapper.toResponse(updated);
    // Cache updated with fresh data
}
```

✅ **Pros:** Cache always warm, next read is fast  
❌ **Cons:** Wastes effort if data rarely read

#### 3. TTL-Based Expiration

```java
.entryTtl(Duration.ofMinutes(30))
```

✅ **Pros:** Automatic cleanup, no manual invalidation  
❌ **Cons:** Can serve stale data until expiration

#### 4. Evict All Entries

```java
@CacheEvict(value = "portfolioSummaries", allEntries = true)
public void recalculateAllSummaries() {
    // Clears entire "portfolioSummaries" cache
}
```

**Use when:** Bulk updates affect many cached items

### Your Project's Strategy

**Portfolio Service uses:**

1. **@Cacheable for reads:**
```java
@Cacheable(value = "portfolios", key = "#id")
public PortfolioDTO.Response getPortfolioById(String id)
```

2. **@CacheEvict for updates:**
```java
@CacheEvict(value = "portfolios", key = "#id")
public PortfolioDTO.Response updatePortfolio(String id, UpdateRequest request)

@CacheEvict(value = "portfolios", key = "#portfolioId")
public PortfolioDTO.Response addHolding(String portfolioId, HoldingRequest request)
```

3. **TTL as safety net:**
```yaml
cache:
  redis:
    time-to-live: 3600000  # 1 hour
```

### Cache Stampede Problem

**Scenario:** Cache expires, 1000 requests hit at once

```
Cache expires at 10:00:00
10:00:01 - Request 1: Cache miss → Query DB
10:00:01 - Request 2: Cache miss → Query DB
10:00:01 - Request 3: Cache miss → Query DB
...
10:00:01 - Request 1000: Cache miss → Query DB

1000 identical database queries! 💥
```

**Solution: Synchronized Caching**

```java
@Cacheable(value = "portfolios", key = "#id", sync = true)
public PortfolioDTO.Response getPortfolioById(String id) {
    // Only ONE thread queries DB
    // Others wait for cached result
}
```

**With sync=true:**
```
10:00:01 - Request 1: Cache miss → Lock acquired → Query DB
10:00:01 - Request 2-1000: Wait for Request 1
10:00:02 - Request 1: Completes, caches result, releases lock
10:00:02 - Request 2-1000: Cache hit! (from Request 1)

Only 1 database query ✅
```

## Cache Keys

### Default Key Generation

Spring generates cache keys from method parameters:

```java
@Cacheable("portfolios")
public Portfolio getById(String id)
// Key: portfolios::id (literal "id", not the value!)

@Cacheable(value = "portfolios", key = "#id")
public Portfolio getById(String id)
// Key: portfolios::p123 (uses actual value!)
```

### SpEL (Spring Expression Language)

#### Using Method Parameters

```java
// Single parameter
@Cacheable(value = "portfolios", key = "#id")
public Portfolio getById(String id)
// Key: portfolios::p123

// Multiple parameters
@Cacheable(value = "portfolios", key = "#clientId + '-' + #accountNumber")
public Portfolio getByClientAndAccount(String clientId, String accountNumber)
// Key: portfolios::CLIENT001-ACC12345

// Object parameter
@Cacheable(value = "portfolios", key = "#request.clientId")
public Portfolio create(CreateRequest request)
// Key: portfolios::CLIENT001
```

#### Using Return Value

```java
@CachePut(value = "portfolios", key = "#result.id")
public PortfolioDTO.Response createPortfolio(CreateRequest request) {
    // ...
    return response;
}
// Key: portfolios::{generated ID from response}
```

#### Root Objects

```java
@Cacheable(value = "portfolios", key = "#root.methodName + #id")
public Portfolio getById(String id)
// Key: portfolios::getById-p123
```

**Available root objects:**
- `#root.method` - Method object
- `#root.methodName` - Method name string
- `#root.target` - Target object
- `#root.targetClass` - Target class
- `#root.args` - Array of arguments
- `#root.caches` - Collection of caches

### Custom Key Generator

For complex key generation logic:

```java
@Component
public class PortfolioKeyGenerator implements KeyGenerator {
    
    @Override
    public Object generate(Object target, Method method, Object... params) {
        // Custom logic to generate key
        StringBuilder key = new StringBuilder();
        key.append(method.getName());
        
        for (Object param : params) {
            if (param != null) {
                key.append("_").append(param.toString());
            }
        }
        
        return key.toString();
    }
}
```

**Usage:**
```java
@Cacheable(value = "portfolios", keyGenerator = "portfolioKeyGenerator")
public Portfolio getById(String id)
```

### Key Best Practices

✅ **Include all identifying parameters:**
```java
// Good: Unique per client AND account
@Cacheable(key = "#clientId + '-' + #accountNumber")
```

❌ **Don't omit important parameters:**
```java
// Bad: Same cache for all clients!
@Cacheable(key = "#accountNumber")  
```

✅ **Keep keys short but descriptive:**
```java
// Good
key = "p123"

// Wasteful
key = "portfolio-id-p123-for-client-CLIENT001-with-account-ACC12345"
```

✅ **Use consistent delimiters:**
```java
// Standard: Use "::" or "-"
key = "CLIENT001::ACC12345"
```

## TTL Strategy

### What is TTL?

**TTL (Time-to-Live):** How long cached data remains valid before automatic expiration.

```
Cache entry at 10:00:00 with TTL=1 hour
↓
Expires at 11:00:00
↓
Next request after 11:00:00 → Cache miss → Query DB
```

### Configuring TTL

#### Global TTL (All Caches)

```yaml
# application.yml
spring:
  cache:
    redis:
      time-to-live: 3600000  # 1 hour in milliseconds
```

#### Programmatic TTL

```java
@Bean
public RedisCacheConfiguration cacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(1));  // 1 hour
}
```

#### Per-Cache TTL

```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory factory) {
    Map<String, RedisCacheConfiguration> configs = new HashMap<>();
    
    // Short TTL for frequently changing data
    configs.put("portfolios", 
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10)));
    
    // Longer TTL for static reference data
    configs.put("assetTypes",
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofDays(1)));
    
    // Medium TTL for calculated data
    configs.put("portfolioSummaries",
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30)));
    
    return RedisCacheManager.builder(factory)
        .withInitialCacheConfigurations(configs)
        .build();
}
```

### Choosing TTL Duration

#### Factors to Consider:

1. **Data Change Frequency**
```
Frequently changing → Short TTL
Rarely changing → Long TTL
```

2. **Staleness Tolerance**
```
Real-time critical → Short TTL or no cache
Eventually consistent OK → Long TTL
```

3. **Query Cost**
```
Expensive queries → Longer TTL (save DB resources)
Cheap queries → Shorter TTL or no cache
```

4. **Memory Constraints**
```
Limited memory → Shorter TTL (evict sooner)
Plenty of memory → Longer TTL
```

### TTL Guidelines by Data Type

| Data Type | Example | Recommended TTL | Reason |
|-----------|---------|----------------|---------|
| **Master Data** | Asset types, currencies | 24 hours | Rarely changes |
| **Aggregate Data** | Portfolio summaries | 30-60 minutes | Derived, can recalculate |
| **Entity Data** | Portfolios, transactions | 10-30 minutes | Changes occasionally |
| **User Session** | Auth tokens | Session duration | Security boundary |
| **Real-time Data** | Stock prices | 1-5 minutes or no cache | Changes constantly |
| **Historical Data** | Closed portfolios | 24+ hours | Immutable |

### Your Project's TTL Strategy

**Portfolio Service:**
```yaml
spring:
  cache:
    redis:
      time-to-live: 3600000  # 1 hour default
```

**Why 1 hour?**
- Portfolios don't change every second
- Balance between performance and freshness
- Manual eviction on updates keeps data fresh
- TTL is safety net for forgotten evictions

### TTL vs Manual Eviction

**Use TTL when:**
- ✅ Can tolerate some staleness
- ✅ Data changes unpredictably
- ✅ Simplicity is important
- ✅ Memory management needed

**Use Manual Eviction (@CacheEvict) when:**
- ✅ Need immediate consistency
- ✅ Know exactly when data changes
- ✅ Can't tolerate stale data
- ✅ Write operations are well-defined

**Best Practice: Use Both!**
```java
// Manual eviction for immediate freshness
@CacheEvict(value = "portfolios", key = "#id")
public void updatePortfolio(String id, UpdateRequest request) {
    // ...
}

// TTL as safety net (1 hour)
.entryTtl(Duration.ofHours(1))
```

### Monitoring TTL

Check remaining TTL in Redis:
```bash
# Redis CLI
TTL portfolios::p123
# Output: 2847 (seconds remaining)

# -1 = No expiration set
# -2 = Key doesn't exist
```

## Testing Cache

### Redis CLI Commands

#### Connect to Redis Container

```powershell
# Connect to Redis CLI
docker exec -it redis-cache redis-cli

# Or in one command
docker exec -it redis-cache redis-cli ping
# Output: PONG
```

#### View All Keys

```bash
# List all keys
KEYS *

# Output:
1) "portfolios::6953bcf8336b5f7dcd43a2d6"
2) "portfolios::p123"
3) "portfolioSummaries::CLIENT001"
```

#### Inspect Specific Key

```bash
# Check if key exists
EXISTS portfolios::p123
# Output: 1 (exists) or 0 (doesn't exist)

# Get key type
TYPE portfolios::p123
# Output: string

# Get value
GET portfolios::p123
# Output: JSON string of cached portfolio

# Get remaining TTL
TTL portfolios::p123
# Output: 3456 (seconds remaining)
```

#### View Cached Data

```bash
# Get cached portfolio (JSON)
GET portfolios::p123

# Output (formatted):
{
  "id": "p123",
  "clientId": "CLIENT001",
  "clientName": "John Doe",
  "accountNumber": "ACC12345",
  "totalValue": 150000.00,
  "holdings": [...]
}
```

#### Delete Keys (Manual Eviction)

```bash
# Delete single key
DEL portfolios::p123
# Output: 1 (deleted)

# Delete multiple keys
DEL portfolios::p123 portfolios::p456
# Output: 2 (number deleted)

# Delete all keys matching pattern
KEYS portfolios::* | xargs redis-cli DEL

# Flush entire cache (DANGEROUS!)
FLUSHALL
```

### Testing Cache Behavior

#### Test 1: Verify Caching Works

```powershell
# 1. Create a portfolio
$portfolio = @{
    clientId = "CLIENT001"
    clientName = "Test User"
    accountNumber = "ACC99999"
    currency = "USD"
    cashBalance = 10000.00
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri http://localhost:8081/api/portfolio/portfolios `
    -Method Post -Body $portfolio -ContentType "application/json"

$portfolioId = $response.id

# 2. First read - Should hit database
Measure-Command {
    Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId"
}
# Output: ~100-200ms (database query)

# 3. Second read - Should hit cache
Measure-Command {
    Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId"
}
# Output: ~10-20ms (cache hit!) ✅

# 4. Verify in Redis
docker exec -it redis-cache redis-cli EXISTS "portfolios::$portfolioId"
# Output: 1 (key exists in cache)
```

#### Test 2: Verify Cache Eviction

```powershell
# 1. Get portfolio (caches it)
$portfolio = Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId"

# 2. Verify cached
docker exec -it redis-cache redis-cli EXISTS "portfolios::$portfolioId"
# Output: 1

# 3. Update portfolio (should evict cache)
$update = @{ cashBalance = 20000.00 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId" `
    -Method Put -Body $update -ContentType "application/json"

# 4. Verify evicted
docker exec -it redis-cache redis-cli EXISTS "portfolios::$portfolioId"
# Output: 0 (cache was evicted!) ✅

# 5. Next read will cache again
Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId"

docker exec -it redis-cache redis-cli EXISTS "portfolios::$portfolioId"
# Output: 1 (cached again)
```

#### Test 3: Verify TTL Expiration

```bash
# 1. Get remaining TTL
docker exec -it redis-cache redis-cli TTL portfolios::p123
# Output: 3600 (1 hour in seconds)

# 2. Wait or manually set shorter TTL for testing
docker exec -it redis-cache redis-cli EXPIRE portfolios::p123 10
# Key will expire in 10 seconds

# 3. Check after 10 seconds
docker exec -it redis-cache redis-cli EXISTS portfolios::p123
# Output: 0 (expired and removed)
```

### Unit Testing with Cache

#### Test Setup with Embedded Redis

```java
@SpringBootTest
@Testcontainers
class PortfolioServiceCacheTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private PortfolioService portfolioService;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private CacheManager cacheManager;
}
```

#### Test 1: Verify @Cacheable Works

```java
@Test
void shouldCachePortfolioOnFirstRead() {
    // Arrange
    Portfolio portfolio = createTestPortfolio();
    portfolioRepository.save(portfolio);
    
    // Act - First call
    PortfolioDTO.Response response1 = portfolioService.getPortfolioById(portfolio.getId());
    
    // Verify cached
    Cache cache = cacheManager.getCache("portfolios");
    assertNotNull(cache);
    assertNotNull(cache.get(portfolio.getId()));
    
    // Act - Second call
    PortfolioDTO.Response response2 = portfolioService.getPortfolioById(portfolio.getId());
    
    // Assert
    assertEquals(response1.getId(), response2.getId());
    
    // Verify repository called only once
    verify(portfolioRepository, times(1)).findById(portfolio.getId());
}
```

#### Test 2: Verify @CacheEvict Works

```java
@Test
void shouldEvictCacheOnUpdate() {
    // Arrange
    Portfolio portfolio = createTestPortfolio();
    portfolioRepository.save(portfolio);
    
    // Cache the portfolio
    portfolioService.getPortfolioById(portfolio.getId());
    
    Cache cache = cacheManager.getCache("portfolios");
    assertNotNull(cache.get(portfolio.getId()), "Should be cached");
    
    // Act - Update (should evict)
    UpdateRequest request = new UpdateRequest();
    request.setCashBalance(new BigDecimal("50000"));
    portfolioService.updatePortfolio(portfolio.getId(), request);
    
    // Assert - Cache evicted
    assertNull(cache.get(portfolio.getId()), "Cache should be evicted");
}
```

#### Test 3: Verify Cache Improves Performance

```java
@Test
void cacheShouldImprovePerformance() {
    // Arrange
    Portfolio portfolio = createTestPortfolio();
    portfolioRepository.save(portfolio);
    
    // Act - Measure first call (cache miss)
    long start1 = System.currentTimeMillis();
    portfolioService.getPortfolioById(portfolio.getId());
    long time1 = System.currentTimeMillis() - start1;
    
    // Act - Measure second call (cache hit)
    long start2 = System.currentTimeMillis();
    portfolioService.getPortfolioById(portfolio.getId());
    long time2 = System.currentTimeMillis() - start2;
    
    // Assert - Second call should be significantly faster
    assertTrue(time2 < time1, 
        "Cached call should be faster. First: " + time1 + "ms, Second: " + time2 + "ms");
}
```

### Monitoring Cache in Production

#### Spring Boot Actuator Metrics

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,caches
  metrics:
    cache:
      instrument-cache: true
```

**Access cache metrics:**
```bash
curl http://localhost:8081/api/portfolio/actuator/caches

# Output:
{
  "cacheManagers": {
    "cacheManager": {
      "caches": {
        "portfolios": {
          "target": "org.springframework.data.redis.cache.RedisCache"
        }
      }
    }
  }
}
```

#### Redis INFO Command

```bash
docker exec -it redis-cache redis-cli INFO stats

# Output:
# Stats
total_connections_received:150
total_commands_processed:5023
instantaneous_ops_per_sec:15
keyspace_hits:4523      # Cache hits
keyspace_misses:500     # Cache misses

# Hit rate = 4523 / (4523 + 500) = 90% ✅
```

#### Monitor Cache Size

```bash
# Number of keys
docker exec -it redis-cache redis-cli DBSIZE
# Output: 42

# Memory usage
docker exec -it redis-cache redis-cli INFO memory | grep used_memory_human
# Output: used_memory_human:2.15M
```

---

## Summary

### Key Concepts

1. **Caching Pattern:** Cache-Aside (check cache → if miss, query DB → store in cache)
2. **Annotations:**
   - `@Cacheable` - Cache method results
   - `@CacheEvict` - Remove from cache
   - `@CachePut` - Update cache
3. **Configuration:** RedisCacheConfiguration with TTL, serialization
4. **Invalidation:** Evict on write + TTL as safety net
5. **Keys:** Use SpEL to generate unique keys from method parameters

### Performance Impact

**Your Project:**
- Without cache: ~50-100ms per portfolio read
- With cache: ~1-5ms per portfolio read
- **10-50x performance improvement!**

### Best Practices

✅ **Cache read-heavy data** (portfolios queried frequently)  
✅ **Evict on write** (keep cache fresh)  
✅ **Use TTL as safety net** (prevent stale data)  
✅ **Monitor cache hit rate** (aim for 80%+)  
✅ **Use sync=true** (prevent cache stampede)  
✅ **Short, unique keys** (efficient memory usage)  

❌ **Don't cache frequently changing data** (real-time prices)  
❌ **Don't cache sensitive data** (passwords, tokens)  
❌ **Don't cache without TTL** (can serve stale data forever)  
❌ **Don't forget to evict** (leads to stale cache)  

### Interview Talking Points

**Q: Why use Redis for caching?**  
A: In-memory storage provides 50-100x faster reads than MongoDB, reducing database load and improving response times for frequently accessed data like portfolios.

**Q: How do you prevent stale cache?**  
A: Use `@CacheEvict` on update operations to immediately invalidate cache, plus TTL (1 hour) as a safety net for any missed evictions.

**Q: What's the cache stampede problem?**  
A: When cache expires and many concurrent requests trigger database queries. Solution: Use `@Cacheable(sync=true)` so only one thread queries the database while others wait for the cached result.

**Q: How do you choose TTL duration?**  
A: Consider data change frequency, staleness tolerance, and query cost. Portfolios change occasionally so 1 hour TTL balances performance and freshness. More dynamic data needs shorter TTL.

**Q: How would you scale the cache?**  
A: Redis Cluster for horizontal scaling, Redis Sentinel for high availability, or use Redis as a service (AWS ElastiCache, Azure Cache for Redis) for managed scaling and replication.

### Next Steps

- Review MongoDB integration (Guide 04)
- Understand distributed cache invalidation in multi-instance deployments
- Learn Redis Cluster for production scaling

