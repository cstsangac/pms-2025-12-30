# Technical Interview Questions - Based on Job Description

## Overview

**Job Requirements Focus:**
- Core Java & Spring Framework
- Event/message-driven architectures (Kafka)
- OpenAPI, microservices, caching
- Document databases
- Test-driven development (Mock/MockServer)
- DevOps practices

---

## Core Java Questions

### Q1: What's the difference between `@Component`, `@Service`, and `@Repository`?

**Expected Answer:**

All three are Spring stereotype annotations that mark a class as a Spring-managed bean, but with different semantic purposes:

**`@Component`** - Generic stereotype for any Spring-managed component
```java
@Component
public class EmailValidator {
    // Generic component
}
```

**`@Service`** - Indicates business logic layer
```java
@Service
public class PortfolioService {
    // Business logic here
}
```

**`@Repository`** - Indicates data access layer (DAO)
- Adds **automatic exception translation** (SQLException → DataAccessException)
- Semantic clarity for persistence layer

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {
    // Data access methods
}
```

**Where in your project:**
- [PortfolioService.java](portfolio-service/src/main/java/com/portfolio/service/PortfolioService.java) uses `@Service`
- [PortfolioRepository.java](portfolio-service/src/main/java/com/portfolio/repository/PortfolioRepository.java) uses `@Repository` (implicit via MongoRepository)

---

### Q2: Explain how Spring dependency injection works. Constructor vs field injection?

**Expected Answer:**

**Dependency Injection (DI)** - Spring creates and injects dependencies rather than objects creating them.

**Constructor Injection** ✅ (Preferred):
```java
@Service
@RequiredArgsConstructor // Lombok generates constructor
public class PortfolioService {
    private final PortfolioRepository repository;
    private final KafkaTemplate<String, Event> kafkaTemplate;
    
    // Spring injects dependencies via constructor
}
```

**Advantages:**
- Immutable dependencies (final fields)
- Easier to test (can create object without Spring)
- Makes dependencies explicit
- Prevents circular dependencies at startup

**Field Injection** ❌ (Avoid):
```java
@Service
public class PortfolioService {
    @Autowired
    private PortfolioRepository repository; // Harder to test
}
```

**Disadvantages:**
- Can't use final
- Harder to unit test (need reflection)
- Hides dependencies
- Circular dependencies fail at runtime

**Where in your project:**
Your services use constructor injection via `@RequiredArgsConstructor` (best practice!)

---

### Q3: What is `@Transactional` and how does it work under the hood?

**Expected Answer:**

**`@Transactional`** ensures method executes within a database transaction (ACID guarantees).

**How it works:**
1. Spring creates a **proxy** around the class using AOP
2. Proxy intercepts method calls
3. Begins transaction before method execution
4. Commits on successful completion
5. Rolls back on unchecked exception

**Example from your project:**
```java
@Service
public class TransactionService {
    
    @Transactional
    public Transaction processTransaction(TransactionRequest request) {
        // 1. Validate transaction
        // 2. Update portfolio
        // 3. Publish event
        // All-or-nothing: if any step fails, entire transaction rolls back
    }
}
```

**Important attributes:**
```java
@Transactional(
    isolation = Isolation.READ_COMMITTED,  // Prevent dirty reads
    propagation = Propagation.REQUIRED,    // Join existing or create new
    timeout = 30,                          // 30 seconds max
    rollbackFor = Exception.class          // Roll back on checked exceptions too
)
```

**Common Pitfalls:**
- Only works on **public methods**
- Doesn't work with **self-invocation** (calling @Transactional method from same class)
- Must throw **unchecked exception** for rollback (or configure rollbackFor)

**Where in your project:**
Check TransactionService.java for transaction management examples

---

### Q4: Difference between `==` and `.equals()` in Java?

**Expected Answer:**

**`==` operator** - Compares **references** (memory addresses)
```java
String s1 = new String("hello");
String s2 = new String("hello");
s1 == s2  // false (different objects in memory)
```

**`.equals()` method** - Compares **values** (if properly overridden)
```java
s1.equals(s2)  // true (same content)
```

**For primitives:**
```java
int a = 5;
int b = 5;
a == b  // true (primitives compare values)
```

**String pool caveat:**
```java
String s1 = "hello";  // From string pool
String s2 = "hello";  // Same object from pool
s1 == s2  // true (same reference!)
```

**In your project:**
```java
// Correct way to compare Portfolio IDs
if (portfolio.getId().equals(transactionRequest.getPortfolioId())) {
    // Process transaction
}

// Wrong!
if (portfolio.getId() == transactionRequest.getPortfolioId()) {
    // May fail even if IDs are same
}
```

**Best Practice:** Always use `.equals()` for object comparison, override `equals()` and `hashCode()` together.

---

### Q5: How does HashMap work internally? What happens on collision?

**Expected Answer:**

**HashMap Structure:**
- Array of **buckets** (default 16)
- Each bucket contains linked list (or tree since Java 8)
- Hash code determines which bucket

**Put Operation:**
```java
Map<String, Portfolio> cache = new HashMap<>();
cache.put("portfolio123", portfolio);
```

**Internal process:**
1. Calculate hash: `"portfolio123".hashCode()` → integer
2. Determine bucket: `hash % buckets.length` → bucket index
3. Store key-value pair in that bucket

**Collision Handling:**

When two keys map to same bucket:

**Java 7 and earlier:** Linked list
```
Bucket[5]: Node1 → Node2 → Node3
           (key1)  (key2)  (key3)
```
- O(n) lookup in worst case

**Java 8+:** Tree (when list size > 8)
```
Bucket[5]: TreeNode
           /      \
        Node1    Node2
```
- O(log n) lookup

**Load Factor & Resizing:**
```java
// Default load factor = 0.75
// When size > capacity * 0.75, resize (double capacity)
HashMap<String, String> map = new HashMap<>(16, 0.75f);
// Resizes when 12 elements added (16 * 0.75)
```

**Where in your project:**
MongoDB uses similar hashing for sharding. Redis internally uses hash tables for O(1) lookups.

**Interview Tip:** Mention the Java 8 tree optimization - shows you know recent improvements!

## Event/Message-Driven Architecture Questions

### Q6: How do you ensure message ordering in Kafka?

**Expected Answer:**

Kafka **guarantees ordering within a partition**, not across partitions.

**Strategy 1: Use Partition Key**
```java
@Service
public class TransactionEventPublisher {
    
