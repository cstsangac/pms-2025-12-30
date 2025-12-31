# Quick Reference - Interview Cheat Sheet

## System Architecture (30-Second Explanation)
"A microservices-based wealth management platform with 4 services communicating via Kafka events, using MongoDB for persistence, Redis for caching, and Spring Cloud Gateway for API routing."

---

## Key Technical Stack
| Component | Technology | Port | Purpose |
|-----------|-----------|------|---------|
| API Gateway | Spring Cloud Gateway | 8080 | Request routing, circuit breaker |
| Portfolio Service | Spring Boot 3.2.1 | 8081 | Manage portfolios, caching |
| Transaction Service | Spring Boot 3.2.1 | 8082 | Process trades, state machine |
| Notification Service | Spring Boot 3.2.1 | 8083 | Kafka consumer, notifications |
| Database | MongoDB 7.0 | 27017/27018 | Document storage |
| Cache | Redis 7 | 6379 | 10-min TTL cache |
| Message Broker | Kafka 7.5.0 | 9092 | Event-driven messaging |
| Frontend | React 18 + TypeScript | 3000 | Dashboard UI |

---

## Essential Endpoints

```bash
# Create Portfolio
POST http://localhost:8080/api/portfolios
{"clientName":"John Doe","riskTolerance":"MODERATE","investmentGoals":["GROWTH"]}

# Get Portfolio (demonstrates caching)
GET http://localhost:8080/api/portfolios/{id}

# Execute Transaction (demonstrates state machine + events)
POST http://localhost:8080/api/transactions
{"portfolioId":"{id}","type":"BUY","symbol":"AAPL","quantity":100,"price":180.50}

# Health Check
GET http://localhost:8081/actuator/health
```

---

## Key Design Patterns

**1. Event-Driven Architecture**
- Transaction created → Kafka event → Portfolio/Notification services consume
- Decouples services, enables async processing, provides audit trail

**2. Cache-Aside Pattern**
```java
@Cacheable("portfolios") // Check cache first
public Portfolio getPortfolio(String id) {
    return repository.findById(id); // Cache miss → query DB
}

@CacheEvict("portfolios") // Invalidate on update
public Portfolio updatePortfolio(Portfolio p) { ... }
```

**3. State Machine (Transaction Lifecycle)**
```
CREATED → PENDING → VALIDATED → PROCESSING → COMPLETED
                                           ↓
                                       FAILED
```

**4. API Gateway Pattern**
- Single entry point for all clients
- Route: `/api/portfolios/*` → `http://portfolio-service:8081`
- Circuit breaker prevents cascade failures

---

## Spring Boot Key Annotations

```java
// Dependency Injection
@Autowired // Avoid - use constructor injection instead
@RequiredArgsConstructor // Lombok - generates constructor

// REST Controllers
@RestController // = @Controller + @ResponseBody
@RequestMapping("/api/portfolios")
@GetMapping("/{id}") // GET request
@PostMapping // POST request

// Services & Components
@Service // Business logic layer
@Repository // Data access layer
@Component // Generic Spring bean

// Caching
@Cacheable("portfolios") // Cache result
@CacheEvict("portfolios") // Remove from cache
@CachePut // Update cache

// Transactions
@Transactional // ACID guarantees
@Transactional(readOnly = true) // Optimize for reads

// Kafka
@KafkaListener(topics = "transaction-events")
public void handleEvent(TransactionEvent event) { ... }
```

---

## MongoDB Query Examples

```java
// Find by ID
portfolioRepository.findById(id);

// Find by client name
portfolioRepository.findByClientName("John Doe");

// Custom query with @Query
@Query("{'clientName': ?0, 'riskTolerance': ?1}")
List<Portfolio> findByClientAndRisk(String name, String risk);

// Aggregation pipeline
Aggregation agg = Aggregation.newAggregation(
    match(Criteria.where("status").is("ACTIVE")),
    group("riskTolerance").sum("totalValue").as("total")
);
```

---

## Redis Cache Configuration

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
      cache-null-values: false
  data:
    redis:
      host: localhost
      port: 6379
