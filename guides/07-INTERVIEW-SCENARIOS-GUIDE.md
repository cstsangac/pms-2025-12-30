# Interview Scenarios & Preparation Guide

## Table of Contents
1. [System Design Questions](#system-design-questions)
2. [Technology Choices](#technology-choices)
3. [Scalability Questions](#scalability-questions)
4. [Trade-offs Discussion](#trade-offs-discussion)
5. [Code Walkthrough](#code-walkthrough)
6. [Architecture Diagrams](#architecture-diagrams)
7. [Common Pitfalls](#common-pitfalls)
8. [Demo Script](#demo-script)

---

## System Design Questions

### Question 1: "Explain the architecture of your portfolio management system"

**Answer Framework:**

**1. High-Level Overview (30 seconds)**

"I designed a microservices-based wealth management platform with 4 core services:
- **Portfolio Service** manages client portfolios and holdings
- **Transaction Service** processes buy/sell transactions
- **Notification Service** sends alerts to clients
- **API Gateway** routes requests and provides circuit breaking

The services communicate asynchronously via Apache Kafka for loose coupling and scalability."

**2. Architecture Diagram (Draw while talking)**

```
┌──────────┐
│ Frontend │
│ (React)  │
└────┬─────┘
     │
     ▼
┌─────────────────┐
│  API Gateway    │ ← Circuit Breaker, Rate Limiting
│  (Port 8080)    │
└────┬────────────┘
     │
     ├──────────────────┬───────────────────┬────────────────┐
     ▼                  ▼                   ▼                ▼
┌──────────┐    ┌──────────────┐   ┌───────────────┐  ┌──────────────┐
│Portfolio │    │ Transaction  │   │ Notification  │  │   (Future    │
│ Service  │    │   Service    │   │   Service     │  │  Services)   │
│ (8081)   │    │   (8082)     │   │   (8083)      │  └──────────────┘
└────┬─────┘    └──────┬───────┘   └───────┬───────┘
     │                 │                   │
     ├────────┬────────┼──────────┬────────┤
     ▼        ▼        ▼          ▼        ▼
┌─────────┐ ┌──────┐ ┌─────────┐ ┌──────────────────┐
│MongoDB  │ │Redis │ │MongoDB  │ │      Kafka       │
│portfolio│ │Cache │ │ txn_db  │ │ (Event Streaming)│
└─────────┘ └──────┘ └─────────┘ └──────────────────┘
```

**3. Data Flow Example (1 minute)**

"Let me walk through a BUY transaction:
1. User clicks 'Buy 100 AAPL @ $150' in React frontend
2. API Gateway routes to Transaction Service (with circuit breaker)
3. Transaction Service creates transaction in PENDING state, saves to MongoDB
4. Publishes TRANSACTION_CREATED event to Kafka
5. Processes transaction → COMPLETED state
6. Publishes TRANSACTION_COMPLETED event
7. Portfolio Service listens to Kafka, updates holdings (adds 100 AAPL)
8. Notification Service listens to Kafka, sends email to client
9. Frontend polls API Gateway for updated portfolio (cached in Redis)"

**4. Key Design Decisions (30 seconds)**

"I chose:
- **Microservices** for independent scaling and deployment
- **Event-driven architecture** to decouple services
- **MongoDB** for flexible schema (portfolios have varying holdings)
- **Redis** for caching frequently accessed portfolios (10-minute TTL)
- **Kafka** for reliable, ordered event delivery"

---

### Question 2: "How does your system handle data consistency?"

**Answer:**

**1. Acknowledge the Challenge**

"Great question. In a distributed system with microservices, maintaining consistency is challenging because we don't have ACID transactions across services."

**2. Explain Your Approach**

"I use **eventual consistency** with an event-driven architecture:

**Example Scenario:**
- Transaction Service updates transaction → COMPLETED
- Publishes event to Kafka
- Portfolio Service eventually processes event and updates holdings

**Ensuring Consistency:**

1. **Transactional Outbox Pattern (could implement):**
   ```java
   @Transactional
   public void processTransaction(String id) {
       // Both operations in same DB transaction
       transaction.setStatus(COMPLETED);
       transactionRepository.save(transaction);
       
       // Outbox table stores event
       outboxRepository.save(new OutboxEvent(transaction));
       
       // Separate process publishes from outbox → Kafka
   }
   ```

2. **Idempotent Event Handling:**
   ```java
   @KafkaListener(topics = "transaction-events")
   public void handleEvent(TransactionEvent event) {
       // Check if already processed
       if (processedEvents.contains(event.getId())) {
           return; // Skip duplicate
       }
       
       updatePortfolio(event);
       processedEvents.add(event.getId());
   }
   ```

3. **Compensating Transactions:**
   - If portfolio update fails, create reverse transaction
   - BUY failed → create compensating SELL
   - Maintains audit trail"

**3. Trade-off Discussion**

"This approach favors **availability** over **strong consistency** (AP in CAP theorem). It's acceptable for wealth management because:
- ✅ Portfolio updates within seconds (good enough)
- ✅ System stays available even if one service is down
- ❌ Wouldn't work for banking transactions needing immediate consistency

For stronger consistency, I could use:
- Saga pattern with orchestration
- Two-phase commit (but reduces availability)
- Distributed transactions (complex, performance impact)"

---

### Question 3: "What happens if Kafka goes down?"

**Answer:**

**1. Immediate Impact**

"If Kafka is unavailable:
- Transaction Service can't publish events → Transactions still created in MongoDB
- Portfolio Service can't receive events → Holdings NOT updated
- Notification Service can't send alerts

System is **partially degraded** but not completely down."

**2. Mitigation Strategies**

**Strategy 1: Circuit Breaker on Kafka Publishing**
```java
@CircuitBreaker(name = "kafka", fallbackMethod = "kafkaFallback")
public void publishEvent(TransactionEvent event) {
    kafkaTemplate.send("transaction-events", event);
}

public void kafkaFallback(TransactionEvent event, Exception e) {
    // Store in database outbox table
    outboxRepository.save(event);
    log.warn("Kafka down, event queued in outbox: {}", event.getId());
}
```

**Strategy 2: Retry with Exponential Backoff**
```java
@Retryable(
    value = {KafkaException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public void publishEvent(TransactionEvent event) {
    kafkaTemplate.send("transaction-events", event);
}
```

**Strategy 3: Background Reconciliation**
```java
@Scheduled(fixedRate = 60000)  // Every minute
public void reconcileEvents() {
    // Find events in outbox not yet published
    List<OutboxEvent> pending = outboxRepository.findUnpublished();
    
    for (OutboxEvent event : pending) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayload());
            event.setPublished(true);
            outboxRepository.save(event);
        } catch (Exception e) {
            // Will retry next minute
        }
    }
}
```

**3. Kafka High Availability**

"In production, I'd run Kafka cluster:
- 3+ broker nodes (fault tolerance)
- Replication factor = 3
- Min in-sync replicas = 2
- Ensures data survives 1-2 broker failures"

---

### Question 4: "How would you scale this system to 1 million users?"

**Answer:**

**1. Current Bottlenecks**

"At 1M users, we'd face:
- Database overload (MongoDB handles ~10K ops/sec per node)
- Memory constraints (Redis caching 1M portfolios)
- Network saturation
- Service instance limits"

**2. Scaling Strategy**

**Horizontal Scaling (Stateless Services):**
```
Current:
1 instance × Portfolio Service
1 instance × Transaction Service
1 instance × Notification Service

Scaled:
10 instances × Portfolio Service (load balanced)
5 instances × Transaction Service
3 instances × Notification Service
```

**Database Scaling:**

**MongoDB Sharding:**
```javascript
// Shard by accountNumber
sh.shardCollection("portfolio_db.portfolios", 
    { accountNumber: "hashed" })

// Distributes portfolios across 3+ shards
Shard 1: Accounts A-G (333K portfolios)
Shard 2: Accounts H-P (333K portfolios)
Shard 3: Accounts Q-Z (334K portfolios)
```

**Redis Scaling:**
```yaml
# Redis Cluster (6 nodes: 3 masters + 3 replicas)
# Each master handles ~333K portfolios
# Replicas for high availability

# OR: Cache only "hot" data
- Most active 10% of portfolios (100K)
- Reduces memory from 10GB to 1GB
```

**Kafka Partitioning:**
```java
// Increase partitions for parallel processing
transaction-events: 10 partitions
portfolio-events: 10 partitions

// 10 consumer instances can process in parallel
```

**3. Additional Optimizations**

**CDN for Frontend:**
```
React app served from CloudFront/CDN
- Reduces API Gateway load
- Faster global access
```

**Read Replicas:**
```
MongoDB: 1 primary + 2 read replicas
- Writes → Primary
- Reads → Replicas (distribute load)
```

**Caching Strategy:**
```java
@Cacheable(value = "portfolios", key = "#id", unless = "#result == null")
public Portfolio getPortfolio(String id) {
    // Cache hit: 1ms
    // Cache miss: 50ms (MongoDB query)
    // 98% cache hit rate → 50x faster
}
```

**4. Cost Estimation**

"For 1M users:
- 10 service instances: $500/month
- MongoDB cluster (3 shards): $2000/month
- Redis cluster: $300/month
- Kafka cluster: $500/month
- Total: ~$3300/month

**vs building from scratch: 6-12 months development time**"

## Technology Choices

### Question: "Why did you choose Spring Boot over other frameworks?"

**Answer:**

**1. Rapid Development**

"Spring Boot provides:
- **Convention over configuration** → Minimal boilerplate
- **Auto-configuration** → MongoDB, Kafka, Redis work out-of-box
- **Embedded server** → No Tomcat installation needed
- **Production-ready features** → Health checks, metrics via Actuator

**Example:**
```java
// Traditional Spring: 50+ lines of XML config
// Spring Boot: 3 lines
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/portfolio_db
// MongoClient, MongoTemplate auto-configured! ✨
```

**2. Industry Standard**

"Spring Boot is the most widely used Java framework:
- 70%+ of Java microservices use Spring Boot
- Huge community → StackOverflow answers, tutorials
- Well-tested in production → Netflix, Alibaba, Amazon"

**3. Interview Relevance**

"Since this is for a wealth management role, Spring Boot demonstrates:
- ✅ Enterprise Java skills
- ✅ Microservices architecture
- ✅ Production-ready code (not just tutorials)
- ✅ Industry best practices"

**4. Alternatives Considered**

| Framework | Pros | Cons | Why Not? |
|-----------|------|------|----------|
| **Quarkus** | Faster startup, native compilation | Smaller ecosystem | Less familiar to interviewers |
| **Micronaut** | Low memory, fast | Newer, less mature | Risk for demo project |
| **Jakarta EE** | Spec-compliant | Verbose, heavy | Too much boilerplate |
| **Vert.x** | Reactive, fast | Steep learning curve | Overkill for this demo |

---

### Question: "Why MongoDB instead of PostgreSQL?"

**Answer:**

**1. Schema Flexibility**

"Portfolios have varying structures:

```javascript
// Portfolio 1: Individual investor (simple)
{
  accountNumber: "ACC001",
  ownerName: "John Doe",
  holdings: [
    { symbol: "AAPL", quantity: 100 }
  ]
}

// Portfolio 2: Institutional investor (complex)
{
  accountNumber: "ACC002",
  ownerName: "Hedge Fund XYZ",
  holdings: [
    { symbol: "AAPL", quantity: 10000, costBasis: 145.50, purchaseDate: "..." },
    { symbol: "GOOGL", quantity: 5000, lotDetails: [...] }
  ],
  strategies: ["momentum", "value"],
  riskMetrics: { sharpeRatio: 1.5, beta: 0.9 }
}
```

**With MongoDB:**
- ✅ No schema migration needed
- ✅ Can add fields without ALTER TABLE
- ✅ Flexible for future enhancements

**With PostgreSQL:**
- ❌ Need migration for new fields
- ❌ JSON columns less performant
- ❌ Schema changes require downtime"

**2. Document Model Matches Domain**

"A portfolio is naturally a document:
- Portfolio contains array of holdings
- One query returns complete portfolio
- No JOINs needed (faster reads)

**MongoDB:**
```java
Portfolio portfolio = portfolioRepository.findById("PORT001");
// Holdings embedded → 1 query
```

**PostgreSQL:**
```sql
-- Need 2 queries
SELECT * FROM portfolios WHERE id = 'PORT001';
SELECT * FROM holdings WHERE portfolio_id = 'PORT001';
-- Or expensive JOIN
```

**3. Horizontal Scaling**

"MongoDB has built-in sharding:
```javascript
sh.shardCollection("portfolio_db.portfolios", 
    { accountNumber: "hashed" })
```

PostgreSQL requires more effort (Citus extension, manual partitioning)"

**4. When I'd Choose PostgreSQL**

"For this use case, I'd choose PostgreSQL if:
- Need complex JOINs (e.g., reporting across multiple tables)
- ACID transactions critical (banking transactions)
- Strong consistency required
- Mature tooling needed (BI tools, reporting)

**But for portfolio management:**
- Holdings data is self-contained (document model fits naturally)
- Eventual consistency acceptable
- Balanced read/write workload (MongoDB handles both well)
- Schema evolution expected (different portfolio types)
- Better write performance than PostgreSQL (less overhead)

→ MongoDB is better fit"

---

### Question: "Why Kafka over RabbitMQ or AWS SNS/SQS?"

**Answer:**

**1. Event Sourcing & Audit Trail**

"Kafka persists events (default 7 days):

```
Transaction Events in Kafka:
2025-12-30 10:00:00 → TRANSACTION_CREATED (id=TXN001)
2025-12-30 10:00:01 → TRANSACTION_PROCESSING (id=TXN001)
2025-12-30 10:00:02 → TRANSACTION_COMPLETED (id=TXN001)

// Can replay events for:
- Debugging: "What happened to TXN001?"
- Audit: "Show all transactions for last month"
- Recovery: Rebuild portfolio state from events
```

**RabbitMQ:** Messages deleted after consumption (ephemeral)  
**SNS/SQS:** Limited retention (14 days max)"

**2. Multiple Consumers**

"Kafka allows multiple consumer groups:

```
Kafka Topic: transaction-events
│
├─ Consumer Group: portfolio-service-group
│  └─ Updates holdings
│
├─ Consumer Group: notification-service-group
│  └─ Sends emails
│
└─ Consumer Group: analytics-service-group (future)
   └─ Generates reports

// All consume same events independently
```

**RabbitMQ:** Needs topic exchanges (more config)  
**SQS:** Each consumer needs separate queue (fan-out complexity)"

**3. Ordering Guarantees**

"Kafka guarantees order within partition:

```java
// Publish with transactionId as key
kafkaTemplate.send("transaction-events", 
    transaction.getId(),  // Key (partition by this)
    event);

// All events for TXN001 go to same partition → ordered
```

**Critical for transactions:**
```
CREATED → PROCESSING → COMPLETED  ✅ Correct order
CREATED → COMPLETED → PROCESSING  ❌ Wrong order (breaks state machine)
```

**4. Throughput**

"Kafka handles:
- 1M+ messages/second per broker
- 10TB+ data per cluster

Perfect for:
- High-frequency trading systems
- Real-time analytics
- Event sourcing at scale"

**5. Comparison Table**

| Feature | Kafka | RabbitMQ | AWS SQS |
|---------|-------|----------|---------|
| **Persistence** | Yes (days/weeks) | No (ephemeral) | Limited (14 days) |
| **Throughput** | Very High | Medium | Medium |
| **Ordering** | Per partition | Per queue | FIFO queues only |
| **Replay** | Yes | No | No |
| **Complexity** | Higher | Lower | Lowest |
| **Use Case** | Event streaming | Task queues | Serverless apps |

**6. When I'd Choose Alternatives**

**RabbitMQ:** 
- Simple request/reply patterns
- Priority queues needed
- Lower throughput requirements

**SQS:**
- AWS-native stack
- Serverless architecture (Lambda)
- No infrastructure management

**For this project:**
- Event sourcing important (audit trail)
- Multiple consumers needed
- Scalability matters
→ Kafka is best choice"

---

### Question: "Why Redis for caching instead of application-level caching (e.g., Caffeine)?"

**Answer:**

**1. Shared Cache Across Instances**

"With multiple service instances:

**Application-level cache (Caffeine):**
```
Portfolio Service Instance 1: Cache(PORT001) = stale
Portfolio Service Instance 2: Cache(PORT001) = fresh
Portfolio Service Instance 3: Cache(PORT001) = missing

// Inconsistent cache → user sees different data
```

**Redis (centralized):**
```
All Instances → Redis Cache → Single source of truth
// Consistent cache across all instances
```

**2. Cache Eviction Coordination**

"When portfolio updates:

```java
@CacheEvict(value = "portfolios", key = "#id")
public Portfolio updatePortfolio(String id, UpdateRequest request) {
    Portfolio updated = portfolioRepository.save(updated);
    // Redis cache evicted
    // All instances get fresh data on next read
    return updated;
}
```

With Caffeine: Need custom pub/sub to notify all instances"

**3. Persistence & Durability**

"Redis can persist cache to disk:
- Cache survives service restarts
- No cold start (empty cache) on deployment

Caffeine: Cache lost on restart"

**4. When I'd Use Caffeine**

"Caffeine is better for:
- Single instance deployments
- Read-only data (never changes)
- Very low latency needs (<1ms)
- No network overhead

**For this project:**
- Multiple instances expected (scalability)
- Cache eviction coordination needed
- Moderate latency acceptable (2-5ms)
→ Redis is better choice"

---

### Question: "Why not use a monolith instead of microservices?"

**Answer:**

**1. Honest Assessment**

"For this demo project, a **monolith would actually be simpler**. I chose microservices to demonstrate:
- Distributed system design skills
- Event-driven architecture
- Microservices best practices (relevant for enterprise roles)

**But let me explain when each makes sense:**

**2. Microservices Benefits (This Project)**

✅ **Independent Deployment:**
```
Update Transaction Service → Deploy only that service
Portfolio Service keeps running (no downtime)
```

✅ **Technology Flexibility:**
```
Portfolio Service: Java + MongoDB
Transaction Service: Java + MongoDB
Future Analytics Service: Python + PostgreSQL (different stack!)
```

✅ **Team Scalability:**
```
Team A: Portfolio Service (3 devs)
Team B: Transaction Service (2 devs)
Team C: Notification Service (2 devs)
// Teams work independently
```

✅ **Fault Isolation:**
```
Notification Service crashes → Portfolio & Transaction still work
```

**3. Monolith Benefits**

✅ **Simpler Deployment:** One JAR, one database  
✅ **No Network Calls:** Faster (no Kafka latency)  
✅ **Easier Debugging:** Single codebase, single process  
✅ **ACID Transactions:** Database transactions across all operations  
✅ **Lower Complexity:** No distributed tracing, no service discovery

**4. When I'd Choose Each**

**Monolith:**
- Team size: 1-5 developers
- Low traffic: <10K requests/minute
- Simple domain: CRUD operations
- Time to market: Need MVP fast
- **Example:** Internal admin tool, small SaaS app

**Microservices:**
- Team size: 10+ developers
- High traffic: >100K requests/minute
- Complex domain: Multiple bounded contexts
- Independent scaling: Different load patterns
- **Example:** E-commerce, social media, wealth management platform

**5. Hybrid Approach (Pragmatic)**

"I'd actually start with a **modular monolith:**

```
Monolith JAR
├── Portfolio Module (separate package)
├── Transaction Module
├── Notification Module
└── Shared Kernel

// Later split into microservices if needed
// Modules → Services (clean boundaries already exist)
```

**For this demo:**
Microservices showcase more advanced skills for interview""

## Scalability Questions

### Question: "How would you handle 10,000 concurrent users?"

**Answer:**

**1. Current Capacity**

"Let me first estimate current capacity:

**Single Instance Limits:**
```
Portfolio Service (1 instance):
- Tomcat thread pool: 200 threads
- Average request time: 100ms
- Max throughput: 200 / 0.1 = 2,000 requests/second
- Concurrent users: ~500-1000 users

MongoDB:
- Single node: ~10,000 reads/second
- With indexes: ~5,000 writes/second

Redis:
- Single node: ~100,000 ops/second (not a bottleneck)
```

**2. Scaling to 10K Concurrent Users**

**Horizontal Scaling (Stateless Services):**

```yaml
# Docker Compose / Kubernetes
portfolio-service:
  replicas: 5  # 5 × 2,000 req/s = 10,000 req/s
  
transaction-service:
  replicas: 3  # Fewer transactions than reads
  
api-gateway:
  replicas: 2  # Load balanced (Nginx/AWS ALB)
```

**Load Balancer Configuration:**

```nginx
upstream portfolio_service {
    least_conn;  # Route to least busy instance
    server portfolio-1:8081;
    server portfolio-2:8081;
    server portfolio-3:8081;
    server portfolio-4:8081;
    server portfolio-5:8081;
}

server {
    listen 80;
    location /api/portfolios {
        proxy_pass http://portfolio_service;
        proxy_next_upstream error timeout;
    }
}
```

**3. Database Scaling**

**Read Replicas:**
```
MongoDB Replica Set:
├── Primary (Writes)
└── 2× Secondaries (Reads)

@ReadPreference("secondary")
public List<Portfolio> getAllPortfolios() {
    // Reads from replicas (distribute load)
}
```

**Connection Pooling:**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://primary,replica1,replica2/portfolio_db?replicaSet=rs0
      connection-pool:
        min-size: 10
        max-size: 50
```

**4. Caching Strategy**

**Redis Cache Layer:**
```java
@Cacheable(value = "portfolios", key = "#id")
public Portfolio getPortfolio(String id) {
    // Cache hit: 1-2ms (Redis)
    // Cache miss: 50ms (MongoDB)
    // 95% hit rate → 25x speedup
}

// Cache warming on startup
@PostConstruct
public void warmCache() {
    List<Portfolio> topPortfolios = getTop1000Portfolios();
    topPortfolios.forEach(p -> cache.put(p.getId(), p));
}
```

**5. Async Processing**

**Non-blocking I/O:**
```java
@Async
public CompletableFuture<Portfolio> getPortfolioAsync(String id) {
    Portfolio portfolio = portfolioRepository.findById(id).orElseThrow();
    return CompletableFuture.completedFuture(portfolio);
}

// 200 threads can handle 2000+ concurrent requests
```

**6. Rate Limiting**

**Protect from traffic spikes:**
```java
@RateLimiter(name = "portfolioApi", fallbackMethod = "rateLimitFallback")
public Portfolio getPortfolio(String id) {
    return portfolioService.getPortfolio(id);
}

public Portfolio rateLimitFallback(String id, Throwable t) {
    throw new TooManyRequestsException("Rate limit exceeded, try again later");
}
```

**7. Monitoring**

"I'd track these metrics:

```java
// Request rate
meterRegistry.counter("api.requests", "endpoint", "/portfolios");

// Response time
meterRegistry.timer("api.response.time");

// Error rate
meterRegistry.counter("api.errors", "type", "500");

// Active users
meterRegistry.gauge("users.active", activeUsers);

// Alert if:
- Response time > 500ms
- Error rate > 1%
- CPU > 80%
```

---

### Question: "What if MongoDB becomes the bottleneck?"

**Answer:**

**1. Identify the Bottleneck**

```bash
# MongoDB profiler
db.setProfilingLevel(2)  // Log all queries

# Find slow queries
db.system.profile.find({ millis: { $gt: 100 } }).sort({ millis: -1 })

# Example output:
{
  "op": "query",
  "ns": "portfolio_db.portfolios",
  "query": { "ownerName": "John Doe" },
  "millis": 523,  // 523ms! 😱
  "planSummary": "COLLSCAN"  // Full table scan (no index)
}
```

**2. Fix: Add Indexes**

```javascript
// Create index on ownerName
db.portfolios.createIndex({ ownerName: 1 })

// Now query takes 3ms instead of 523ms (174x faster!)
```

**All indexes in our schema:**
```javascript
// Portfolio collection
db.portfolios.createIndex({ accountNumber: 1 }, { unique: true })
db.portfolios.createIndex({ ownerName: 1 })
db.portfolios.createIndex({ totalValue: -1 })  // For sorting by value

// Transaction collection
db.transactions.createIndex({ portfolioId: 1, transactionDate: -1 })
db.transactions.createIndex({ status: 1 })
db.transactions.createIndex({ accountNumber: 1 })
```

**3. If Still Slow: Sharding**

```javascript
// Enable sharding
sh.enableSharding("portfolio_db")

// Shard portfolios by accountNumber (hashed)
sh.shardCollection("portfolio_db.portfolios", 
    { accountNumber: "hashed" })

// MongoDB distributes across shards:
Shard 1: 33% of portfolios
Shard 2: 33% of portfolios
Shard 3: 34% of portfolios

// Each shard handles ~3,333 req/s
// Total: 10,000 req/s
```

**4. Read Replicas**

```
Primary (Writes)
├── Replica 1 (Reads)
├── Replica 2 (Reads)
└── Replica 3 (Reads)

// Writes: 1 node
// Reads: 3 nodes (3x capacity)
```

**5. Caching (Redis)**

"Already implemented:

```java
@Cacheable(value = "portfolios", key = "#id")
public Portfolio getPortfolio(String id) {
    // Only queries MongoDB on cache miss
}

// Cache hit rate: 95%
// Reduces MongoDB load by 20x
```

**6. Denormalization**

"For read-heavy fields:

```javascript
// Instead of calculating totalValue every time
{
  accountNumber: "ACC001",
  holdings: [...],
  totalValue: 150000,  // Pre-calculated (denormalized)
  lastUpdated: ISODate("2025-12-30T10:00:00Z")
}

// Update totalValue when holdings change
// Fast reads (no calculation)
```

**7. Alternative: Switch to Cassandra**

"If MongoDB still can't handle load:

**Cassandra advantages:**
- Linear scalability (add nodes → more capacity)
- Built for write-heavy workloads
- Better for time-series data

**Migration path:**
```java
// Keep MongoDB for now
// Add Cassandra for transaction history (time-series)
// Portfolio Service → MongoDB (current portfolios)
// Analytics Service → Cassandra (historical data)
```"

---

### Question: "How do you ensure the system is stateless for horizontal scaling?"

**Answer:**

**1. Stateless Design Principles**

"All services are stateless - no session data stored in memory:

**❌ Stateful (BAD):**
```java
@Service
public class PortfolioService {
    // Instance variable (state!)
    private Map<String, Portfolio> userSessions = new HashMap<>();
    
    public Portfolio getPortfolio(String id) {
        // Load from memory
        return userSessions.get(id);
    }
}

// Problem:
User request → Instance 1 (has session)
User request → Instance 2 (no session) ❌ 404 Not Found!
```

**✅ Stateless (GOOD):**
```java
@Service
public class PortfolioService {
    private final PortfolioRepository repository;  // Stateless
    
    public Portfolio getPortfolio(String id) {
        // Always load from database/cache
        return repository.findById(id).orElseThrow();
    }
}

// Any instance can handle any request
```

**2. Session Management**

**For authentication (if added):**

```java
// ❌ Don't store in memory
HttpSession session = request.getSession();
session.setAttribute("userId", userId);

// ✅ Use JWT tokens (stateless)
String token = jwtService.generateToken(userId);
// Client sends token in each request
// Any instance can validate token
```

**3. Caching**

"Use centralized cache (Redis), not local cache:

**❌ Local Cache (Stateful):**
```java
@Cacheable(value = "portfolios")  // Local Caffeine cache
// Each instance has different cache
// Cache eviction not coordinated
```

**✅ Redis (Stateless):**
```java
@Cacheable(value = "portfolios")  
// All instances share Redis cache
// Cache eviction coordinated
```

**4. File Uploads**

"Don't store files locally:

**❌ Local Storage:**
```java
file.transferTo(new File("/app/uploads/" + filename));
// Stored on Instance 1 only
// Instance 2 can't access it
```

**✅ Object Storage (S3):**
```java
s3Client.putObject(bucketName, filename, file.getInputStream());
// All instances can access S3
```

**5. Async Tasks**

"Use message queue, not in-memory queue:

**❌ In-Memory:**
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> processTransaction(id));
// Task lost if instance crashes
```

**✅ Kafka/SQS:**
```java
kafkaTemplate.send("transaction-tasks", task);
// Any instance can consume and process
// Task survives instance crash
```

**6. Health Checks**

"Stateless services should be identical:

```yaml
# Kubernetes/Docker Compose
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
  interval: 30s
  
# Any instance can be killed/restarted
# No data loss (state in database/cache)
```"

## Trade-offs Discussion

### CAP Theorem Trade-offs

**Question: "Explain CAP theorem in the context of your system"**

**Answer:**

**1. CAP Theorem Basics**

"CAP theorem states you can only have 2 of 3:
- **C**onsistency: All nodes see same data at same time
- **A**vailability: System always responds (even if degraded)
- **P**artition tolerance: System works despite network failures

**In distributed systems, network partitions WILL happen, so you must choose:**
- **CP:** Consistency + Partition tolerance (sacrifice availability)
- **AP:** Availability + Partition tolerance (sacrifice consistency)"

**2. Our System Choice: AP (Eventual Consistency)**

"I chose **Availability** over **Consistency**:

**Scenario: Network partition between Transaction Service and Portfolio Service**

```
Transaction Service              Portfolio Service
      ↓                                ↓
  Updates transaction          Can't receive Kafka event
  Status: COMPLETED           Holdings NOT updated (yet)
      ↓                                ↓
  Still accepting              Still serving read requests
  new transactions            (slightly stale data)
      ✅ Available                   ✅ Available
      ❌ Inconsistent                ❌ Inconsistent
```

**Why this is acceptable:**

✅ Wealth management isn't life-critical (unlike medical systems)  
✅ Portfolio updates within 1-2 seconds (good enough)  
✅ Users prefer fast response over 100% accuracy  
✅ System stays operational during issues

**What we sacrifice:**

❌ Portfolio might show old holdings for brief period  
❌ No strong consistency guarantees  
❌ Need reconciliation mechanisms

**3. If We Chose CP (Strong Consistency)**

"Alternative: Wait for confirmation before responding

```java
@Transactional
public Transaction processTransaction(String id) {
    transaction.setStatus(COMPLETED);
    transactionRepository.save(transaction);
    
    // WAIT for portfolio service to confirm update
    PortfolioUpdate result = portfolioService.updateHoldings(transaction);
    
    if (!result.isSuccess()) {
        throw new Exception("Portfolio update failed - rollback transaction");
    }
    
    return transaction;  // Only return after portfolio updated
}
```

**Trade-off:**
- ✅ Strong consistency (portfolio always accurate)
- ❌ Lower availability (fails if Portfolio Service down)
- ❌ Slower response time (wait for sync update)
- ❌ Coupled services (tight dependency)"

**4. When Would I Choose CP?**

"For banking transactions:

```java
// Bank transfer: $1000 from Account A → Account B
// MUST be atomic (both succeed or both fail)
// Can't have:
//   - A debited, B not credited (lost money!)
//   - A not debited, B credited (free money!)

// Use distributed transaction (2-phase commit)
// Sacrifice availability for consistency
```

**5. Hybrid Approach**

"In reality, I'd use **per-operation** decisions:

| Operation | CAP Choice | Reason |
|-----------|-----------|--------|
| Create portfolio | **CP** | Must be consistent |
| Update holdings | **AP** | Eventual consistency OK |
| Get portfolio | **AP** | Stale data acceptable (cached) |
| Process transaction | **AP** | Async event-driven |
| Calculate total value | **AP** | Approximate OK |"

---

### Consistency vs Performance

**Question: "How do you balance data consistency with performance?"

**Answer:**

**1. Use Caching with TTL**

"Accept slightly stale data for speed:

```java
@Cacheable(value = "portfolios", key = "#id")
public Portfolio getPortfolio(String id) {
    // Cached for 10 minutes
    // Might be 10 minutes out of date
    // But 50x faster (2ms vs 100ms)
}
```

**Trade-off:**
- ✅ Fast reads (2ms from Redis)
- ❌ May show old holdings (max 10 minutes stale)

**When to invalidate:**
```java
@CacheEvict(value = "portfolios", key = "#id")
public Portfolio updatePortfolio(String id, UpdateRequest request) {
    // Immediate cache invalidation
    // Next read gets fresh data
}
```

**2. Async Event Processing**

"Accept eventual consistency for async operations:

```
User creates transaction
  ↓
Transaction Service saves (status: COMPLETED)
  ↓
Returns 201 Created immediately ✅ Fast!
  ↓
Publishes Kafka event
  ↓
Portfolio Service processes event (1-2 seconds later)
  ↓
Holdings updated ✅ Eventually consistent
```

**Trade-off:**
- ✅ Fast response (100ms)
- ❌ Portfolio not updated immediately

**3. Read Your Own Writes**

"Problem: User sees stale data after update

```
1. User updates portfolio name: "My Portfolio" → "Retirement Fund"
2. Backend updates MongoDB + evicts Redis cache
3. User refreshes page immediately
4. Load balancer routes to different instance
5. Instance has old cache → shows "My Portfolio" ❌
```

**Solution: Cache versioning**

```java
@CachePut(value = "portfolios", key = "#result.id")
public Portfolio updatePortfolio(String id, UpdateRequest request) {
    Portfolio updated = portfolioRepository.save(updated);
    
    // Immediately update cache with new version
    redisTemplate.opsForValue().set(
        "portfolio:" + id,
        updated,
        Duration.ofMinutes(10)
    );
    
    return updated;
}
```

**4. Optimistic Locking**

"Prevent lost updates without sacrificing performance:

```java
@Data
@Document
public class Portfolio {
    @Id
    private String id;
    
    @Version  // Optimistic locking
    private Long version;
    
    private BigDecimal totalValue;
}

// Update scenario:
// User A reads portfolio (version = 1)
// User B reads portfolio (version = 1)
// User A updates totalValue (version = 2) ✅
// User B updates totalValue (version = 1) ❌ Conflict!
// MongoDB throws OptimisticLockingFailureException
```

**Trade-off:**
- ✅ No locks (better performance)
- ❌ Update might fail (need retry logic)

**5. Denormalization for Reads**

"Pre-calculate expensive computations:

```java
@Document
public class Portfolio {
    private List<Holding> holdings;
    
    // Denormalized field (calculated once on write)
    private BigDecimal totalValue;
    
    // Recalculate on every update
    public void updateTotalValue() {
        this.totalValue = holdings.stream()
            .map(h -> h.getQuantity().multiply(h.getCurrentPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

// Read: O(1) - just return totalValue
// Write: O(n) - recalculate on update
```

**Trade-off:**
- ✅ Fast reads (no calculation needed)
- ❌ Slower writes (must recalculate)
- ❌ Data might be inconsistent if calculation fails"

---

### Synchronous vs Asynchronous

**Question: "Why use async events instead of sync REST calls?"

**Answer:**

**1. Decoupling Services**

**Synchronous (Tight Coupling):**
```java
@Service
public class TransactionService {
    private final PortfolioServiceClient portfolioClient;  // Dependency!
    
    public Transaction processTransaction(String id) {
        Transaction txn = complete(id);
        
        // Synchronous HTTP call
        portfolioClient.updateHoldings(txn.getPortfolioId(), txn.getSymbol());
        
        return txn;
    }
}

// Problems:
// ❌ Transaction Service depends on Portfolio Service
// ❌ If Portfolio Service down → Transaction fails
// ❌ Latency adds up (100ms + 50ms = 150ms total)
// ❌ Timeout handling complex
```

**Asynchronous (Loose Coupling):**
```java
@Service
public class TransactionService {
    private final KafkaTemplate kafkaTemplate;  // No service dependency!
    
    public Transaction processTransaction(String id) {
        Transaction txn = complete(id);
        
        // Publish event (fire and forget)
        kafkaTemplate.send("transaction-events", buildEvent(txn));
        
        return txn;  // Return immediately
    }
}

// Benefits:
// ✅ No dependency on Portfolio Service
// ✅ Portfolio Service down → Transaction still succeeds
// ✅ Fast response (100ms, no waiting)
// ✅ Portfolio Service processes when ready
```

**2. Failure Handling**

**Synchronous:**
```java
try {
    portfolioClient.updateHoldings(id, symbol);
} catch (ServiceUnavailableException e) {
    // What to do? 🤔
    // - Retry? (how many times?)
    // - Fail transaction? (but already completed!)
    // - Return error to user? (confusing)
}
```

**Asynchronous:**
```java
kafkaTemplate.send("transaction-events", event);
// Kafka handles retries automatically
// Dead letter queue for poison messages
// Portfolio Service processes when available
// No error handling in Transaction Service!
```

**3. Scalability**

**Synchronous (Blocking):**
```
Transaction Service (200 threads)
  ↓ HTTP call (wait 50ms)
Portfolio Service
  ↓ MongoDB query (wait 50ms)
MongoDB

// Each thread blocked for 100ms
// Max throughput: 200 threads / 0.1s = 2,000 req/s
```

**Asynchronous (Non-blocking):**
```
Transaction Service (200 threads)
  ↓ Kafka publish (1ms, no waiting)
Kafka
  ↓ (async processing)
Portfolio Service (processes independently)
  ↓
MongoDB

// Threads free immediately
// Max throughput: 200 threads / 0.001s = 200,000 req/s
```

**4. When to Use Each**

**Use Synchronous When:**
- Need immediate response (user waiting)
- Simple request/reply pattern
- Strong consistency required
- Low latency tolerable

**Example:**
```java
@GetMapping("/portfolios/{id}")
public Portfolio getPortfolio(@PathVariable String id) {
    // User waiting → must be synchronous
    return portfolioService.getPortfolio(id);
}
```

**Use Asynchronous When:**
- Fire-and-forget operations
- Don't need immediate result
- Eventual consistency acceptable
- High throughput needed

**Example:**
```java
@PostMapping("/transactions")
public Transaction createTransaction(@RequestBody CreateRequest request) {
    Transaction created = transactionService.create(request);
    // Portfolio update happens async (user doesn't need to wait)
    return created;
}
```"

## Code Walkthrough

### Scenario: "Walk me through your Portfolio Service code"

**Answer Framework:**

**1. Start with High-Level Structure (30 seconds)**

"Let me show you the layered architecture:

```
portfolio-service/
├── controller/         ← REST API endpoints
│   └── PortfolioController.java
├── service/           ← Business logic
│   └── PortfolioService.java
├── repository/        ← Data access
│   └── PortfolioRepository.java
├── model/             ← Domain entities
│   ├── Portfolio.java
│   └── Holding.java
├── dto/               ← Data transfer objects
│   └── PortfolioDTO.java
├── mapper/            ← DTO ↔ Entity conversion
│   └── PortfolioMapper.java
├── event/             ← Kafka events
│   └── PortfolioEvent.java
└── config/            ← Configuration
    ├── RedisConfig.java
    └── KafkaConfig.java
```

**2. Walk Through Request Flow (2 minutes)**

"Let me trace a GET portfolio request:

**Step 1: Controller (Entry Point)**

```java
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/{id}")
    public ResponseEntity<PortfolioDTO.Response> getPortfolio(@PathVariable String id) {
        PortfolioDTO.Response portfolio = portfolioService.getPortfolioById(id);
        return ResponseEntity.ok(portfolio);
    }
}
```

**Why this design:**
- `@RestController` → Auto-serializes to JSON
- `@RequiredArgsConstructor` → Constructor injection (immutable)
- Returns DTO, not entity (decoupling)
- Clean separation: Controller just routes, no business logic

**Step 2: Service (Business Logic)**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;

    @Cacheable(value = "portfolios", key = "#id")
    public PortfolioDTO.Response getPortfolioById(String id) {
        log.info("Fetching portfolio: {}", id);
        
        Portfolio portfolio = portfolioRepository.findById(id)
            .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + id));
        
        return portfolioMapper.toResponse(portfolio);
    }
}
```

**Design decisions:**
- `@Cacheable` → Caches in Redis (10-minute TTL)
- `.orElseThrow()` → Fail fast with meaningful error
- DTO mapping → Never expose entity to clients
- Logging → Debug production issues

**Step 3: Repository (Data Access)**

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {
    Optional<Portfolio> findByAccountNumber(String accountNumber);
    List<Portfolio> findByOwnerName(String ownerName);
}
```

**Why interface:**
- Spring Data auto-implements (no boilerplate)
- Method names define queries (convention over configuration)
- Easy to mock in tests

**Step 4: Entity (Domain Model)**

```java
@Data
@Builder
@Document(collection = "portfolios")
public class Portfolio {
    @Id
    private String id;
    
    private String accountNumber;
    private String ownerName;
    private BigDecimal initialCash;
    private String currency;
    
    @DBRef
    private List<Holding> holdings;
    
    private BigDecimal totalValue;
    
    @CreatedDate
    private LocalDateTime createdDate;
}
```

**Design choices:**
- `@Document` → MongoDB collection mapping
- `@DBRef` → Reference to holdings (normalized)
- `BigDecimal` → Financial precision (no floating point errors)
- Lombok `@Data` → Reduces boilerplate

**3. Highlight Key Patterns (1 minute)**

**Pattern 1: DTO Pattern**

```java
// Never return entity directly
❌ public Portfolio getPortfolio(String id) { }

// Always return DTO
✅ public PortfolioDTO.Response getPortfolio(String id) { }

// Why?
// - Decouple API from database schema
// - Hide sensitive fields
// - Version API independently
// - Add calculated fields
```

**Pattern 2: MapStruct for Mapping**

```java
@Mapper(componentModel = "spring")
public interface PortfolioMapper {
    PortfolioDTO.Response toResponse(Portfolio portfolio);
    Portfolio toEntity(PortfolioDTO.CreateRequest request);
}

// Generated code is type-safe and fast
// No manual mapping boilerplate
```

**Pattern 3: Exception Handling**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PortfolioNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status(404)
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}

// Centralized error handling
// Consistent error responses
// No try-catch in every controller
```

---

### Scenario: "Explain your caching strategy"

**Answer:**

**1. Show the Configuration**

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );
    }
}
```

**2. Show Usage in Service**

```java
@Cacheable(value = "portfolios", key = "#id")
public PortfolioDTO.Response getPortfolioById(String id) {
    // Cache miss: Query MongoDB (50ms)
    // Cache hit: Return from Redis (2ms)
    return portfolioMapper.toResponse(
        portfolioRepository.findById(id).orElseThrow()
    );
}

@CacheEvict(value = "portfolios", key = "#id")
public PortfolioDTO.Response updatePortfolio(String id, UpdateRequest request) {
    // Invalidate cache on update
    Portfolio updated = /* update logic */;
    return portfolioMapper.toResponse(updated);
}

@CacheEvict(value = "portfolios", allEntries = true)
public void clearAllCache() {
    // Clear entire cache (admin operation)
}
```

**3. Explain Trade-offs**

"I chose 10-minute TTL because:
- ✅ Portfolio data doesn't change frequently
- ✅ Reduces MongoDB load by 95%
- ✅ 10 minutes = acceptable staleness
- ❌ Trade-off: User might see slightly old data

Alternative approaches:
- **Write-through cache:** Update cache on every write (complex)
- **Cache aside:** Manual cache management (more control)
- **TTL = 0:** Disable TTL, evict only on update (risk of stale data)

I chose TTL for simplicity and reliability."

---

### Scenario: "Show me how you handle transactions"

**Answer:**

**1. Show Transaction Processing Code**

```java
@Transactional
public TransactionDTO.Response createTransaction(CreateRequest request) {
    // 1. Validate
    validateTransaction(request);
    
    // 2. Create entity
    Transaction transaction = transactionMapper.toEntity(request);
    transaction.setStatus(TransactionStatus.PENDING);
    
    // 3. Calculate amounts
    BigDecimal amount = request.getQuantity().multiply(request.getPrice());
    transaction.setAmount(amount);
    transaction.calculateTotalAmount();
    
    // 4. Save to MongoDB
    Transaction saved = transactionRepository.save(transaction);
    
    // 5. Publish event
    publishEvent(saved, EventType.TRANSACTION_CREATED);
    
    // 6. Process immediately
    return processTransaction(saved.getId());
}
```

**2. Explain @Transactional**

"The `@Transactional` annotation ensures:

```java
// Without @Transactional:
transaction.save();          // ✅ Saved
// CRASH HERE
publishEvent(transaction);   // ❌ Never published
// Inconsistent state!

// With @Transactional:
transaction.save();
// CRASH HERE
publishEvent(transaction);
// Both rolled back ✅ Consistent state!
```

**3. Show State Machine**

```java
@Transactional
public TransactionDTO.Response processTransaction(String id) {
    Transaction txn = getTransactionById(id);
    
    // Guard clause (idempotency)
    if (txn.getStatus() != PENDING) {
        return transactionMapper.toResponse(txn);
    }
    
    // State transition: PENDING → PROCESSING
    txn.setStatus(PROCESSING);
    transactionRepository.save(txn);
    publishEvent(txn, TRANSACTION_PROCESSING);
    
    try {
        // Execute transaction
        executeTrade(txn);
        
        // Success: PROCESSING → COMPLETED
        txn.setStatus(COMPLETED);
        txn.setProcessedDate(LocalDateTime.now());
        transactionRepository.save(txn);
        publishEvent(txn, TRANSACTION_COMPLETED);
        
    } catch (Exception e) {
        // Failure: PROCESSING → FAILED
        txn.setStatus(FAILED);
        transactionRepository.save(txn);
        publishEvent(txn, TRANSACTION_FAILED);
        throw e;
    }
    
    return transactionMapper.toResponse(txn);
}
```

**4. Key Points**

"Notice:
- ✅ State machine prevents invalid transitions
- ✅ Events published at each transition
- ✅ Idempotent (safe to retry)
- ✅ Clear error handling
- ✅ Audit trail (all events logged)"

---

### Scenario: "How do you test this code?"

**Answer:**

**1. Unit Tests (Service Layer)**

```java
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    
    @Mock
    private KafkaTemplate<String, PortfolioEvent> kafkaTemplate;
    
    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    void getPortfolioById_shouldReturnPortfolio() {
        // Given
        String id = "PORT001";
        Portfolio portfolio = Portfolio.builder()
            .id(id)
            .accountNumber("ACC001")
            .ownerName("John Doe")
            .build();
        
        when(portfolioRepository.findById(id)).thenReturn(Optional.of(portfolio));
        
        // When
        PortfolioDTO.Response result = portfolioService.getPortfolioById(id);
        
        // Then
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getOwnerName()).isEqualTo("John Doe");
        verify(portfolioRepository).findById(id);
    }
    
    @Test
    void getPortfolioById_shouldThrowException_whenNotFound() {
        // Given
        when(portfolioRepository.findById(any())).thenReturn(Optional.empty());
        
        // When/Then
        assertThrows(PortfolioNotFoundException.class, 
            () -> portfolioService.getPortfolioById("INVALID"));
    }
}
```

**2. Integration Tests (With Testcontainers)**

```java
@SpringBootTest
@Testcontainers
class PortfolioServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0")
        .withExposedPorts(27017);
    
    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl);
    }
    
    @Autowired
    private PortfolioService portfolioService;
    
    @Test
    void createPortfolio_shouldPersistToMongoDB() {
        // Given
        CreateRequest request = CreateRequest.builder()
            .accountNumber("ACC001")
            .ownerName("John Doe")
            .build();
        
        // When
        PortfolioDTO.Response created = portfolioService.createPortfolio(request);
        
        // Then
        assertThat(created.getId()).isNotNull();
        
        // Verify persisted
        PortfolioDTO.Response retrieved = portfolioService.getPortfolioById(created.getId());
        assertThat(retrieved.getOwnerName()).isEqualTo("John Doe");
    }
}
```

**3. API Tests (RestAssured)**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class PortfolioControllerApiTest {

    @LocalServerPort
    private int port;
    
    @Test
    void getPortfolio_shouldReturn200() {
        given()
            .port(port)
            .pathParam("id", "PORT001")
        .when()
            .get("/api/portfolios/{id}")
        .then()
            .statusCode(200)
            .body("id", equalTo("PORT001"))
            .body("ownerName", notNullValue());
    }
}
```

**4. Test Pyramid**

"I follow the test pyramid:
- **70% Unit tests** → Fast, isolated, mock dependencies
- **20% Integration tests** → Real database (Testcontainers)
- **10% API tests** → Full stack, end-to-end

This gives good coverage without slow tests.""

## Architecture Diagrams

### How to Draw System Architecture (Whiteboard/Interview)

**1. Start Simple (30 seconds)**

"Let me draw the high-level architecture first:"

```
┌──────────┐
│  Client  │
│ (React)  │
└─────┬────┘
      │
      ▼
┌─────────────┐
│ API Gateway │
└──────┬──────┘
       │
   ┌───┴───┬───────────┬────────────┐
   ▼       ▼           ▼            ▼
┌────┐  ┌────┐    ┌────────┐   ┌────────┐
│Port│  │Txn │    │Notif.  │   │Future  │
│folio│ │Svc │    │Service │   │Services│
└────┘  └────┘    └────────┘   └────────┘
   │       │           │
   └───┬───┴──────┬────┘
       ▼          ▼
   ┌────────┐  ┌─────┐
   │MongoDB │  │Kafka│
   └────────┘  └─────┘
```

**2. Add More Detail (1 minute)**

"Now let me add the data stores and caching:"

```
                    ┌──────────────┐
                    │   Frontend   │
                    │   (React)    │
                    │   Port 3000  │
                    └──────┬───────┘
                           │ HTTP
                           ▼
                    ┌─────────────────────┐
                    │   API Gateway       │
                    │   Spring Cloud      │
                    │   Port 8080         │
                    │                     │
                    │ - Circuit Breaker   │
                    │ - Rate Limiting     │
                    │ - CORS              │
                    └──────┬──────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌──────────────┐  ┌──────────────┐
│Portfolio Svc  │  │Transaction   │  │Notification  │
│Port 8081      │  │Service       │  │Service       │
│               │  │Port 8082     │  │Port 8083     │
│- REST API     │  │- REST API    │  │- Kafka       │
│- Caching      │  │- State Mgmt  │  │  Consumer    │
│- Events       │  │- Events      │  │- Email/SMS   │
└───┬───────┬───┘  └───┬──────┬───┘  └──────┬───────┘
    │       │          │      │             │
    │       │          │      └─────────────┼──────┐
    ▼       ▼          ▼                    ▼      ▼
┌────────┐ ┌────┐  ┌────────┐         ┌────────────┐
│MongoDB │ │Redis│ │MongoDB │         │   Kafka    │
│portfolio│ │Cache│ │ txn_db │         │ (Topics)   │
│_db     │ │     │ │        │         │            │
└────────┘ └────┘  └────────┘         │- portfolio │
                                       │  -events   │
                                       │- transaction│
                                       │  -events   │
                                       └────────────┘
```

**3. Sequence Diagram (Transaction Flow)**

"For a transaction, here's the sequence:"

```
User     Frontend    Gateway    TxnSvc    Kafka    PortSvc   NotifSvc
 │          │          │          │         │         │         │
 │──Buy────>│          │          │         │         │         │
 │         100 AAPL    │          │         │         │         │
 │          │          │          │         │         │         │
 │          │──POST───>│          │         │         │         │
 │          │  /txn    │          │         │         │         │
 │          │          │          │         │         │         │
 │          │          │─create──>│         │         │         │
 │          │          │  txn     │         │         │         │
 │          │          │          │         │         │         │
 │          │          │          │─save───>MongoDB  │         │
 │          │          │          │         │         │         │
 │          │          │          │─CREATED────────>│         │
 │          │          │          │  event  │         │         │
 │          │          │          │         │         │         │
 │          │          │          │─process │         │         │
 │          │          │          │         │         │         │
 │          │          │          │─COMPLETED──────>│         │
 │          │          │          │  event  │         │         │
 │          │          │          │         │         │         │
 │          │          │<─201─────│         │         │         │
 │          │<─created─│          │         │         │         │
 │<─success─│          │         │         │         │         │
 │          │          │          │         │──update─>         │
 │          │          │          │         │ holdings│         │
 │          │          │          │         │         │         │
 │          │          │          │         │─────notify────────>│
 │          │          │          │         │         │  send   │
 │          │          │          │         │         │  email  │
 │<────────────────────email "Transaction Complete"──────────────│
```

**4. Component Diagram (Internal Service)**

"Inside Portfolio Service:"

```
┌─────────────────────────────────────────────────────┐
│            Portfolio Service                        │
│                                                     │
│  ┌─────────────────────────────────────────────┐  │
│  │         Controller Layer                    │  │
│  │  ┌───────────────────────────────────────┐ │  │
│  │  │  PortfolioController                  │ │  │
│  │  │  - GET /api/portfolios                │ │  │
│  │  │  - POST /api/portfolios               │ │  │
│  │  │  - PUT /api/portfolios/{id}           │ │  │
│  │  └───────────────┬───────────────────────┘ │  │
│  └──────────────────┼─────────────────────────┘  │
│                     ▼                             │
│  ┌─────────────────────────────────────────────┐  │
│  │         Service Layer                       │  │
│  │  ┌───────────────────────────────────────┐ │  │
│  │  │  PortfolioService                     │ │  │
│  │  │  - Business Logic                     │ │  │
│  │  │  - Validation                         │ │  │
│  │  │  - Caching (@Cacheable)               │ │  │
│  │  │  - Event Publishing                   │ │  │
│  │  └───┬───────────────────┬───────────────┘ │  │
│  └──────┼───────────────────┼─────────────────┘  │
│         ▼                   ▼                     │
│  ┌────────────┐      ┌──────────────┐            │
│  │ Repository │      │ KafkaTemplate│            │
│  │   Layer    │      │              │            │
│  │            │      └──────┬───────┘            │
│  └─────┬──────┘             │                    │
└────────┼────────────────────┼────────────────────┘
         ▼                    ▼
    ┌─────────┐         ┌─────────┐
    │ MongoDB │         │  Kafka  │
    └─────────┘         └─────────┘
```

**5. Deployment Diagram (Docker)**

"How it runs in production:"

```
┌─────────────────────────────────────────────────────┐
│              Docker Host / Kubernetes               │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │Portfolio │  │Transaction│ │Notification│        │
│  │Service   │  │Service    │ │Service    │         │
│  │Container │  │Container  │ │Container  │         │
│  │:8081     │  │:8082      │ │:8083      │         │
│  └────┬─────┘  └────┬──────┘ └────┬──────┘         │
│       │             │             │                 │
│       └─────────────┼─────────────┘                 │
│                     │                               │
│  ┌──────────────────┼───────────────────────────┐  │
│  │         Docker Network (pms-network)         │  │
│  │                  │                            │  │
│  │    ┌─────────────┼─────────────────┐        │  │
│  │    │             ▼                 │        │  │
│  │  ┌────────┐  ┌─────────┐  ┌──────────┐    │  │
│  │  │MongoDB │  │  Redis  │  │  Kafka   │    │  │
│  │  │:27017  │  │  :6379  │  │  :9092   │    │  │
│  │  │        │  │         │  │          │    │  │
│  │  │Volume: │  │         │  │ Zookeeper│    │  │
│  │  │mongo-data│ │         │  │  :2181   │    │  │
│  │  └────────┘  └─────────┘  └──────────┘    │  │
│  │                                            │  │
│  └────────────────────────────────────────────┘  │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │         Port Mapping                        │ │
│  │  8080 → API Gateway                         │ │
│  │  8081 → Portfolio Service                   │ │
│  │  8082 → Transaction Service                 │ │
│  │  8083 → Notification Service                │ │
│  │  27017 → MongoDB                            │ │
│  │  6379 → Redis                               │ │
│  │  9092 → Kafka                               │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

**6. Data Flow Diagram**

"How data flows for a portfolio update:"

```
                    READ PATH
                    ─────────
                        
Frontend ──GET /portfolios/1──> Gateway ──> Portfolio Service
                                                    │
                                              ┌─────▼─────┐
                                              │Redis Cache│
                                              │  (Check)  │
                                              └─────┬─────┘
                                                    │
                                            ┌───────┴────────┐
                                            │                │
                                         Hit│             Miss│
                                            │                │
                                            ▼                ▼
                                    Return cached    Query MongoDB
                                         (2ms)            (50ms)
                                                            │
                                                            ▼
                                                      Store in cache
                                                            │
                                                            ▼
                                                    Return to user


                    WRITE PATH
                    ──────────
                        
Frontend ──POST /transactions──> Gateway ──> Transaction Service
                                                      │
                                                      ▼
                                              Save to MongoDB
                                              (status: PENDING)
                                                      │
                                                      ▼
                                             Publish CREATED event
                                                      │
                                                      ▼
                                              Process transaction
                                              (status: COMPLETED)
                                                      │
                                                      ▼
                                             Publish COMPLETED event
                                                      │
                                    ┌─────────────────┴─────────────────┐
                                    │                                   │
                                    ▼                                   ▼
                           Portfolio Service                   Notification Service
                           (Kafka Consumer)                     (Kafka Consumer)
                                    │                                   │
                                    ▼                                   ▼
                            Update holdings                        Send email
                                    │
                                    ▼
                            Evict Redis cache
                                    │
                                    ▼
                           Next GET → fresh data
```

**Tips for Drawing in Interview:**

1. **Start simple, add detail gradually**
2. **Use boxes for services, cylinders for databases**
3. **Label all connections (HTTP, Kafka, etc.)**
4. **Show data flow with arrows**
5. **Explain while drawing** ("This box is the API Gateway...")
6. **Ask if they want more detail** before adding complexity

## Common Pitfalls

### Interview Red Flags to Avoid

**1. Overengineering**

❌ **AVOID:**
"I'd use microservices, Kubernetes, service mesh, CQRS, event sourcing, GraphQL federation..."

✅ **BETTER:**
"I chose microservices for this demo to show distributed systems knowledge. For a real startup with 3 developers, I'd start with a monolith and extract services only when needed."

**Honest Assessment:**
- "This project is deliberately over-architected for learning"
- "In real life, I'd evaluate team size, traffic, and timeline"
- "I'd start simple and add complexity based on metrics"

**2. Ignoring Trade-offs**

❌ **AVOID:**
"MongoDB is always better than PostgreSQL"
"Microservices are superior to monoliths"

✅ **BETTER:**
"I chose MongoDB because:
- Schema flexibility for evolving domain
- Document model matches portfolio structure
- Good for read-heavy operations

But PostgreSQL would be better if:
- We need ACID transactions across portfolios
- Complex joins are common
- Strong consistency is critical"

**3. Not Knowing Limitations**

❌ **AVOID:**
Interviewer: "How does your system handle split brain?"
You: "Um... Redis handles that automatically?"

✅ **BETTER:**
"Great question. My current setup doesn't handle Redis split-brain gracefully. In production, I'd:
- Use Redis Sentinel for automatic failover
- Or Redis Cluster for partitioning
- Consider what happens when cache is inconsistent
- Implement cache versioning if needed"

**Be honest about what you don't know!**

**4. Cargo Cult Programming**

❌ **AVOID:**
"I used @Transactional because that's what everyone does"
"I added caching everywhere for speed"

✅ **BETTER:**
"I used @Transactional here because we need atomic updates to portfolio and audit log. I specifically set isolation=READ_COMMITTED to allow concurrent reads while preventing dirty reads."

**Understand WHY, not just HOW**

**5. Buzzword Soup**

❌ **AVOID:**
"It's a cloud-native, containerized, serverless, AI-powered, blockchain-enabled system"

✅ **BETTER:**
"It's a Spring Boot application running in Docker containers, using MongoDB for persistence and Kafka for asynchronous communication between services."

**Use precise technical terms**

**6. Ignoring Metrics**

❌ **AVOID:**
Interviewer: "Why did you cache with 10-minute TTL?"
You: "10 minutes seemed reasonable?"

✅ **BETTER:**
"I chose 10 minutes based on:
- Portfolio values change frequently (market hours)
- Balance between freshness and load
- In production, I'd monitor cache hit ratio and adjust

If hit ratio < 80%, increase TTL
If users report stale data, decrease TTL"

**7. Not Considering Scale**

❌ **AVOID:**
"This handles my test data fine"

✅ **BETTER:**
"Current design works for ~10K users. Beyond that:
- Need MongoDB sharding (tested on embedded, would need to prove at scale)
- Need Redis cluster for cache
- Need Kafka partitioning by portfolio ID
- Need to measure and optimize based on real metrics"

**8. Dismissing Simpler Solutions**

❌ **AVOID:**
Interviewer: "Why not use a simple database trigger?"
You: "Triggers are legacy, we use event-driven architecture now"

✅ **BETTER:**
"Good point. A trigger would be simpler. I chose Kafka because:
- Wanted to demonstrate event-driven patterns (for interview/learning)
- Multiple consumers can process same event
- Events are persisted for replay

But you're right - for a single consumer, a trigger would work fine and be easier to maintain."

**9. Not Knowing Your Own Code**

❌ **AVOID:**
Interviewer: "How does your state machine work?"
You: "Um... it's in TransactionService somewhere..."

✅ **PREPARE:**
Know these cold:
- How portfolios are cached and evicted
- Transaction state transitions
- Event publishing flow
- Error handling strategy
- API endpoints and what they do

**10. Poor Demo Preparation**

❌ **AVOID:**
"Let me start the services... hmm, why isn't Kafka working?"
[10 minutes of debugging in interview]

✅ **PREPARE:**
- Test demo flow 3 times before interview
- Have docker-compose running before call starts
- Know exact curl commands or use Postman collection
- Have backup screenshots if demo fails

**11. Underestimating Operational Concerns**

❌ **AVOID:**
Interviewer: "How would you debug a production issue?"
You: "Add more logs and redeploy?"

✅ **BETTER:**
"I'd check:
1. Spring Boot Actuator health endpoints
2. Application logs (structured logging with correlation IDs)
3. MongoDB slow query logs
4. Kafka consumer lag
5. Redis metrics

Then reproduce in lower environment before deploying fix."

**12. Not Connecting to Business Value**

❌ **AVOID:**
"I used Kafka because it's cool technology"

✅ **BETTER:**
"For wealth management, audit trail is critical:
- Kafka persists all events (regulatory compliance)
- Can replay events to reconstruct state
- Supports future analytics (pattern detection, fraud)
- Decouples transaction processing from notifications"

**Technical choices should map to business requirements**

### What Interviewers Want to Hear

**✅ DO SAY:**
- "I don't know, but here's how I'd find out..."
- "In hindsight, I'd do X differently because..."
- "This is over-engineered for a demo, real scenario would be..."
- "Let me think through the trade-offs..."
- "Great question! I hadn't considered that..."

**❌ DON'T SAY:**
- "That's how the tutorial did it"
- "I just copied from Stack Overflow"
- "My code is perfect and scalable to billions"
- "I don't remember" [for your own code]
- Making up technical details you're unsure about

### Recovery Strategies

**If Demo Breaks:**
"Looks like Kafka isn't starting. Let me show you the code instead and walk through what should happen."
[Have code sections ready to show]

**If You Don't Know:**
"I haven't implemented that yet, but here's how I'd approach it:
1. Research Redis Sentinel for high availability
2. Test failover scenarios
3. Measure impact on cache hit ratio"

**If Caught Overengineering:**
"You're absolutely right - this is too complex for a real 3-person startup. I built it to learn and demonstrate patterns, not as production-ready code for this scale."

**Remember: Honesty + Thoughtfulness > Pretending to Know Everything**

## Demo Script

### 10-Minute Technical Demo Flow

**Total Time: 10 minutes**
- Setup verification: 30 seconds
- Create portfolio: 2 minutes
- Execute transaction: 3 minutes
- Show caching: 2 minutes
- Architecture walkthrough: 2.5 minutes

---

### MINUTE 0-0:30: Introduction & Setup Check

**Say:**
"I'll demonstrate a microservices-based portfolio management system I built. It showcases event-driven architecture, caching strategies, and distributed systems patterns commonly used in financial technology."

**Do:**
```powershell
# Quick health check
docker ps --format "table {{.Names}}\t{{.Status}}"
```

**Say:**
"All services are running - 4 microservices plus MongoDB, Redis, and Kafka. Let me show you the workflow."

---

### MINUTE 0:30-2:30: Create Portfolio (API Gateway → Portfolio Service → MongoDB → Redis)

**Say:**
"First, I'll create a client portfolio through the API Gateway. This demonstrates service routing and data persistence."

**Do:**
```powershell
# Create portfolio
curl -X POST http://localhost:8080/api/portfolios `
  -H "Content-Type: application/json" `
  -d '{"clientName": "John Doe", "riskTolerance": "MODERATE", "investmentGoals": ["GROWTH", "INCOME"]}'
```

**Expected Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "clientName": "John Doe",
  "riskTolerance": "MODERATE",
  "totalValue": 0.0,
  "holdings": [],
  "createdAt": "2024-01-15T10:30:00"
}
```

**Explain While Showing:**
"Notice the flow:
1. **API Gateway** (port 8080) routes to Portfolio Service (port 8081)
2. **Portfolio Service** validates and saves to MongoDB
3. Data is returned with generated ID
4. Portfolio is now **cached in Redis** for 10 minutes"

**Show the Data:**
```powershell
# Show in MongoDB
docker exec -it pms-mongodb mongosh --quiet --eval `
  "use portfolio_db; db.portfolios.find({clientName: 'John Doe'}).pretty()"
```

**Say:**
"Here's the document in MongoDB - notice the nested structure and metadata."

---

### MINUTE 2:30-5:30: Execute Transaction (State Machine + Events)

**Say:**
"Now I'll execute a stock purchase. This shows the state machine pattern and event-driven communication between services."

**Do:**
```powershell
# Buy stocks
curl -X POST http://localhost:8080/api/transactions `
  -H "Content-Type: application/json" `
  -d '{
    "portfolioId": "507f1f77bcf86cd799439011",
    "type": "BUY",
    "symbol": "AAPL",
    "quantity": 100,
    "price": 180.50
  }'
```

**Expected Response:**
```json
{
  "transactionId": "txn_20240115_001",
  "status": "COMPLETED",
  "totalAmount": 18050.00,
  "timestamp": "2024-01-15T10:35:00"
}
```

**Explain the Flow:**

"Let me show you what just happened behind the scenes:"

**1. Transaction Service Processing:**
```
CREATED → PENDING → VALIDATED → PROCESSING → COMPLETED
```

**Say:**
"The transaction moves through a state machine:
- **CREATED**: Initial state, saved to MongoDB
- **PENDING**: Validation in progress
- **VALIDATED**: Sufficient funds checked
- **PROCESSING**: Executing the trade
- **COMPLETED**: Success

At each transition, events are published to Kafka."

**2. Show Kafka Events:**
```powershell
# Show recent events
docker exec -it pms-kafka kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic transaction-events `
  --from-beginning --max-messages 2
```

**Say:**
"See the events:
- `TRANSACTION_CREATED` → Triggers notification
- `TRANSACTION_COMPLETED` → Updates portfolio holdings"

**3. Show Updated Portfolio:**
```powershell
# Get updated portfolio
curl http://localhost:8080/api/portfolios/507f1f77bcf86cd799439011
```

**Say:**
"Notice the holdings array now shows:
- 100 shares of AAPL
- Purchase price $180.50
- Total value updated to $18,050

This update happened asynchronously via Kafka event consumption."

---

### MINUTE 5:30-7:30: Demonstrate Caching Strategy

**Say:**
"Let me show you the caching layer. This is critical for high-performance financial systems."

**Do:**

**1. First Request (Cache MISS):**
```powershell
# Measure response time
Measure-Command {
  curl http://localhost:8080/api/portfolios/507f1f77bcf86cd799439011
} | Select-Object -ExpandProperty TotalMilliseconds
```

**Say:**
"First request: ~50ms (MongoDB query)"

**2. Second Request (Cache HIT):**
```powershell
# Cached request
Measure-Command {
  curl http://localhost:8080/api/portfolios/507f1f77bcf86cd799439011
} | Select-Object -ExpandProperty TotalMilliseconds
```

**Say:**
"Second request: ~2ms (Redis cache)
That's **25x faster** - critical for real-time dashboards."

**3. Show Cache Invalidation:**
```powershell
# Update portfolio (evicts cache)
curl -X PUT http://localhost:8080/api/portfolios/507f1f77bcf86cd799439011 `
  -H "Content-Type: application/json" `
  -d '{"riskTolerance": "AGGRESSIVE"}'

# Next GET will be cache MISS again
```

**Say:**
"When portfolio updates, cache is evicted using `@CacheEvict` annotation. Next read fetches fresh data from MongoDB."

**4. Show Redis Contents:**
```powershell
# Peek into Redis
docker exec -it pms-redis redis-cli KEYS "portfolio:*"
```

**Say:**
"Redis stores portfolios with key pattern `portfolio:{id}` and 10-minute TTL."

---

### MINUTE 7:30-10:00: Architecture Walkthrough

**Say:**
"Let me quickly show you the overall architecture."

**Draw/Show Diagram:**
```
Frontend (React) → API Gateway → Microservices
                                  ↓
                          ┌──────┼──────┬────────┐
                          ▼      ▼      ▼        ▼
                     Portfolio  Txn  Notif   (Future)
                          │      │      │
                      MongoDB  MongoDB  Kafka
                          │             ↑
                       Redis ───────────┘
```

**Explain:**

**1. API Gateway:**
"Spring Cloud Gateway handles:
- Routing (`/api/portfolios` → Portfolio Service)
- Load balancing (if multiple instances)
- Circuit breaker (fails gracefully if service down)
- CORS for frontend"

**2. Portfolio Service:**
"Core service:
- Manages client portfolios and holdings
- Redis caching with @Cacheable
- Publishes events on updates
- MongoDB for persistence"

**3. Transaction Service:**
"Handles trades:
- State machine for transaction lifecycle
- Validation rules (sufficient funds, valid symbols)
- Publishes events at each state change"

**4. Notification Service:**
"Kafka consumer:
- Listens to transaction-events topic
- Sends email/SMS notifications
- Decoupled from transaction processing"

**5. Event-Driven Communication:**
"Why Kafka over REST?
- **Decoupling**: Services don't need to know about each other
- **Reliability**: Events persisted, can replay
- **Scalability**: Multiple consumers can process same event
- **Audit trail**: Every transaction is logged"

**Key Patterns Demonstrated:**
- ✅ Microservices architecture
- ✅ Event-driven communication
- ✅ Caching strategy (Redis)
- ✅ State machine pattern
- ✅ API Gateway pattern
- ✅ Docker containerization

---

### MINUTE 10: Wrap-Up & Questions

**Say:**
"To summarize:
- **4 microservices** working together
- **Event-driven** via Kafka for decoupling
- **Caching** with Redis for performance
- **State management** for complex workflows
- **Production-ready patterns** (health checks, error handling, testing)

This demonstrates skills relevant to:
- Distributed systems design
- Event-driven architecture
- Performance optimization
- Financial domain modeling

Happy to dive deeper into any component or answer questions about design decisions."

---

### Backup: If Demo Fails

**Have Ready:**
1. **Screenshots** of working system
2. **Code walkthrough** as alternative:
   - Show `PortfolioController` → explain REST endpoints
   - Show `TransactionService` → explain state machine
   - Show `application.yml` → explain configuration
3. **Logs** showing successful flow:
   ```
   Portfolio created → Event published → Kafka consumer triggered → Holdings updated
   ```

**Say:**
"Looks like [X] isn't responding. Let me show you the code and walk through what should happen..."

---

### Alternative: Code-First Demo (If Services Won't Start)

**1. Show Service Structure (1 min):**
```
portfolio-service/
  src/main/java/com/portfolio/
    controller/  ← REST endpoints
    service/     ← Business logic + caching
    repository/  ← MongoDB access
    model/       ← Domain entities
```

**2. Walk Through Request Flow (3 min):**
```java
@GetMapping("/{id}")  // Controller receives request
→ portfolioService.getPortfolio(id)  // Service checks cache
  → @Cacheable("portfolios")  // Redis cache lookup
    → portfolioRepository.findById(id)  // MongoDB query (if cache miss)
      → return PortfolioDTO  // Map entity to DTO
```

**3. Show Event Publishing (2 min):**
```java
public Portfolio updatePortfolio(...) {
    Portfolio updated = repository.save(portfolio);
    kafkaTemplate.send("portfolio-events", event);  // Async notification
    return updated;
}
```

**4. Show Configuration (2 min):**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/portfolio_db
  kafka:
    bootstrap-servers: localhost:9092
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
```

**5. Show Tests (2 min):**
```java
@Test
void shouldCachePortfolio() {
    // First call → hits database
    Portfolio p1 = service.getPortfolio(id);
    
    // Second call → hits cache
    Portfolio p2 = service.getPortfolio(id);
    
    verify(repository, times(1)).findById(id);  // Only one DB call
}
```

---

### Pro Tips for Demo

**Before Interview:**
✅ Test demo 3 times end-to-end
✅ Have all curl commands in a script
✅ Start docker-compose 5 minutes before call
✅ Clear MongoDB/Redis to avoid stale data
✅ Have backup plan if anything fails

**During Demo:**
✅ Explain WHILE doing (don't just type silently)
✅ Pause for questions
✅ Connect to real-world scenarios ("In production, we'd...")
✅ Show enthusiasm but stay professional

**Common Questions After Demo:**
- "How would you handle service failures?" → Circuit breaker pattern
- "How do you ensure data consistency?" → Eventual consistency + idempotency
- "How would this scale?" → Horizontal scaling + sharding
- "What about security?" → JWT tokens, API keys, rate limiting

**Be ready to go deeper on ANY component!**