    public void publishEvent(TransactionEvent event) {
        // Use portfolioId as key
        kafkaTemplate.send(
            "transaction-events",
            event.getPortfolioId(),  // ← PARTITION KEY
            event
        );
    }
}
```

**How it works:**
- Messages with same key → same partition
- Within partition → strict ordering (FIFO)
- Different portfolios can process in parallel

**Example:**
```
Portfolio A transactions → Partition 0 → Ordered ✓
Portfolio B transactions → Partition 1 → Ordered ✓
Portfolio C transactions → Partition 0 → Ordered ✓ (same partition as A, but different key)
```

**Strategy 2: Single Partition (Not Scalable)**
```java
// Only use if order across ALL messages is critical
@KafkaListener(topics = "transaction-events", concurrency = "1")
```

**Where in your project:**
Check how events are published in TransactionService - ensure portfolioId is used as partition key.

---

### Q7: What happens if a Kafka consumer fails while processing a message?

**Expected Answer:**

**Default behavior:**
1. Consumer receives message
2. Starts processing
3. **Consumer crashes**
4. Offset NOT committed yet
5. On restart, message is **reprocessed**

**This means consumers must be IDEMPOTENT!**

**Example - Non-Idempotent (BAD):**
```java
@KafkaListener(topics = "transaction-events")
public void handleTransaction(TransactionEvent event) {
    // If this runs twice, balance is wrong!
    portfolio.setBalance(portfolio.getBalance() + event.getAmount());
}
```

**Idempotent Solution 1: Check if already processed**
```java
@KafkaListener(topics = "transaction-events")
public void handleTransaction(TransactionEvent event) {
    if (processedEvents.contains(event.getEventId())) {
        return; // Already processed, skip
    }
    
    portfolio.updateBalance(event.getAmount());
    processedEvents.add(event.getEventId());
}
```

**Idempotent Solution 2: Use unique constraint**
```java
@KafkaListener(topics = "transaction-events")
public void handleTransaction(TransactionEvent event) {
    try {
        eventRepository.save(event); // Unique constraint on eventId
        portfolio.updateBalance(event.getAmount());
    } catch (DuplicateKeyException e) {
        // Already processed, ignore
    }
}
```

**Configuration options:**
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # Manual offset management
      max-poll-records: 10       # Process fewer messages per poll
    listener:
      ack-mode: manual           # Commit only after successful processing
```

**Where in your project:**
Review [NotificationService.java](notification-service/src/main/java/com/notification/consumer/TransactionEventConsumer.java) - ensure idempotency.

---

### Q8: Kafka vs RabbitMQ vs AWS SQS - when to use each?

**Expected Answer:**

| Feature | Kafka | RabbitMQ | AWS SQS |
|---------|-------|----------|---------|
| **Use Case** | Event streaming, logs, replay | Traditional messaging, routing | Simple queuing, AWS-native |
| **Ordering** | Per-partition | Queue-level | FIFO queues available |
| **Persistence** | Always persisted (configurable retention) | Optional | Always persisted |
| **Consumers** | Multiple can read same message | Message consumed once | Message consumed once |
| **Throughput** | Very high (millions/sec) | High | Medium |
| **Complexity** | High (needs Zookeeper) | Medium | Low (managed service) |

**Choose Kafka when:**
✅ Need **event sourcing** (replay events)
✅ Multiple consumers need same data
✅ High throughput required
✅ Order within entity is critical
✅ Need audit trail (keep events forever)

**Your project uses Kafka because:**
- Portfolio and Notification services both consume transaction events
- Can replay events to rebuild state
- Demonstrates enterprise event-driven architecture

**Choose RabbitMQ when:**
✅ Complex routing (topic, fanout, direct exchanges)
✅ Traditional request/response patterns
✅ Don't need message replay
✅ Lower operational complexity than Kafka

**Choose SQS when:**
✅ Already on AWS
✅ Simple queuing needs
✅ Don't want to manage infrastructure
✅ Serverless architecture (Lambda triggers)

**Interview Tip:** "For this demo, I chose Kafka to show event-driven patterns. In production, I'd evaluate based on replay requirements, consumer count, and operational overhead."

---

### Q9: How do you handle poison messages (messages that always fail)?

**Expected Answer:**

**Poison Message** - Message that repeatedly fails processing (bad format, missing data, etc.)

**Without handling:**
```
Consumer receives message → Process fails → Requeue
→ Process fails → Requeue → INFINITE LOOP
→ Consumer stuck, other messages blocked
```

**Solution 1: Dead Letter Queue (DLQ)**
```java
@Configuration
public class KafkaConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Event> kafkaListenerContainerFactory() {
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000L, 3)  // 3 retries, 1 sec apart
            )
        );
        return factory;
    }
}
```

**Flow:**
```
Message fails → Retry 3 times → Still fails → Send to DLQ topic
→ Original queue keeps processing other messages
```

**Solution 2: Max Retry with Manual Review**
```java
@KafkaListener(topics = "transaction-events")
public void handleEvent(TransactionEvent event, 
                        @Header(KafkaHeaders.DELIVERY_ATTEMPT) int attempt) {
    try {
        processEvent(event);
    } catch (Exception e) {
        if (attempt >= 3) {
            // Log for manual review
            logger.error("Failed after 3 attempts: {}", event);
            alertOps(event);  // Send to ops team
            return; // Skip message
        }
        throw e; // Retry
    }
}
```

**Solution 3: Skip with Logging**
```java
@KafkaListener(topics = "transaction-events")
public void handleEvent(TransactionEvent event) {
    try {
        processEvent(event);
    } catch (Exception e) {
        logger.error("Failed to process event: {}", event, e);
        // Don't rethrow - acknowledge and move on
        // Store in failed_events table for later analysis
        failedEventRepository.save(event, e.getMessage());
    }
}
```