```

---

## Kafka Topics & Events

| Topic | Producer | Consumer | Event Type |
|-------|----------|----------|------------|
| `portfolio-events` | Portfolio Service | Notification Service | PORTFOLIO_CREATED, PORTFOLIO_UPDATED |
| `transaction-events` | Transaction Service | Portfolio Service, Notification Service | TRANSACTION_CREATED, TRANSACTION_COMPLETED |

**Event Schema:**
```json
{
  "eventId": "evt_123",
  "eventType": "TRANSACTION_COMPLETED",
  "timestamp": "2024-01-15T10:30:00Z",
  "payload": {
    "transactionId": "txn_456",
    "portfolioId": "port_789",
    "symbol": "AAPL",
    "quantity": 100
  }
}
```

---

## Testing Quick Reference

```java
// Unit Test (Mockito)
@Mock
private PortfolioRepository repository;

@InjectMocks
private PortfolioService service;

@Test
void shouldCachePortfolio() {
    when(repository.findById("123")).thenReturn(Optional.of(portfolio));
    service.getPortfolio("123");
    verify(repository, times(1)).findById("123");
}

// Integration Test (Testcontainers)
@Testcontainers
class PortfolioServiceIntegrationTest {
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");
    
    // Test with real MongoDB
}

// API Test (RestAssured)
given()
    .contentType("application/json")
    .body(portfolio)
.when()
    .post("/api/portfolios")
.then()
    .statusCode(201);
```

---

## Common Troubleshooting

```bash
# Check service logs
docker logs pms-portfolio-service

# Restart a service
docker-compose restart portfolio-service

# Clear MongoDB data
docker exec -it pms-mongodb mongosh
use portfolio_db
db.portfolios.deleteMany({})

# Clear Redis cache
docker exec -it pms-redis redis-cli FLUSHALL

# Check Kafka topics
docker exec -it pms-kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## Key Trade-offs to Discuss

**1. Eventual Consistency vs Strong Consistency**
- ✅ Chose: Eventual consistency (Kafka async events)
- Trade-off: Portfolio might be briefly stale after transaction
- Mitigation: Cache eviction, idempotent event processing

**2. Microservices vs Monolith**
- ✅ Chose: Microservices (to demonstrate distributed systems)
- Trade-off: More complexity, harder to debug
- Real-world: "Would start with monolith for small team"

**3. MongoDB vs PostgreSQL**
- ✅ Chose: MongoDB (schema flexibility, document model)
- Trade-off: No ACID across collections
- When PostgreSQL better: Complex joins, strict consistency

---

## One-Liners for Common Questions

**"Why Spring Boot?"**
→ "Convention over configuration, industry standard, rapid development with auto-configuration and embedded server."

**"Why Kafka over REST?"**
→ "Decoupling, persistence, multiple consumers can process same event, built-in audit trail."

**"How do you handle failures?"**
→ "Circuit breaker in gateway, retry logic in Kafka consumers, idempotent event handlers, health checks."

**"How would you scale?"**
→ "Horizontal scaling of services, MongoDB sharding, Redis cluster, Kafka partitioning by portfolio ID."

**"What would you do differently?"**
→ "Start with monolith for real startup. Add observability (Prometheus/Grafana). Implement proper authentication (OAuth2/JWT)."

---

## Pro Tips for Interview

1. **Always explain WHILE coding/demoing** - don't type silently
2. **Start simple, add detail** - high-level first, then dive deep
3. **Connect to business value** - "For wealth management, audit trail is critical..."
4. **Be honest about limitations** - "This doesn't handle X, but I'd add Y..."
5. **Ask clarifying questions** - "Are you interested in the caching strategy or the event flow?"

---

## 2-Year Gap Talking Points

✅ **Positive framing:**
- "Took time for family/personal reasons - now fully committed to returning"
- "Used the time to learn modern tech stack (Spring Boot 3, Kafka, React)"
- "Built this project from scratch to demonstrate current skills"
- "Stayed current by following industry trends and hands-on learning"

❌ **Avoid:**
- Being defensive or overly detailed about the gap
- Saying "I forgot everything" or "I'm rusty"
- Making excuses

---

## Questions to Ask Interviewer

**Technical:**
- "What does your current microservices architecture look like?"
- "How do you handle event-driven patterns - Kafka, SNS/SQS, or something else?"
- "What's your deployment pipeline - Kubernetes, ECS, or traditional?"

**Team/Culture:**
- "What does a typical sprint look like for your team?"
- "How do you balance new features vs technical debt?"
- "What opportunities are there for learning and growth?"

**Role-Specific:**
- "What are the biggest technical challenges in the next 6 months?"
- "How does the team collaborate across Asia and Australia time zones?"
- "What does success look like for this role in the first 90 days?"
