# Code Study Guide - Interview Preparation

## Overview
This guide helps you understand the codebase systematically to prepare for technical interviews. After sabbatical, this will help you get back up to speed with modern Java development.

---

## Study Roadmap (Recommended Order)

### Phase 1: Architecture Understanding (Day 1)
1. **Project Structure** - Understand the multi-module Maven layout
2. **Service Boundaries** - Learn what each microservice does
3. **Data Flow** - How requests flow through the system

### Phase 2: Core Technologies (Day 2-3)
4. **Spring Boot Basics** - Controllers, Services, Repositories
5. **MongoDB Integration** - Document database usage
6. **Redis Caching** - Cache strategy implementation

### Phase 3: Advanced Topics (Day 4-5)
7. **Event-Driven Architecture** - Kafka producers and consumers
8. **API Gateway** - Spring Cloud Gateway patterns
9. **Testing Strategy** - Unit and integration tests

### Phase 4: Interview Scenarios (Day 6-7)
10. **Design Decisions** - Why certain technologies were chosen
11. **Common Interview Questions** - Prepare answers
12. **Demo Walkthrough** - Practice explaining the system

---

## Part 1: Project Structure

### Multi-Module Maven Project

```
pms-2025-12-30/                    # Root project
├── pom.xml                        # Parent POM (coordinates all modules)
├── portfolio-service/             # Module 1: Portfolio management
├── transaction-service/           # Module 2: Transaction processing
├── notification-service/          # Module 3: Event notifications
├── api-gateway/                   # Module 4: API routing
└── frontend/                      # React UI (not Maven)
```

**Key Concept:** Each service is a separate Maven module but shares common dependencies defined in the parent POM.

**Files to Study:**
- `/pom.xml` - Parent POM with common dependencies
- `/portfolio-service/pom.xml` - Service-specific dependencies
- `/docker-compose.yml` - Service orchestration

---

## Part 2: Service Boundaries

### Portfolio Service (Port 8081)
**Purpose:** Manage client portfolios and holdings  
**Database:** MongoDB (portfolio_db)  
**Cache:** Redis  
**Responsibilities:**
- CRUD operations for portfolios
- Add/update holdings
- Calculate portfolio values
- Publish Kafka events when portfolios change

**Key Files:**
- `PortfolioController.java` - REST endpoints
- `PortfolioService.java` - Business logic
- `PortfolioRepository.java` - Database access
- `Portfolio.java` - Domain model
- `PortfolioDTO.java` - Data transfer objects

---

### Transaction Service (Port 8082)
**Purpose:** Process buy/sell transactions  
**Database:** MongoDB (transaction_db)  
**Responsibilities:**
- Create and track transactions
- Update transaction status (PENDING → PROCESSING → COMPLETED)
- Publish Kafka events
- Validate transaction rules

**Key Files:**
- `TransactionController.java`
- `TransactionService.java`
- `Transaction.java`

---

### Notification Service (Port 8083)
**Purpose:** Listen to events and send notifications  
**No Database** (stateless)  
**Responsibilities:**
- Consume Kafka events
- Process notifications
- Log notification activities

**Key Files:**
- `EventListener.java` - Kafka consumers with @KafkaListener
- `NotificationService.java` - Notification logic

---

### API Gateway (Port 8080)
**Purpose:** Single entry point, routing, circuit breaker  
**Responsibilities:**
- Route requests to backend services
- CORS handling
- Circuit breaker for resilience
- Request retry logic

**Key Files:**
- `application.yml` - Route configuration
- `FallbackController.java` - Circuit breaker fallbacks

---

## Part 3: Technology Deep Dive Map

### Spring Boot 3.x
**What to Study:**
- Dependency Injection with constructor injection
- `@RestController`, `@Service`, `@Repository` annotations
- Spring Boot Actuator for health/metrics
- Application properties (YAML configuration)

**Files:** Any `*Controller.java`, `*Service.java`, `*Repository.java`

---

### MongoDB (NoSQL Database)
**What to Study:**
- Document-based storage
- Spring Data MongoDB
- `@Document` annotation
- MongoRepository interface

**Files:** 
- `Portfolio.java`, `Transaction.java` (domain models)
- `PortfolioRepository.java` (Spring Data)

---

### Redis (Caching)
**What to Study:**
- Cache-aside pattern
- `@Cacheable`, `@CachePut`, `@CacheEvict` annotations
- Cache configuration

**Files:**
- `PortfolioService.java` - See caching annotations
- `application.yml` - Redis configuration

---

### Kafka (Event Streaming)
**What to Study:**
- Producer: Publishing events
- Consumer: Subscribing to topics
- Event-driven architecture benefits