**Best Practice: Combination**
1. Retry 3 times with backoff
2. Send to DLQ
3. Alert operations team
4. Dashboard showing DLQ size
5. Tool to replay DLQ messages after fix

**Where in your project:**
Add this configuration to your Kafka consumer setup. Current implementation may not handle poison messages gracefully.

**Interview Answer:** "I'd use DLQ with retries. After 3 attempts, send to separate topic for manual review. Monitor DLQ depth as operational metric."

## Microservices & OpenAPI Questions

### Q10: What are the challenges of microservices? How do you address them?

**Expected Answer:**

**Challenge 1: Distributed Tracing**

Problem: Request spans multiple services, hard to debug

Solution:
```yaml
# Add correlation ID to all requests
spring:
  sleuth:
    enabled: true
  zipkin:
    base-url: http://localhost:9411
```

```java
@RestController
public class PortfolioController {
    
    @GetMapping("/{id}")
    public Portfolio getPortfolio(@PathVariable String id,
                                  @RequestHeader("X-Correlation-ID") String correlationId) {
        logger.info("Request {}: Getting portfolio {}", correlationId, id);
        // Pass correlation ID to downstream services
    }
}
```

**Challenge 2: Data Consistency**

Problem: No distributed transactions (ACID) across services

Solutions in your project:
- **Eventual consistency** via Kafka events
- **Idempotent consumers** (process event multiple times safely)
- **Compensating transactions** (reverse failed operations)

```java
// Transaction Service creates transaction
transaction.setStatus(PENDING);
transactionRepository.save(transaction);
kafkaTemplate.send("transaction-events", event);

// Portfolio Service updates holdings (eventually)
@KafkaListener(topics = "transaction-events")
public void updateHoldings(TransactionEvent event) {
    // Idempotent: check if already processed
}
```

**Challenge 3: Cascading Failures**

Problem: One service down → all dependent services fail

Solution: **Circuit Breaker** in API Gateway
```java
@Bean
public RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("portfolio", r -> r.path("/api/portfolios/**")
            .filters(f -> f.circuitBreaker(c -> c
                .setName("portfolioCircuitBreaker")
                .setFallbackUri("/fallback/portfolios")))
            .uri("http://portfolio-service:8081"))
        .build();
}
```

**Challenge 4: Service Discovery**

Problem: Services need to find each other (IPs change in cloud)

Solutions:
- **Eureka** (Netflix): Service registry
- **Consul**: Service mesh
- **Kubernetes**: Built-in DNS service discovery

Your project: Docker network with service names
```yaml
# docker-compose.yml
services:
  portfolio-service:
    networks:
      - pms-network
  # Other services can reach via: http://portfolio-service:8081
```

**Challenge 5: Testing Complexity**

Problem: Need all services running to test

Solutions:
- **Unit tests** with mocks (test in isolation)
- **Contract testing** (Pact) - verify API contracts
- **Integration tests** with Testcontainers
- **End-to-end tests** (minimal, slow)

**Your Project Addresses:**
✅ Event-driven for eventual consistency
✅ Circuit breaker in gateway
✅ Health checks on all services
✅ Docker network for service discovery
✅ Unit + integration tests

**What's Missing (Acknowledge in Interview):**
- Distributed tracing (Sleuth/Zipkin)
- Centralized logging (ELK stack)
- Service mesh (Istio)
- API versioning

---

### Q11: How do you version your APIs?

**Expected Answer:**

**Strategy 1: URL Versioning** (Most Common)
```java
@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioControllerV1 {
    @GetMapping("/{id}")
    public PortfolioV1 getPortfolio(@PathVariable String id) {
        return service.getPortfolio(id);
    }
}

@RestController
@RequestMapping("/api/v2/portfolios")
public class PortfolioControllerV2 {
    @GetMapping("/{id}")
    public PortfolioV2 getPortfolio(@PathVariable String id) {
        // V2 includes additional fields
        return service.getPortfolioV2(id);
    }
}
```

✅ Pros: Clear, cacheable, easy to route
❌ Cons: URL changes, need multiple controllers

**Strategy 2: Header Versioning**
```java
@GetMapping(value = "/{id}", headers = "API-Version=1")
public PortfolioV1 getPortfolioV1(@PathVariable String id) { }

@GetMapping(value = "/{id}", headers = "API-Version=2")
public PortfolioV2 getPortfolioV2(@PathVariable String id) { }
```

✅ Pros: Same URL, cleaner
❌ Cons: Harder to test (can't just type URL in browser)

**Strategy 3: Content Negotiation**
```java
@GetMapping(value = "/{id}", produces = "application/vnd.portfolio.v1+json")
public PortfolioV1 getV1(@PathVariable String id) { }

@GetMapping(value = "/{id}", produces = "application/vnd.portfolio.v2+json")
public PortfolioV2 getV2(@PathVariable String id) { }
```

**Best Practice:**
- **Breaking changes** → New version (v1 → v2)
- **Backward compatible changes** → Same version (add optional fields)
- **Deprecate old versions** with sunset date

**In Your Project:**
Currently no versioning. Could improve:
```java
@RequestMapping("/api/v1/portfolios")  // Add v1
```

**Interview Tip:** "My demo doesn't version APIs since it's a greenfield project. In production, I'd use URL versioning for clarity and ease of routing."

---

### Q12: What's the difference between REST and gRPC?

**Expected Answer:**

| Aspect | REST | gRPC |
|--------|------|------|
| **Protocol** | HTTP/1.1 | HTTP/2 |
| **Data Format** | JSON (text) | Protocol Buffers (binary) |
| **Schema** | OpenAPI (optional) | .proto files (required) |
| **Performance** | Slower (text parsing) | Faster (binary, smaller payload) |
| **Browser Support** | Native | Requires proxy (gRPC-Web) |
| **Streaming** | Limited | Bi-directional streaming |
| **Human Readable** | Yes (JSON) | No (binary) |

**REST Example:**
```http
GET /api/portfolios/123
Accept: application/json

{
  "id": "123",
  "clientName": "John Doe",
  "totalValue": 50000.00
}
```

**gRPC Example:**
```protobuf
// portfolio.proto
service PortfolioService {
  rpc GetPortfolio(PortfolioRequest) returns (Portfolio);
}

message Portfolio {
  string id = 1;
  string client_name = 2;
  double total_value = 3;
}
```

**Use REST when:**
✅ Public APIs (external clients)
✅ Browser-based applications
✅ Human-readable format needed
✅ Flexibility over performance

**Use gRPC when:**
✅ Internal microservices communication
✅ High performance required
✅ Strong typing needed
✅ Bi-directional streaming (e.g., real-time updates)

**Your Project:**
Uses REST for simplicity and browser compatibility. Could add gRPC for internal service-to-service calls.

**Interview Answer:** "I chose REST for this demo because it's widely understood and works well with browsers. For high-throughput internal communication, gRPC would be more efficient."

---

### Q13: Explain Circuit Breaker pattern. Why is it important?

**Expected Answer:**

**Problem Without Circuit Breaker:**
```
API Gateway → Portfolio Service (down)
   ↓
Every request waits 30 seconds for timeout
   ↓
Gateway accumulates waiting threads
   ↓
Gateway runs out of resources
   ↓
ENTIRE SYSTEM DOWN (cascade failure)
```

**Circuit Breaker Solution:**

**States:**
```
CLOSED → Normal operation
   ↓ (failures exceed threshold)
OPEN → Fail fast, return fallback
   ↓ (after timeout period)
HALF_OPEN → Try one request
   ↓ (if successful)
CLOSED → Back to normal
```

**Implementation in Your Project:**
```java
// api-gateway/src/main/java/config/RouteConfig.java
@Bean
public RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("portfolio", r -> r.path("/api/portfolios/**")
            .filters(f -> f.circuitBreaker(config -> config
                .setName("portfolioCircuitBreaker")
                .setFallbackUri("forward:/fallback/portfolios")
                .setStatusCodes("500", "503")))  // Open on these
            .uri("http://portfolio-service:8081"))
        .build();
}

@RestController
public class FallbackController {
    
    @GetMapping("/fallback/portfolios")
    public ResponseEntity<String> portfolioFallback() {
        return ResponseEntity.status(503)
            .body("Portfolio service temporarily unavailable");
    }
}
```

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      portfolioCircuitBreaker:
        failure-rate-threshold: 50        # Open after 50% failures
        wait-duration-in-open-state: 10s  # Stay open for 10 sec
        sliding-window-size: 10           # Track last 10 requests
        permitted-number-of-calls-in-half-open-state: 3
```

**Benefits:**
1. **Fail fast** - Don't wait for timeout
2. **Give service time to recover** - Stop overwhelming failed service
3. **Graceful degradation** - Return cached/default data
4. **Prevent cascade failures** - Protect calling service

**Real-World Example:**
```
Black Friday sale → Product service overloaded
→ Circuit opens after 50% failure
→ Shopping cart returns: "Product details temporarily unavailable"
→ Users can still checkout (vs entire site down)
→ After 10 sec, try again → Service recovered → Circuit closes
```

**Interview Answer:** "Circuit breaker prevents cascade failures. If Portfolio Service is down, the gateway fails fast and returns a fallback response instead of timing out and exhausting resources. This keeps the gateway healthy even when downstream services fail."

## Caching Questions

### Q14: When would you NOT use caching?

**Expected Answer:**

**Don't cache when:**

❌ **1. Highly Dynamic Data**
```java
// Stock prices change every second
@Service
public class StockPriceService {
    // DON'T cache - data stale immediately
    public double getCurrentPrice(String symbol) {
        return stockApi.getRealTimePrice(symbol);
    }
}
```

❌ **2. User-Specific Sensitive Data**
```java
// Different for each user
@Cacheable("accounts")  // DANGEROUS!
public Account getAccount(String userId) {
    // User A might get User B's cached account!
}

// Better: Include userId in cache key
@Cacheable(value = "accounts", key = "#userId")
```

❌ **3. Data with Strong Consistency Requirements**
```java
// Banking transaction balance
// Must ALWAYS be accurate, no stale data acceptable
public BigDecimal getAccountBalance(String accountId) {
    return accountRepository.findBalance(accountId);  // No cache
}
```

❌ **4. Large Objects (Memory Pressure)**
```java
// 50MB report cached for 1000 users = 50GB RAM!
@Cacheable("reports")
public byte[] generateLargeReport(String userId) {
    // Consider caching URL to S3 instead
}
```

❌ **5. Data Changed Frequently by External Systems**
```java
// Updated by batch jobs, other services, manual DB changes
// Cache invalidation becomes complex
```

**When TO cache (Your Project):**

✅ **Portfolios** - Read-heavy, updates infrequent
```java
@Cacheable("portfolios")
public Portfolio getPortfolio(String id) {
    // Portfolio doesn't change often
    // Cache hit = 25x faster (2ms vs 50ms)
}
```

**Trade-off in Your Project:**
- TTL = 10 minutes
- Users might see slightly stale total value
- Acceptable for portfolio dashboard
- NOT acceptable for executing trades (must be real-time)

**Interview Answer:** "I wouldn't cache rapidly changing data like real-time stock prices, or user-specific sensitive data without proper cache key isolation. For portfolios, 10-minute staleness is acceptable for dashboard views, but I'd skip cache for trade execution."

---

### Q15: What's cache stampede? How do you prevent it?

**Expected Answer:**

**Cache Stampede** (Thundering Herd Problem):

```
Time 0:00: Cache entry expires (TTL = 10 min)
Time 0:01: 1000 concurrent requests arrive
           ↓
All 1000 requests: Cache MISS
           ↓
All 1000 query database simultaneously
           ↓
Database overwhelmed, slows down
           ↓
All 1000 requests take 30 seconds
           ↓
Even more requests pile up
           ↓
DATABASE CRASHES
```

**Problem in Your Project:**
```java
@Cacheable(value = "portfolios", key = "#id")
public Portfolio getPortfolio(String id) {
    // If popular portfolio cache expires at peak time
    // 1000 users viewing dashboard → 1000 DB queries
}
```

**Solution 1: Locking (Request Coalescing)**
```java
@Cacheable(value = "portfolios", key = "#id", sync = true)
// ↑ Only ONE thread queries DB, others wait for result
public Portfolio getPortfolio(String id) {
    return repository.findById(id).orElseThrow();
}
```

**How it works:**
```
1000 requests arrive
   ↓
First request: Query DB
Next 999 requests: Wait for first request
   ↓
First request completes, fills cache
   ↓
All 999 get result from cache
```

**Solution 2: Staggered Expiration**
```java
@Cacheable("portfolios")
public Portfolio getPortfolio(String id) {
    // TTL = 10 min + random(0-60 sec)
    // Not all entries expire at same time
}
```

```java
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .disableCachingNullValues();
    
    return RedisCacheManager.builder(factory)
        .cacheDefaults(config)
        .build();
}