**Files:**
- `PortfolioEventPublisher.java` - Producer
- `EventListener.java` - Consumer with @KafkaListener
- `PortfolioCreatedEvent.java` - Event objects

---

### Spring Cloud Gateway
**What to Study:**
- Route predicates and filters
- Circuit breaker pattern
- Gateway filters (retry, rewrite)

**Files:**
- `api-gateway/src/main/resources/application.yml`

---

## Part 4: Data Flow Examples

### Example 1: Create Portfolio Flow
1. **Frontend** → HTTP POST to http://localhost:3000/api/portfolio/portfolios
2. **Vite Proxy** → Forwards to http://localhost:8080/api/portfolio/portfolios
3. **API Gateway** → Routes to http://portfolio-service:8081/api/portfolio/portfolios
4. **Portfolio Controller** → Receives request, validates DTO
5. **Portfolio Service** → Business logic, saves to MongoDB
6. **Kafka Producer** → Publishes PortfolioCreatedEvent
7. **Notification Service** → Consumes event, logs notification
8. **Response** → Returns portfolio data through reverse path

### Example 2: Get Portfolio with Cache
1. Request comes to Portfolio Service
2. Check Redis cache first (`@Cacheable`)
3. If cache hit → Return immediately
4. If cache miss → Query MongoDB
5. Store result in Redis for next time
6. Return data

---

## Part 5: Key Interview Topics

### 1. Microservices Architecture
**Be Ready to Explain:**
- Why split into 4 services vs monolith?
- How do services communicate?
- Database per service pattern
- Benefits and challenges

### 2. Event-Driven Design
**Be Ready to Explain:**
- Synchronous (REST) vs Asynchronous (Kafka)
- Why use Kafka for notifications?
- Event sourcing concepts

### 3. Caching Strategy
**Be Ready to Explain:**
- Why cache portfolios?
- Cache invalidation strategy
- TTL (time-to-live) considerations

### 4. Resilience Patterns
**Be Ready to Explain:**
- Circuit breaker in API Gateway
- Retry logic
- Fallback responses

### 5. Technology Choices
**Be Ready to Explain:**
- MongoDB vs PostgreSQL - why NoSQL?
- Redis for caching - why not in-memory?
- Kafka vs RabbitMQ
- Spring Boot vs other frameworks

---

## Part 6: Hands-On Exercises

### Exercise 1: Trace a Request
Pick a portfolio ID and trace it through:
1. MongoDB database
2. Redis cache
3. API response
4. Frontend display

### Exercise 2: Trigger Kafka Event
Create a portfolio and watch:
1. Kafka logs in notification-service
2. Event payload structure
3. Consumer processing

### Exercise 3: Test Circuit Breaker
1. Stop portfolio-service: `docker stop portfolio-service`
2. Call API Gateway endpoint
3. Observe fallback response
4. Restart service and test again

### Exercise 4: Explore MongoDB
```powershell
docker exec -it mongodb-portfolio mongosh portfolio_db
db.portfolios.find().pretty()
```
Understand the document structure.

### Exercise 5: Check Redis Cache
```powershell
docker exec -it redis-cache redis-cli
KEYS *
GET "portfolio:YOUR_ID"
```
See cached data format.

---

## Part 7: Common Interview Questions & Answers

**Q: Why microservices instead of monolith?**
A: Scalability (scale services independently), technology flexibility, team autonomy, fault isolation.

**Q: How do you handle distributed transactions?**
A: Eventual consistency with events, saga pattern (compensating transactions).

**Q: How do you ensure data consistency?**
A: Events via Kafka, idempotent consumers, proper error handling.

**Q: Why MongoDB over SQL?**
A: Flexible schema for portfolios (different asset types), horizontal scalability, document model fits domain.

**Q: How do you test microservices?**
A: Unit tests (JUnit + Mockito), integration tests (TestContainers), contract tests, end-to-end tests.

**Q: How would you secure this system?**
A: OAuth2/JWT, API Gateway authentication, service-to-service auth, HTTPS, secrets management.

**Q: How do you monitor this in production?**
A: Spring Boot Actuator, Prometheus metrics, distributed tracing (Zipkin/Jaeger), centralized logging (ELK).

---

## Next Steps

After reading this overview:
1. Ask me to expand on any specific section
2. Request detailed walkthroughs of specific components
3. Get help with specific interview scenarios
4. Practice explaining architecture diagrams

**Suggested Next Prompts:**
- "Explain the Portfolio Service in detail"
- "Deep dive into Kafka implementation"
- "Show me the caching implementation"
- "Explain the testing strategy"
- "Help me prepare for system design questions"