// Add jitter in application code
int jitter = ThreadLocalRandom.current().nextInt(0, 60);
Duration ttl = Duration.ofMinutes(10).plusSeconds(jitter);
```

**Solution 3: Probabilistic Early Expiration**
```java
public Portfolio getPortfolio(String id) {
    CacheEntry entry = cache.get(id);
    
    if (entry != null) {
        long timeToExpire = entry.expiresAt - System.currentTimeMillis();
        long totalTTL = 10 * 60 * 1000; // 10 minutes
        
        // If within last 10% of TTL, maybe refresh early
        if (timeToExpire < totalTTL * 0.1) {
            double probability = 1.0 - (timeToExpire / (totalTTL * 0.1));
            if (Math.random() < probability) {
                // Refresh cache asynchronously
                refreshCacheAsync(id);
            }
        }
    }
    
    return entry != null ? entry.value : loadFromDb(id);
}
```

**Solution 4: Cache Warming**
```java
@Scheduled(cron = "0 */9 * * * *")  // Every 9 minutes
public void warmCache() {
    // Refresh before expiration
    List<String> popularIds = getTop100Portfolios();
    popularIds.forEach(id -> {
        Portfolio p = repository.findById(id).orElse(null);
        if (p != null) {
            cache.put(id, p);
        }
    });
}
```

**Best Practice for Your Project:**
```java
@Cacheable(value = "portfolios", key = "#id", sync = true)
// ↑ Add this to prevent stampede
public Portfolio getPortfolio(String id) {
    return repository.findById(id).orElseThrow();
}
```

**Interview Answer:** "Cache stampede happens when many requests hit an expired cache entry simultaneously, overwhelming the database. I'd use `sync = true` in @Cacheable to ensure only one thread queries the DB while others wait. For high-traffic systems, I'd also add staggered TTL and cache warming."

---

### Q16: Redis vs Memcached - differences?

**Expected Answer:**

| Feature | Redis | Memcached |
|---------|-------|-----------|
| **Data Structures** | Strings, Lists, Sets, Hashes, Sorted Sets | Only strings (key-value) |
| **Persistence** | RDB snapshots, AOF logs | No persistence (memory only) |
| **Replication** | Master-slave, clustering | No built-in replication |
| **Pub/Sub** | Yes | No |
| **Transactions** | Yes (MULTI/EXEC) | No |
| **Lua Scripting** | Yes | No |
| **Threading** | Single-threaded | Multi-threaded |
| **Max Value Size** | 512 MB | 1 MB |
| **Use Case** | Rich features, persistence | Pure caching, simplicity |

**Redis Data Structures Example:**
```java
// String
redisTemplate.opsForValue().set("portfolio:123", portfolio);

// Hash (store object fields separately)
redisTemplate.opsForHash().put("portfolio:123", "clientName", "John Doe");
redisTemplate.opsForHash().put("portfolio:123", "totalValue", "50000");

// List (recent transactions)
redisTemplate.opsForList().leftPush("recent:txn", transaction);

// Sorted Set (leaderboard by value)
redisTemplate.opsForZSet().add("top:portfolios", "portfolio:123", 50000.0);
```

**Memcached (only key-value):**
```java
memcachedClient.set("portfolio:123", 600, portfolio);
String value = memcachedClient.get("portfolio:123");
```

**Why Your Project Uses Redis:**
✅ Richer data structures (could use hashes for partial updates)
✅ Persistence option (can survive restarts)
✅ Industry standard for Spring Boot
✅ Future: Use pub/sub for cache invalidation across instances

**When to Use Memcached:**
- Simple key-value caching only
- Want multi-threaded performance
- Don't need persistence
- Existing infrastructure uses it

**Interview Answer:** "I chose Redis for richer data structures and future extensibility. If I only needed simple key-value caching with no persistence, Memcached would be simpler and potentially faster due to multi-threading."

---

### Q17: How do you invalidate cache across multiple service instances?

**Expected Answer:**

**Problem:**
```
Instance 1: Updates portfolio → Evicts local cache
Instance 2: Still has stale cache ← PROBLEM!
Instance 3: Still has stale cache ← PROBLEM!
```

**Solution 1: Shared Cache (Redis)** - Your Project ✅
```java
// All instances share same Redis
Instance 1: @CacheEvict("portfolios", key = "123")
           ↓
       Redis cache cleared
           ↓
Instance 2: Next request → Cache miss → Fresh data
Instance 3: Next request → Cache miss → Fresh data
```

**No cross-instance invalidation needed!**

**Solution 2: Cache Invalidation Events (if using local cache)**
```java
@Service
public class PortfolioService {
    
    @CacheEvict("portfolios")
    public Portfolio updatePortfolio(Portfolio p) {
        Portfolio updated = repository.save(p);
        
        // Publish cache invalidation event
        kafkaTemplate.send("cache-invalidation", 
            new CacheInvalidationEvent("portfolios", p.getId()));
        
        return updated;
    }
}

// Each instance listens
@KafkaListener(topics = "cache-invalidation")
public void handleCacheInvalidation(CacheInvalidationEvent event) {
    cacheManager.getCache(event.getCacheName()).evict(event.getKey());
}
```

**Solution 3: Redis Pub/Sub**
```java
// Instance 1: Evicts and publishes
@CacheEvict("portfolios", key = "#id")
public void updatePortfolio(String id, Portfolio p) {
    repository.save(p);
    redisTemplate.convertAndSend("cache:evict", 
        new EvictionMessage("portfolios", id));
}

// All instances subscribe
@Component
public class CacheEvictionListener {
    
    @RedisMessageListener(channel = "cache:evict")
    public void onMessage(EvictionMessage msg) {
        cacheManager.getCache(msg.cacheName).evict(msg.key);
    }
}
```

**Solution 4: Time-Based Invalidation (TTL)**
```java
// Simple but not immediate
@Cacheable(value = "portfolios", key = "#id")
// Stale data for max 10 minutes
public Portfolio getPortfolio(String id) { ... }
```

**Trade-offs:**

| Approach | Consistency | Complexity | Performance |
|----------|-------------|------------|-------------|
| Shared Redis | Eventual (immediate) | Low | High |
| Kafka Events | Eventual (seconds) | Medium | Medium |
| Redis Pub/Sub | Eventual (milliseconds) | Medium | High |
| TTL Only | Eventual (minutes) | Low | Highest |

**Your Project:**
Uses shared Redis - best approach for distributed caching!

**Interview Answer:** "My project uses shared Redis cache, so all instances automatically see evictions. If I were using local caches (like Caffeine), I'd publish cache invalidation events via Kafka or Redis Pub/Sub so all instances can evict their local copies."

## Document Database Questions

### Q18: When would you choose MongoDB over PostgreSQL?

**Expected Answer:**

**Choose MongoDB when:**

✅ **1. Schema Flexibility**
```javascript
// Portfolio schema can evolve without migrations
{
  "clientName": "John Doe",
  "holdings": [
    {"symbol": "AAPL", "quantity": 100}
  ]
  // Later, add new fields without ALTER TABLE
  "riskProfile": { "score": 75, "category": "moderate" }
}
```

✅ **2. Document Model Matches Domain**
```java
// Portfolio is naturally a document
@Document(collection = "portfolios")
public class Portfolio {
    private String id;
    private String clientName;
    private List<Holding> holdings;  // Embedded documents
    // Stored together, retrieved together
}
```

✅ **3. Horizontal Scaling (Sharding)**
```
MongoDB: Shard by portfolioId → Scales linearly
PostgreSQL: Vertical scaling (bigger server) → Limited
```

✅ **4. High Write Throughput**
- Millions of portfolio updates/day
- Log event streams
- Time-series data

**Choose PostgreSQL when:**

✅ **1. ACID Across Multiple Entities**
```sql
BEGIN;
UPDATE accounts SET balance = balance - 1000 WHERE id = 1;
UPDATE accounts SET balance = balance + 1000 WHERE id = 2;
COMMIT;  -- Both succeed or both fail
```

✅ **2. Complex Joins**
```sql
SELECT p.name, SUM(t.amount)
FROM portfolios p
JOIN transactions t ON p.id = t.portfolio_id
JOIN holdings h ON p.id = h.portfolio_id
WHERE t.date > '2024-01-01'
GROUP BY p.name;
```

✅ **3. Strong Consistency Required**
- Banking transactions
- Inventory management
- Anything where stale data is unacceptable

✅ **4. Mature Tooling**
- Better BI tools
- More DBAs familiar with it
- Established backup/recovery

**Your Project Choice:**
```java
// MongoDB fits because:
// 1. Portfolio is document-centric (client + holdings together)
// 2. Schema might evolve (new investment types)
// 3. Read-heavy workload (good fit for MongoDB)
// 4. No cross-portfolio transactions needed
```

**Interview Answer:** "I chose MongoDB because portfolios are naturally document-structured with embedded holdings. The schema can evolve as new investment types are added. For a banking app with transfers between accounts, I'd choose PostgreSQL for ACID guarantees."

---

### Q19: Explain MongoDB indexing. How do you know which indexes to create?

**Expected Answer:**

**Without Index:**
```javascript
db.portfolios.find({clientName: "John Doe"})
// Scans ALL documents (SLOW for large collections)
```

**With Index:**
```javascript
db.portfolios.createIndex({clientName: 1})
db.portfolios.find({clientName: "John Doe"})
// Uses index (FAST - O(log n))
```

**In Your Project:**
```java
@Document(collection = "portfolios")
@CompoundIndex(name = "client_risk_idx", 
               def = "{'clientName': 1, 'riskTolerance': 1}")
public class Portfolio {
    @Id
    private String id;  // Automatically indexed
    
    @Indexed(unique = true)
    private String clientName;
    
    private String riskTolerance;
}
```

**How to Identify Needed Indexes:**

**1. Analyze Queries**
```java
// This query pattern:
portfolioRepository.findByClientNameAndRiskTolerance(name, risk);

// Needs this index:
db.portfolios.createIndex({clientName: 1, riskTolerance: 1})
```

**2. Use Explain Plan**
```javascript
db.portfolios.find({clientName: "John Doe"}).explain("executionStats")

// Look for:
// - "COLLSCAN" = BAD (full collection scan)
// - "IXSCAN" = GOOD (index scan)
// - "executionTimeMillis" = response time
```

**3. Monitor Slow Queries**
```javascript
// Enable slow query log
db.setProfilingLevel(1, {slowms: 100})  // Log queries > 100ms

// Review slow queries
db.system.profile.find({millis: {$gt: 100}})
```

**Index Types:**

**Single Field:**
```javascript
db.portfolios.createIndex({clientName: 1})  // Ascending
```

**Compound Index:**
```javascript
db.portfolios.createIndex({clientName: 1, totalValue: -1})
// Good for: WHERE clientName = X ORDER BY totalValue DESC
```

**Interview Answer:** "Create indexes on fields used in WHERE, JOIN, and ORDER BY clauses. Use explain() to verify index usage. Monitor slow queries in production. Be careful not to over-index - each index slows writes."

---

### Q20: What's eventual consistency? Give an example from your project.

**Expected Answer:**

**Eventual Consistency** - System becomes consistent over time, but may be temporarily inconsistent.

**Example from Your Project:**

**Scenario: User executes a stock purchase**

```
Time 0:00:00
User clicks "Buy 100 AAPL"
   ↓
Time 0:00:01
Transaction Service: Creates transaction (PENDING)
Saves to MongoDB: transaction_db
   ↓
Time 0:00:02
Transaction Service: Publishes TRANSACTION_CREATED event to Kafka
   ↓
Time 0:00:03
Transaction Service: Processes transaction, updates status to COMPLETED
   ↓
Time 0:00:04
Transaction Service: Publishes TRANSACTION_COMPLETED event
   ↓
Time 0:00:05
Portfolio Service: Kafka consumer receives event
   ↓
Time 0:00:06
Portfolio Service: Updates holdings in portfolio_db
Portfolio Service: Evicts Redis cache
   ↓
Time 0:00:07
System is now CONSISTENT
```

**Inconsistency Window:**
Between 0:00:02 and 0:00:06, if user queries portfolio:
- Transaction shows COMPLETED
- But holdings NOT updated yet
- **Temporarily inconsistent!**

**Why This is Acceptable:**
- 4-5 second delay is fine for portfolio dashboard
- Benefits: Services decoupled, can scale independently
- Trade-off: Immediate consistency vs system resilience

**How to Mitigate:**

**1. Idempotent Event Processing**
```java
@KafkaListener(topics = "transaction-events")
public void handleTransactionEvent(TransactionEvent event) {
    // Check if already processed
    if (processedEvents.exists(event.getEventId())) {
        return;  // Skip duplicate
    }
    updateHoldings(event);
    processedEvents.save(event.getEventId());
}
```

**2. Read Your Own Writes**
```java
@PostMapping("/transactions")
public Transaction createTransaction(@RequestBody TransactionRequest req) {
    Transaction txn = service.createTransaction(req);
    
    // Return updated portfolio immediately (not from cache)
    Portfolio updated = service.getPortfolioUncached(req.getPortfolioId());
    
    return TransactionResponse.builder()
        .transaction(txn)
        .updatedPortfolio(updated)  // Consistent for this user
        .build();
}
```

**3. Version Numbers**
```java
@Document
public class Portfolio {
    private Long version;  // Increment on each update
    private List<Holding> holdings;
}

// Client can detect stale data
if (portfolio.getVersion() < expectedVersion) {
    // Refresh from source
}
```

**Strong Consistency Alternative:**
```java
// Two-phase commit (slow, complex)
@Transactional
public void processTransaction(Transaction txn) {
    // 1. Lock transaction record
    // 2. Lock portfolio record
    // 3. Update both atomically
    // 4. Commit
    // Problem: Distributed locks, slower, harder to scale
}
```

**Interview Answer:** "In my project, when a transaction completes, there's a 5-second window where the transaction is marked complete but portfolio holdings aren't updated yet. This is acceptable for a dashboard but wouldn't work for real-time trading. I ensure idempotent event processing to handle duplicate events safely."

## Testing Questions (Mock/MockServer)

### Q21: What's the difference between unit tests and integration tests?

**Expected Answer:**

| Aspect | Unit Test | Integration Test |
|--------|-----------|------------------|
| **Scope** | Single class/method | Multiple components |
| **Dependencies** | Mocked | Real (or test containers) |
| **Speed** | Fast (milliseconds) | Slower (seconds) |
| **Database** | No real DB | Real database (test instance) |
| **Purpose** | Test logic in isolation | Test components working together |
| **Quantity** | Many (100s) | Fewer (10s) |

**Unit Test Example:**
```java
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    
    @Mock
    private PortfolioRepository repository;
    
    @InjectMocks
    private PortfolioService service;
    
    @Test
    void shouldGetPortfolioById() {
        Portfolio expected = Portfolio.builder().id("123").build();
        when(repository.findById("123")).thenReturn(Optional.of(expected));
        
        Portfolio actual = service.getPortfolio("123");
        
        verify(repository, times(1)).findById("123");
    }
}
```

**Integration Test Example:**
```java
@SpringBootTest
@Testcontainers
class PortfolioServiceIntegrationTest {
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");
    
    @Autowired
    private PortfolioService service;
    
    @Test
    void shouldSaveAndRetrievePortfolio() {
        // Uses REAL MongoDB (in Docker container)
        Portfolio saved = service.createPortfolio(portfolio);
        Portfolio retrieved = service.getPortfolio(saved.getId());
        
        assertEquals("John Doe", retrieved.getClientName());
    }
}
```

**Interview Answer:** "Unit tests mock all dependencies to test business logic in isolation. Integration tests use real dependencies like databases to verify components work together. I write many fast unit tests and fewer integration tests for critical paths."

---

### Q22: How do you mock external dependencies in unit tests?

**Expected Answer:**

**Using Mockito:**

```java
@Mock
private PortfolioRepository repository;

@Mock
private KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

@Test
void shouldPublishEventOnCreate() {
    when(repository.save(any())).thenReturn(portfolio);
    
    service.createPortfolio(request);
    
    verify(kafkaTemplate, times(1))
        .send(eq("portfolio-events"), any(PortfolioEvent.class));
}

@Test
void shouldCaptureEventData() {
    ArgumentCaptor<PortfolioEvent> captor = 
        ArgumentCaptor.forClass(PortfolioEvent.class);
    
    service.createPortfolio(request);
    
    verify(kafkaTemplate).send(anyString(), captor.capture());
    assertEquals("PORTFOLIO_CREATED", captor.getValue().getEventType());
}
```

**Interview Answer:** "I use Mockito to mock repositories, Kafka templates, and external services. I verify interactions with `verify()` and capture arguments with `ArgumentCaptor` to assert on the data being sent."

---

### Q23: What's test coverage and what's a good percentage?

**Expected Answer:**

**Good Percentages:** 70-80% is reasonable. Critical code (payments): 95%+

**Focus On:** Business logic, edge cases, error handling
**Skip:** Getters/setters, generated code

**Interview Answer:** "I aim for 70-80% coverage, focusing on business logic and edge cases. 100% wastes time testing trivial code."

---

### Q24: Explain test pyramid.

**Expected Answer:**

```
       /\
      /E2E\       Slow (minutes)
     /------\     
    /Integration\ Medium (seconds)
   /------------\
  /  Unit Tests  \ Fast (milliseconds)
 /________________\
```

**Interview Answer:** "Test pyramid means mostly fast unit tests, fewer integration tests, minimal E2E tests. My project has 100+ unit tests, 20-30 integration tests, 5-10 API tests."

## DevOps/CI-CD Questions

### Q25: What's your Docker build strategy?

**Expected Answer:**

**Multi-Stage Build:**
```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
RUN mvn clean package

# Runtime stage (smaller)
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/target/*.jar app.jar
```

**Benefits:** Smaller images, layer caching, faster builds

**Interview Answer:** "I use multi-stage builds: Maven for compilation, JRE for runtime. This keeps images small and leverages caching."

---

### Q26: How would you set up CI/CD for this project?

**Expected Answer:**

**GitHub Actions Workflow:**
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
      - name: Run tests
        run: mvn test
      - name: Upload coverage
        run: mvn jacoco:report
  
  build:
    needs: test
    steps:
      - name: Build Docker images
        run: docker-compose build
      - name: Push to registry
        run: docker push myregistry/portfolio-service:${{ github.sha }}
  
  deploy:
    needs: build
    steps:
      - name: Deploy to staging
        run: kubectl apply -f k8s/
```

**Stages:**
1. **Test**: Run unit + integration tests
2. **Build**: Create Docker images
3. **Push**: To container registry
4. **Deploy**: To Kubernetes/ECS

**Interview Answer:** "I'd use GitHub Actions: test → build Docker images → push to registry → deploy to Kubernetes. Each PR runs tests, merges to main deploy to staging, manual approval for production."

---

### Q27: Blue-green vs canary deployment - explain both.

**Expected Answer:**

**Blue-Green Deployment:**
```
Blue (current v1.0)  ← 100% traffic
Green (new v2.0)     ← 0% traffic

Test Green → Switch traffic → Green 100%, Blue 0%

If issue: Instant rollback to Blue
```

**Canary Deployment:**
```
v1.0 ← 90% traffic
v2.0 ← 10% traffic (canary)

Monitor metrics → If good, increase to 50%
                → If good, increase to 100%
                → If bad, rollback to 0%
```

**Interview Answer:** "Blue-green is instant switch between versions, easy rollback. Canary gradually increases traffic to new version (10% → 50% → 100%), allowing validation with real users before full rollout."

## Quick Prep Tips

### Files to Know Cold

**1. PortfolioService.java**
- Caching with `@Cacheable` and `@CacheEvict`
- Event publishing after updates
- Business logic for portfolio operations

**2. TransactionService.java**
- State machine implementation
- Transaction validation
- Event-driven workflow

**3. application.yml**
- MongoDB configuration
- Kafka settings
- Redis caching config
- Port numbers

**4. docker-compose.yml**
- Service orchestration
- Network configuration
- Port mappings

### Practice Out Loud

**"Walk me through a transaction flow"**
1. User posts to /api/transactions
2. Gateway routes to Transaction Service
3. Service validates and creates transaction (PENDING)
4. Publishes CREATED event to Kafka
5. Processes transaction → COMPLETED
6. Publishes COMPLETED event
7. Portfolio Service consumes event, updates holdings
8. Notification Service sends email

**"Explain your caching strategy"**
- Redis shared cache across instances
- 10-minute TTL for portfolios
- Cache on read (`@Cacheable`)
- Evict on update (`@CacheEvict`)
- Trade-off: Staleness vs performance

**"How would you debug slow API responses?"**
1. Check Spring Actuator metrics
2. MongoDB slow query log
3. Redis hit/miss ratio
4. Kafka consumer lag
5. Add distributed tracing (Sleuth/Zipkin)

### Numbers to Remember

- **Ports:** 8080 (gateway), 8081-8083 (services), 27017 (MongoDB), 6379 (Redis), 9092 (Kafka)
- **Cache TTL:** 10 minutes (600,000 ms)
- **Services:** 4 microservices
- **Databases:** 2 MongoDB instances (portfolio_db, transaction_db)
- **Cache performance:** 2ms (cache hit) vs 50ms (DB query) = 25x faster

### Code Snippets to Reference

**Dependency Injection:**
```java
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository repository;
    private final KafkaTemplate<String, Event> kafkaTemplate;
}
```

**Caching:**
```java
@Cacheable(value = "portfolios", key = "#id")
public Portfolio getPortfolio(String id) { ... }

@CacheEvict(value = "portfolios", key = "#id")
public Portfolio updatePortfolio(String id, Portfolio p) { ... }
```

**Kafka Producer:**
```java
kafkaTemplate.send("portfolio-events", portfolioId, event);
```

**Kafka Consumer:**
```java
@KafkaListener(topics = "transaction-events", groupId = "portfolio-group")
public void handleEvent(TransactionEvent event) { ... }
```

### Questions to Ask Interviewer

**Technical:**
- "What event-driven patterns does your team use?"
- "How do you handle distributed tracing and logging?"
- "What's your deployment strategy - Kubernetes, ECS, or traditional?"

**Team:**
- "What does a typical sprint look like?"
- "How do you balance feature work vs technical debt?"
- "What learning opportunities exist for engineers?"

**Role-Specific:**
- "What are the biggest technical challenges in the next 6 months?"
- "How do teams collaborate across Asia and Australia time zones?"
- "What does success look like in the first 90 days?"

### Final Checklist

**Day Before Interview:**
- [ ] Run demo end-to-end 3 times
- [ ] Review this document
- [ ] Prepare questions for interviewer
- [ ] Get good sleep

**Day of Interview:**
- [ ] Start Docker services 30 min early
- [ ] Test all endpoints with curl
- [ ] Have code open in VS Code
- [ ] Have QUICK-REFERENCE.md ready
- [ ] Be honest, thoughtful, and curious

**Remember:** You don't need to know everything. Demonstrating problem-solving, honesty about gaps, and willingness to learn is more valuable than pretending to have all the answers.

Good luck! 🚀
