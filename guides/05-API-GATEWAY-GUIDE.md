# API Gateway Deep Dive

## Table of Contents
1. [Gateway Role](#gateway-role)
2. [Spring Cloud Gateway](#spring-cloud-gateway)
3. [Route Configuration](#route-configuration)
4. [Predicates and Filters](#predicates-and-filters)
5. [Circuit Breaker](#circuit-breaker)
6. [CORS Configuration](#cors-configuration)
7. [Retry Logic](#retry-logic)
8. [Gateway Filters](#gateway-filters)

---

## Gateway Role

### What is an API Gateway?

An **API Gateway** is a single entry point for all client requests to backend microservices.

**Architecture:**
```
┌─────────┐
│ Browser │
│  Client │
└────┬────┘
     │
     ▼
┌────────────────┐
│  API Gateway   │  ← Single entry point (Port 8080)
│  (Port 8080)   │
└────┬───────────┘
     │
     ├──────────┬──────────┬──────────┐
     ▼          ▼          ▼          ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│Portfolio│ │Transaction│ │Notification│ │ Redis  │
│Service  │ │ Service │ │  Service │ │ Cache  │
│  :8081  │ │  :8082  │ │  :8083   │ │ :6379  │
└─────────┘ └─────────┘ └─────────┘ └─────────┘
```

### Why API Gateway?

**Without Gateway:**
```
Frontend connects to:
- http://localhost:8081/api/portfolio
- http://localhost:8082/api/transaction
- http://localhost:8083/api/notification

Problems:
❌ Multiple endpoints to manage
❌ CORS configuration on every service
❌ No centralized authentication
❌ Client knows internal structure
```

**With Gateway:**
```
Frontend connects to:
- http://localhost:8080/api/portfolio/*    → Routes to :8081
- http://localhost:8080/api/transaction/* → Routes to :8082
- http://localhost:8080/api/notification/* → Routes to :8083

Benefits:
✅ Single endpoint for all services
✅ Centralized CORS, auth, logging
✅ Service discovery and load balancing
✅ Circuit breaker pattern
✅ Hide internal architecture
```

### Key Benefits

**1. Single Entry Point**
- Clients call one URL: `http://api.wealthmanagement.com`
- Gateway routes to appropriate service

**2. Cross-Cutting Concerns**
- **Authentication/Authorization:** JWT validation, OAuth2
- **CORS:** Allow frontend origins
- **Rate Limiting:** Prevent abuse
- **Logging:** Centralized request/response logs
- **Metrics:** Request counts, latency

**3. Service Abstraction**
- Clients don't know about internal services
- Services can change ports/hosts without affecting clients
- Can refactor microservices without breaking clients

**4. Load Balancing**
- Distribute requests across multiple instances
- Route to healthy instances only

**5. Circuit Breaker**
- Protect services from cascading failures
- Return fallback responses when service is down

**6. Request/Response Transformation**
- Path rewriting: `/api/portfolio/*` → `/api/portfolio/*`
- Header manipulation: Add correlation IDs
- Payload transformation: Aggregate multiple service calls

### Real-World Analogy

Think of API Gateway as a **hotel receptionist**:
- **Guests (clients)** only talk to the receptionist
- **Receptionist (gateway)** routes to:
  - Room service (portfolio service)
  - Concierge (transaction service)
  - Housekeeping (notification service)
- Guests don't need to know internal departments
- Receptionist handles authentication (room key check)
- Can reroute if a service is unavailable

### When to Use API Gateway

✅ **Use Gateway when:**
- Multiple microservices
- External clients (web, mobile)
- Need centralized security
- Cross-cutting concerns (CORS, logging)

❌ **Skip Gateway when:**
- Single monolithic application
- Internal-only communication
- Very simple architecture
- Every millisecond matters (gateway adds ~5-10ms latency)

## Spring Cloud Gateway

### What is Spring Cloud Gateway?

**Spring Cloud Gateway** is a reactive, non-blocking API Gateway built on Spring WebFlux.

**Key Characteristics:**
- **Reactive:** Built on Project Reactor (non-blocking I/O)
- **WebFlux:** Uses Netty instead of Tomcat
- **High Performance:** Handles thousands of concurrent requests
- **Spring Ecosystem:** Integrates with Spring Boot, Spring Cloud

### Spring Cloud Gateway vs Alternatives

| Feature | Spring Cloud Gateway | Netflix Zuul | Kong | NGINX |
|---------|---------------------|--------------|------|-------|
| **Type** | Java, reactive | Java, blocking | Lua plugin | C, reverse proxy |
| **Performance** | High (non-blocking) | Medium (blocking) | Very High | Highest |
| **Spring Integration** | Native | Native (deprecated) | No | No |
| **Learning Curve** | Medium | Low | High | Low |
| **Use Case** | Spring microservices | Legacy Spring | Polyglot services | Any HTTP service |

**Why Spring Cloud Gateway for this project:**
- ✅ Native Spring Boot integration
- ✅ Reactive (better resource utilization)
- ✅ Built-in circuit breaker support
- ✅ Easy configuration (YAML)
- ✅ Active development (Zuul is deprecated)

### Reactive Architecture

**Traditional (Blocking - Zuul):**
```
Request → Thread 1 → Wait for service → Response
Request → Thread 2 → Wait for service → Response
Request → Thread 3 → Wait for service → Response
...
Request → Thread 100 → Wait... (thread exhaustion!)
```

**Problem:** Each request blocks a thread. Limited by thread pool size (e.g., 200 threads).

**Reactive (Non-blocking - Gateway):**
```
Request 1 → Event Loop → Forward → Continue processing others
Request 2 → Event Loop → Forward → Continue processing others
Request 3 → Event Loop → Forward → Continue processing others
...
1000s of requests with same resources!
```

**Benefit:** Threads don't block waiting for responses. Can handle more concurrent requests with fewer threads.

### WebFlux vs Spring MVC

**Spring MVC (Blocking):**
```java
@RestController
public class PortfolioController {
    
    @GetMapping("/portfolio/{id}")
    public ResponseEntity<Portfolio> getPortfolio(@PathVariable String id) {
        // Blocking call - thread waits here
        Portfolio portfolio = portfolioService.findById(id);
        return ResponseEntity.ok(portfolio);
    }
}
```

**WebFlux (Non-blocking):**
```java
@RestController
public class PortfolioController {
    
    @GetMapping("/portfolio/{id}")
    public Mono<ResponseEntity<Portfolio>> getPortfolio(@PathVariable String id) {
        // Non-blocking - returns immediately with Mono (promise)
        return portfolioService.findById(id)
            .map(ResponseEntity::ok);
    }
}
```

**Gateway uses WebFlux internally** for routing requests.

### Project Dependencies

**Location:** `api-gateway/pom.xml`

```xml
<dependencies>
    <!-- Spring Cloud Gateway (includes WebFlux, Netty) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- Circuit Breaker support -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
    </dependency>

    <!-- Actuator for monitoring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### Application Class

**Location:** `ApiGatewayApplication.java`

```java
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

**No special annotations needed!** Spring Cloud Gateway is auto-configured.

### How It Works

**Request Flow:**
```
1. Client Request
   ↓
2. Gateway Handler Mapping (match route predicates)
   ↓
3. Gateway Web Handler (apply filters)
   ↓
4. Proxy Request to Backend Service
   ↓
5. Backend Service Response
   ↓
6. Gateway Post-Filters (modify response)
   ↓
7. Client Response
```

**Example:**
```
GET http://localhost:8080/api/portfolio/PORT001

Gateway matches: Path=/api/portfolio/**
Applies filters: RewritePath, CircuitBreaker
Proxies to: http://portfolio-service:8081/api/portfolio/PORT001
Returns: Portfolio JSON
```

### Performance Benefits

**Benchmark (1000 concurrent requests):**

| Gateway Type | Threads Used | Memory | Throughput |
|--------------|-------------|--------|------------|
| **Zuul (Blocking)** | 200 | 512MB | 1,000 req/s |
| **Spring Cloud Gateway (Reactive)** | 8 | 256MB | 5,000 req/s |

**Non-blocking = 5x throughput with half the memory!**

## Route Configuration

### Basic Route Structure

**Location:** `api-gateway/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: portfolio-service              # Unique route identifier
          uri: http://portfolio-service:8081 # Target service URI
          predicates:
            - Path=/api/portfolio/**         # Match requests starting with this path
          filters:
            - RewritePath=/api/portfolio/(?<segment>.*), /api/portfolio/${segment}
```

**Route Components:**
- **id:** Unique identifier for the route
- **uri:** Backend service location (where to forward requests)
- **predicates:** Conditions to match incoming requests
- **filters:** Transformations to apply to request/response

### Complete Routes Configuration

**Location:** `application.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Portfolio Service Routes
        - id: portfolio-service
          uri: http://portfolio-service:8081
          predicates:
            - Path=/api/portfolio/**
          filters:
            - RewritePath=/api/portfolio/(?<segment>.*), /api/portfolio/${segment}
            - name: CircuitBreaker
              args:
                name: portfolioCircuitBreaker
                fallbackUri: forward:/fallback/portfolio

        # Transaction Service Routes
        - id: transaction-service
          uri: http://transaction-service:8082
          predicates:
            - Path=/api/transaction/**
          filters:
            - RewritePath=/api/transaction/(?<segment>.*), /api/transaction/${segment}
            - name: CircuitBreaker
              args:
                name: transactionCircuitBreaker
                fallbackUri: forward:/fallback/transaction

        # Notification Service Routes
        - id: notification-service
          uri: http://notification-service:8083
          predicates:
            - Path=/api/notification/**
          filters:
            - RewritePath=/api/notification/(?<segment>.*), /api/notification/${segment}
```

### How Routing Works

**Example Request Flow:**

```
Client Request:
GET http://localhost:8080/api/portfolio/portfolios/PORT001

Gateway Processing:
1. Match predicates: Path=/api/portfolio/** ✓
2. Select route: portfolio-service
3. Apply filters: RewritePath (no change in this case)
4. Apply CircuitBreaker filter
5. Forward to: http://portfolio-service:8081/api/portfolio/portfolios/PORT001

Backend Response:
{ "id": "PORT001", "clientId": "CLIENT123", ... }

Gateway Returns:
Same JSON to client
```

### URI Patterns

**Static URI (Development):**
```yaml
uri: http://localhost:8081  # Hardcoded host and port
```

**Service Name URI (Docker/Kubernetes):**
```yaml
uri: http://portfolio-service:8081  # Uses Docker service name
```

**Load Balanced URI (Multiple Instances):**
```yaml
uri: lb://portfolio-service  # lb:// enables client-side load balancing
```

**When to use each:**
- **Static:** Local development, single instance
- **Service Name:** Docker Compose, Docker network
- **Load Balanced:** Multiple service instances, Eureka/Consul

### Path Matching

**Exact Path:**
```yaml
predicates:
  - Path=/api/portfolio/health  # Only matches exact path
```

**Wildcard (single segment):**
```yaml
predicates:
  - Path=/api/portfolio/*  # Matches: /api/portfolio/PORT001
                           # Doesn't match: /api/portfolio/client/CLIENT123
```

**Ant-style (multiple segments):**
```yaml
predicates:
  - Path=/api/portfolio/**  # Matches: /api/portfolio/portfolios/PORT001
                            # Matches: /api/portfolio/client/CLIENT123/summary
```

### Multiple Predicates (AND Logic)

```yaml
routes:
  - id: secure-portfolio-route
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
      - Method=GET,POST          # Only GET and POST
      - Header=Authorization     # Must have Authorization header
      - After=2025-01-01T00:00:00Z  # Only after this date
```

**All predicates must match** for the route to be selected.

### Java-based Configuration

Alternative to YAML (useful for dynamic routes):

**Location:** `GatewayConfig.java`

```java
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("portfolio-service", r -> r
                .path("/api/portfolio/**")
                .filters(f -> f
                    .rewritePath("/api/portfolio/(?<segment>.*)", "/api/portfolio/${segment}")
                    .circuitBreaker(config -> config
                        .setName("portfolioCircuitBreaker")
                        .setFallbackUri("forward:/fallback/portfolio")))
                .uri("http://portfolio-service:8081"))
            
            .route("transaction-service", r -> r
                .path("/api/transaction/**")
                .filters(f -> f
                    .rewritePath("/api/transaction/(?<segment>.*)", "/api/transaction/${segment}"))
                .uri("http://transaction-service:8082"))
            
            .build();
    }
}
```

### Route Priority

When multiple routes match, **first match wins**.

```yaml
routes:
  # Specific route (checked first)
  - id: portfolio-health
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/health
    order: 1  # Lower number = higher priority

  # General route (checked second)
  - id: portfolio-service
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
    order: 2
```

**Best Practice:** Order routes from most specific to most general.

### Testing Routes

**Check active routes:**
```powershell
# Enable gateway actuator endpoint
curl http://localhost:8080/actuator/gateway/routes
```

**Response:**
```json
[
  {
    "route_id": "portfolio-service",
    "uri": "http://portfolio-service:8081",
    "order": 0,
    "predicate": "Paths: [/api/portfolio/**], match trailing slash: true",
    "filters": ["[[RewritePath ...], order = 1]"]
  }
]
```

## Predicates and Filters

### What are Predicates?

**Predicates** are conditions that must be true for a route to be selected.

**Think of it as:** `if (predicate matches) { use this route }`

### Built-in Predicates

**1. Path Predicate (Most Common)**
```yaml
predicates:
  - Path=/api/portfolio/**
```

**2. Method Predicate**
```yaml
predicates:
  - Method=GET,POST  # Only match GET and POST requests
```

**3. Header Predicate**
```yaml
predicates:
  - Header=X-Request-Id, \d+  # Must have header with regex pattern
```

**4. Query Parameter Predicate**
```yaml
predicates:
  - Query=userId, CLIENT\d+  # Must have userId param matching pattern
```

**5. Host Predicate**
```yaml
predicates:
  - Host=**.wealthmanagement.com  # Match subdomain
```

**6. Time-based Predicates**
```yaml
predicates:
  - After=2025-01-01T00:00:00Z    # Route active after date
  - Before=2025-12-31T23:59:59Z   # Route active before date
  - Between=2025-01-01T00:00:00Z,2025-12-31T23:59:59Z
```

**7. Cookie Predicate**
```yaml
predicates:
  - Cookie=session, [a-z]+  # Must have cookie matching pattern
```

**8. Remote Address Predicate**
```yaml
predicates:
  - RemoteAddr=192.168.1.0/24  # IP whitelist
```

### Combining Predicates

**All predicates must match (AND logic):**
```yaml
routes:
  - id: secure-portfolio-route
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**      # AND
      - Method=GET,POST              # AND
      - Header=Authorization, Bearer.*  # AND
```

### What are Filters?

**Filters** transform requests and responses as they pass through the gateway.

**Types:**
- **Pre-filters:** Modify request before forwarding (add headers, rewrite path)
- **Post-filters:** Modify response before returning to client (add headers, transform body)

### Built-in Filters

**1. RewritePath Filter**
```yaml
filters:
  - RewritePath=/api/portfolio/(?<segment>.*), /api/portfolio/${segment}
```

**Example:**
```
Client Request:  /api/portfolio/portfolios/PORT001
Gateway Rewrites: /api/portfolio/portfolios/PORT001  (no change in this case)
Backend Receives: /api/portfolio/portfolios/PORT001
```

**Another Example (Path Prefix Removal):**
```yaml
filters:
  - RewritePath=/red/(?<segment>.*), /${segment}
```

```
Client Request:  /red/api/portfolio/PORT001
Gateway Rewrites: /api/portfolio/PORT001
Backend Receives: /api/portfolio/PORT001
```

**2. AddRequestHeader Filter**
```yaml
filters:
  - AddRequestHeader=X-Request-Source, API-Gateway
  - AddRequestHeader=X-Correlation-Id, ${UUID}
```

**3. AddResponseHeader Filter**
```yaml
filters:
  - AddResponseHeader=X-Response-Time, ${ResponseTime}
```

**4. RemoveRequestHeader Filter**
```yaml
filters:
  - RemoveRequestHeader=Cookie  # Remove sensitive header before forwarding
```

**5. SetPath Filter**
```yaml
filters:
  - SetPath=/api/v2/portfolio/{id}  # Override entire path
```

**6. StripPrefix Filter**
```yaml
filters:
  - StripPrefix=2  # Remove first 2 segments
```

```
Client Request:  /api/v1/portfolio/PORT001
After StripPrefix=2: /portfolio/PORT001
Backend Receives: /portfolio/PORT001
```

**7. PrefixPath Filter**
```yaml
filters:
  - PrefixPath=/api  # Add prefix
```

```
Client Request:  /portfolio/PORT001
After PrefixPath=/api: /api/portfolio/PORT001
Backend Receives: /api/portfolio/PORT001
```

**8. SetStatus Filter**
```yaml
filters:
  - SetStatus=401  # Override response status
```

**9. Retry Filter**
```yaml
filters:
  - name: Retry
    args:
      retries: 3
      statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
      methods: GET,POST
      backoff:
        firstBackoff: 50ms
        maxBackoff: 500ms
        factor: 2
```

**10. RequestRateLimiter Filter**
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10  # Requests per second
      redis-rate-limiter.burstCapacity: 20
```

### Filter Order

Filters execute in this order:
1. **Default filters** (applied to all routes)
2. **Route-specific filters** (applied to matched route)

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - AddRequestHeader=X-Gateway, API-Gateway  # Applied to ALL routes
      
      routes:
        - id: portfolio-service
          uri: http://portfolio-service:8081
          predicates:
            - Path=/api/portfolio/**
          filters:
            - RewritePath=...  # Applied only to this route
            - CircuitBreaker=...
```

### Custom Filter Example

**Global Filter (Java):**
```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Pre-filter logic
        log.info("Incoming request: {} {}", request.getMethod(), request.getURI());
        
        // Continue filter chain
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // Post-filter logic
            ServerHttpResponse response = exchange.getResponse();
            log.info("Response status: {}", response.getStatusCode());
        }));
    }

    @Override
    public int getOrder() {
        return -1;  // Execute early
    }
}
```

### Real-World Filter Use Cases

**1. Add Correlation ID:**
```yaml
filters:
  - AddRequestHeader=X-Correlation-Id, ${UUID}
```

**2. API Versioning:**
```yaml
routes:
  - id: portfolio-v2
    uri: http://portfolio-service-v2:8081
    predicates:
      - Path=/api/v2/portfolio/**
    filters:
      - RewritePath=/api/v2/portfolio/(?<segment>.*), /api/portfolio/${segment}
```

**3. Security Headers:**
```yaml
filters:
  - AddResponseHeader=X-Content-Type-Options, nosniff
  - AddResponseHeader=X-Frame-Options, DENY
  - AddResponseHeader=X-XSS-Protection, 1; mode=block
```

**4. Response Caching:**
```yaml
filters:
  - AddResponseHeader=Cache-Control, max-age=3600
```

## Circuit Breaker

### What is a Circuit Breaker?

A **Circuit Breaker** protects your system from cascading failures when a downstream service is unavailable.

**Analogy:** Like an electrical circuit breaker in your home:
- When there's an overload, it **trips** (opens)
- Prevents damage to the entire system
- Can be **reset** when the problem is fixed

### The Problem Without Circuit Breaker

```
Client → Gateway → Portfolio Service (DOWN)
                   ↓ Timeout (30s)
                   ↓ Retry
                   ↓ Timeout (30s)
                   ↓ Retry
                   ↓ Timeout (30s)
Total wait: 90 seconds! ❌

Meanwhile:
- Gateway threads exhausted
- Other services affected
- System becomes unresponsive
```

### The Solution With Circuit Breaker

```
Client → Gateway → Portfolio Service (DOWN)
                   ↓ Circuit OPEN
                   ↓ Fallback response (instant)
Total wait: <100ms ✅

Benefits:
- Fast failure
- Fallback response
- System remains responsive
```

### Circuit Breaker States

```
           requests succeeding
                   │
    ┌──────────────▼──────────────┐
    │         CLOSED              │  ← Normal operation
    │   (Requests pass through)   │
    └──────────────┬──────────────┘
                   │ failure threshold reached
                   ▼
    ┌─────────────────────────────┐
    │          OPEN               │  ← Service down
    │   (Requests immediately     │
    │    fail, fallback used)     │
    └──────────────┬──────────────┘
                   │ wait duration elapsed
                   ▼
    ┌─────────────────────────────┐
    │       HALF_OPEN             │  ← Testing recovery
    │   (Allow limited requests)  │
    └──────────────┬──────────────┘
                   │ success? → CLOSED
                   │ failure? → OPEN
```

**States Explained:**

**1. CLOSED (Normal)**
- All requests pass through to service
- Success/failure tracked
- If failure rate > threshold → OPEN

**2. OPEN (Service Down)**
- Requests immediately fail
- Fallback response returned
- No requests to downstream service
- After wait duration → HALF_OPEN

**3. HALF_OPEN (Testing)**
- Allow limited requests through
- If successful → CLOSED
- If failed → OPEN

### Configuration

**Dependency:**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```

**Route Configuration:**
```yaml
routes:
  - id: portfolio-service
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
    filters:
      - name: CircuitBreaker
        args:
          name: portfolioCircuitBreaker
          fallbackUri: forward:/fallback/portfolio
```

**Resilience4j Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      portfolioCircuitBreaker:
        slidingWindowSize: 10                # Track last 10 requests
        failureRateThreshold: 50             # Open if 50% fail
        waitDurationInOpenState: 10000       # Wait 10s before HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3  # Test with 3 requests
        automaticTransitionFromOpenToHalfOpenEnabled: true
        slowCallDurationThreshold: 5000      # Consider call slow if > 5s
        slowCallRateThreshold: 50            # Open if 50% are slow
```

### Fallback Controller

**Location:** `FallbackController.java`

```java
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/portfolio")
    public ResponseEntity<Map<String, Object>> portfolioFallback() {
        log.warn("Portfolio service fallback triggered");
        return buildFallbackResponse("Portfolio service is currently unavailable. Please try again later.");
    }

    @GetMapping("/transaction")
    public ResponseEntity<Map<String, Object>> transactionFallback() {
        log.warn("Transaction service fallback triggered");
        return buildFallbackResponse("Transaction service is currently unavailable. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", message);

        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
```

### How It Works

**Normal Flow (Circuit CLOSED):**
```
GET /api/portfolio/PORT001
→ Gateway routes to portfolio-service:8081
→ Service responds: { "id": "PORT001", ... }
→ Client receives: 200 OK
```

**Failure Flow (Circuit OPEN):**
```
GET /api/portfolio/PORT001
→ Circuit Breaker detects OPEN state
→ Immediately forward to fallback
→ FallbackController responds:
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Portfolio service is currently unavailable. Please try again later."
}
→ Client receives: 503 Service Unavailable (instantly!)
```

### Testing Circuit Breaker

**1. Stop the service:**
```powershell
docker stop portfolio-service
```

**2. Make requests:**
```powershell
# First few requests - slow failures
curl http://localhost:8080/api/portfolio/portfolios/PORT001
# Response: Timeout after 5s

# After failure threshold - instant fallback
curl http://localhost:8080/api/portfolio/portfolios/PORT001
# Response: Fallback (instant!)
```

**3. Check circuit state:**
```powershell
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "portfolioCircuitBreaker": {
          "status": "OPEN",  ← Circuit is OPEN
          "failureRate": "75.0%"
        }
      }
    }
  }
}
```

### Circuit Breaker Patterns

**1. Fail Fast (Current Implementation)**
```yaml
filters:
  - name: CircuitBreaker
    args:
      fallbackUri: forward:/fallback/portfolio
```

**2. Fallback to Cache**
```java
@GetMapping("/fallback/portfolio/{id}")
public ResponseEntity<Portfolio> portfolioFallbackWithCache(@PathVariable String id) {
    // Try cache first
    Portfolio cached = cacheService.get(id);
    if (cached != null) {
        return ResponseEntity.ok(cached);
    }
    return ResponseEntity.status(503).build();
}
```

**3. Fallback to Secondary Service**
```yaml
filters:
  - name: CircuitBreaker
    args:
      fallbackUri: forward:/api/portfolio-backup  # Route to backup service
```

### Monitoring Circuit Breakers

**Actuator Endpoints:**
```powershell
# Circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# All metrics
curl http://localhost:8080/actuator/circuitbreakers
```

**Enable in application.yml:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,circuitbreakers
  health:
    circuitbreakers:
      enabled: true
```

### Best Practices

✅ **DO:**
- Set reasonable thresholds (50% failure rate)
- Provide meaningful fallback responses
- Log when circuit opens (alerts)
- Monitor circuit state in production
- Test circuit breaker behavior

❌ **DON'T:**
- Set threshold too low (opens on minor issues)
- Return generic errors (confusing for users)
- Ignore circuit breaker metrics
- Use same circuit for all routes (one failure affects all)

## CORS Configuration

### What is CORS?

**CORS (Cross-Origin Resource Sharing)** is a security mechanism that controls which origins can access your API.

**Problem:**
```
Frontend: http://localhost:3000
Backend:  http://localhost:8080

Browser blocks request:
❌ "Access to XMLHttpRequest has been blocked by CORS policy"
```

**Why?** Browsers enforce **Same-Origin Policy** for security:
- Same origin: `http://localhost:3000` → `http://localhost:3000/api` ✅
- Different origin: `http://localhost:3000` → `http://localhost:8080/api` ❌

### CORS Headers

**Browser sends preflight request:**
```http
OPTIONS /api/portfolio/portfolios HTTP/1.1
Host: localhost:8080
Origin: http://localhost:3000
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type
```

**Server must respond:**
```http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Max-Age: 3600
```

**Then browser sends actual request.**

### Global CORS Configuration

**Location:** `application.yml`

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':  # Apply to all routes
            allowedOrigins: "*"  # Allow all origins (dev only!)
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"  # Allow all headers
            exposedHeaders:
              - Content-Type
              - Authorization
            maxAge: 3600  # Cache preflight response for 1 hour
```

### Production CORS Configuration

**Restrict to specific origins:**
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - http://localhost:3000  # Local dev
              - https://app.wealthmanagement.com  # Production frontend
              - https://staging.wealthmanagement.com  # Staging
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - Content-Type
              - Authorization
              - X-Requested-With
            allowCredentials: true  # Allow cookies/auth headers
            maxAge: 3600
```

### Route-Specific CORS

**Different CORS for different routes:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: public-portfolio-route
          uri: http://portfolio-service:8081
          predicates:
            - Path=/api/public/portfolio/**
          metadata:
            cors:
              allowedOrigins: "*"  # Public API - allow all
              allowedMethods: [GET]
        
        - id: admin-route
          uri: http://portfolio-service:8081
          predicates:
            - Path=/api/admin/**
          metadata:
            cors:
              allowedOrigins: "https://admin.wealthmanagement.com"  # Admin only
              allowedMethods: [GET, POST, PUT, DELETE]
              allowCredentials: true
```

### Java-Based CORS Configuration

**Alternative to YAML:**

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow specific origins
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "https://app.wealthmanagement.com"
        ));
        
        // Allow specific methods
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        
        // Allow specific headers
        config.setAllowedHeaders(Arrays.asList(
            "Content-Type", "Authorization", "X-Requested-With"
        ));
        
        // Expose headers
        config.setExposedHeaders(Arrays.asList(
            "Content-Type", "Authorization"
        ));
        
        // Allow credentials
        config.setAllowCredentials(true);
        
        // Cache preflight
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }
}
```

### CORS Configuration Options

| Option | Description | Example |
|--------|-------------|---------|
| **allowedOrigins** | Allowed origins | `["http://localhost:3000"]` |
| **allowedOriginPatterns** | Pattern-based origins | `["https://*.wealthmanagement.com"]` |
| **allowedMethods** | HTTP methods | `["GET", "POST"]` |
| **allowedHeaders** | Request headers | `["Content-Type", "Authorization"]` |
| **exposedHeaders** | Response headers visible to client | `["X-Total-Count"]` |
| **allowCredentials** | Allow cookies/auth | `true` |
| **maxAge** | Preflight cache duration (seconds) | `3600` |

### Testing CORS

**Preflight Request:**
```powershell
curl -X OPTIONS http://localhost:8080/api/portfolio/portfolios `
  -H "Origin: http://localhost:3000" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: Content-Type" `
  -v
```

**Expected Response:**
```http
< HTTP/1.1 200 OK
< Access-Control-Allow-Origin: http://localhost:3000
< Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
< Access-Control-Allow-Headers: Content-Type
< Access-Control-Max-Age: 3600
```

### Common CORS Issues

**Problem 1: Wildcard with credentials**
```yaml
allowedOrigins: "*"
allowCredentials: true  # ❌ Error! Cannot use both
```

**Solution:**
```yaml
allowedOrigins:
  - http://localhost:3000
allowCredentials: true  # ✅ Specific origin required
```

**Problem 2: Missing OPTIONS method**
```yaml
allowedMethods:
  - GET
  - POST
  # ❌ Missing OPTIONS for preflight
```

**Solution:**
```yaml
allowedMethods:
  - GET
  - POST
  - OPTIONS  # ✅ Required for preflight
```

**Problem 3: Missing headers**
```yaml
allowedHeaders:
  - Content-Type
  # ❌ Missing Authorization
```

**Solution:**
```yaml
allowedHeaders:
  - Content-Type
  - Authorization  # ✅ Include all headers used by frontend
```

### Frontend Integration

**React Fetch with CORS:**
```javascript
// Frontend running on http://localhost:3000
fetch('http://localhost:8080/api/portfolio/portfolios', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  credentials: 'include'  // Include cookies
})
.then(response => response.json())
.then(data => console.log(data));
```

**Axios with CORS:**
```javascript
axios.get('http://localhost:8080/api/portfolio/portfolios', {
  headers: {
    'Authorization': 'Bearer ' + token
  },
  withCredentials: true  // Include cookies
});
```

### Security Best Practices

✅ **DO:**
- Specify exact origins in production
- Use `allowCredentials: true` only when needed
- Limit allowed methods to what's actually used
- Keep `maxAge` reasonable (1 hour)
- Use HTTPS in production

❌ **DON'T:**
- Use `allowedOrigins: "*"` in production
- Allow all headers unnecessarily
- Set `maxAge` too high (cache pollution)
- Forget to include OPTIONS method

## Retry Logic

### What is Retry Logic?

**Retry Logic** automatically retries failed requests to handle transient failures.

**Transient Failures (temporary issues):**
- Network blip
- Service temporarily busy
- Brief connection timeout
- Load balancer switching instances

### Why Retry?

**Without Retry:**
```
Request → Service (network blip) → FAIL ❌
User sees error (even though service is fine)
```

**With Retry:**
```
Request → Service (network blip) → FAIL
        → Retry → Service → SUCCESS ✅
User never sees the error
```

### Default Retry Filter

**Location:** `application.yml`

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: Retry
          args:
            retries: 3
            statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
            methods: GET,POST,PUT,DELETE
            backoff:
              firstBackoff: 50ms
              maxBackoff: 500ms
              factor: 2
              basedOnPreviousValue: false
```

### Retry Configuration Options

| Option | Description | Example |
|--------|-------------|---------|
| **retries** | Number of retry attempts | `3` |
| **statuses** | HTTP statuses to retry | `BAD_GATEWAY`, `GATEWAY_TIMEOUT` |
| **methods** | HTTP methods to retry | `GET`, `POST` |
| **series** | Status series to retry | `SERVER_ERROR` (5xx) |
| **exceptions** | Exceptions to retry | `IOException`, `TimeoutException` |
| **backoff.firstBackoff** | Initial wait time | `50ms` |
| **backoff.maxBackoff** | Maximum wait time | `500ms` |
| **backoff.factor** | Multiplier for each retry | `2` (exponential) |
| **backoff.basedOnPreviousValue** | Use previous backoff for calculation | `false` |

### Backoff Strategies

**1. Fixed Backoff**
```yaml
backoff:
  firstBackoff: 100ms
  maxBackoff: 100ms
  factor: 1  # No increase
```

**Retry Timing:**
- Attempt 1: Fail → Wait 100ms
- Attempt 2: Fail → Wait 100ms
- Attempt 3: Fail → Wait 100ms
- Total wait: 300ms

**2. Exponential Backoff (Recommended)**
```yaml
backoff:
  firstBackoff: 50ms
  maxBackoff: 500ms
  factor: 2
```

**Retry Timing:**
- Attempt 1: Fail → Wait 50ms
- Attempt 2: Fail → Wait 100ms (50 × 2)
- Attempt 3: Fail → Wait 200ms (100 × 2)
- Attempt 4: Fail → Wait 400ms (200 × 2)
- Total wait: 750ms

**Why exponential?** Gives service time to recover, reduces load during issues.

### HTTP Status Codes to Retry

**Should Retry (Transient):**
- `502 Bad Gateway` - Upstream service temporarily unavailable
- `503 Service Unavailable` - Service overloaded
- `504 Gateway Timeout` - Upstream timeout
- `429 Too Many Requests` - Rate limit (with backoff)

**Should NOT Retry (Permanent):**
- `400 Bad Request` - Invalid input (won't fix itself)
- `401 Unauthorized` - Missing/invalid auth
- `403 Forbidden` - No permission
- `404 Not Found` - Resource doesn't exist
- `409 Conflict` - Business logic error

### HTTP Methods to Retry

**Safe to Retry (Idempotent):**
```yaml
methods: GET,PUT,DELETE
```

- **GET** - Read operation, safe to retry
- **PUT** - Idempotent (same result if called multiple times)
- **DELETE** - Idempotent

**Careful with Retry (Non-idempotent):**
```yaml
methods: POST  # Use with caution!
```

- **POST** - May create duplicate resources if retried
- **Solution:** Use idempotency keys

**Example:**
```http
POST /api/transactions
Content-Type: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

{
  "portfolioId": "PORT001",
  "type": "BUY",
  "symbol": "AAPL",
  "quantity": 100
}
```

### Route-Specific Retry

**Different retry for different routes:**

```yaml
routes:
  # Read-heavy service - aggressive retry
  - id: portfolio-service
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
    filters:
      - name: Retry
        args:
          retries: 5
          statuses: BAD_GATEWAY,GATEWAY_TIMEOUT,SERVICE_UNAVAILABLE
          methods: GET
          backoff:
            firstBackoff: 50ms
            maxBackoff: 1000ms
            factor: 2

  # Write-heavy service - conservative retry
  - id: transaction-service
    uri: http://transaction-service:8082
    predicates:
      - Path=/api/transaction/**
    filters:
      - name: Retry
        args:
          retries: 2
          statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
          methods: GET  # Only retry GET, not POST
          backoff:
            firstBackoff: 100ms
            maxBackoff: 500ms
            factor: 2
```

### Retry with Circuit Breaker

**Combine for resilience:**

```yaml
routes:
  - id: portfolio-service
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
    filters:
      # 1. Try request
      - name: Retry
        args:
          retries: 3
          statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
          methods: GET
          backoff:
            firstBackoff: 50ms
            maxBackoff: 500ms
            factor: 2
      
      # 2. If retries exhausted, use circuit breaker
      - name: CircuitBreaker
        args:
          name: portfolioCircuitBreaker
          fallbackUri: forward:/fallback/portfolio
```

**Flow:**
```
Request → Retry (attempt 1) → FAIL (502)
        → Retry (attempt 2) → FAIL (502)
        → Retry (attempt 3) → FAIL (502)
        → Retries exhausted
        → Circuit Breaker increments failure count
        → If threshold reached → Circuit OPEN
        → Fallback response
```

### Testing Retry Logic

**1. Simulate transient failure:**

```powershell
# Stop service briefly
docker stop portfolio-service

# Make request (will fail initially)
curl http://localhost:8080/api/portfolio/portfolios/PORT001

# Quickly start service (before retries exhausted)
docker start portfolio-service

# Request should succeed after retry
```

**2. Monitor retry attempts:**

Enable debug logging:
```yaml
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    reactor.netty.http.client: DEBUG
```

**Log output:**
```
DEBUG - Retry attempt 1 for request GET /api/portfolio/portfolios/PORT001
DEBUG - Retry attempt 2 for request GET /api/portfolio/portfolios/PORT001
DEBUG - Retry attempt 3 for request GET /api/portfolio/portfolios/PORT001
DEBUG - Request succeeded on retry attempt 3
```

### Retry Metrics

**Track retry performance:**

```yaml
management:
  metrics:
    tags:
      application: api-gateway
```

**Metrics to monitor:**
- `retry.attempts.total` - Total retry attempts
- `retry.success.total` - Successful retries
- `retry.exhausted.total` - Failed after all retries

### Best Practices

✅ **DO:**
- Use exponential backoff (reduces load)
- Retry only idempotent operations
- Set reasonable max retries (3-5)
- Retry only transient errors (5xx)
- Use idempotency keys for POST

❌ **DON'T:**
- Retry forever (set max retries)
- Retry client errors (4xx)
- Use retry without circuit breaker
- Retry without backoff (hammers service)
- Retry non-idempotent operations without protection

### Advanced: Custom Retry Logic

**Java Configuration:**

```java
@Configuration
public class RetryConfig {

    @Bean
    public RetryGatewayFilterFactory retryGatewayFilterFactory() {
        return new RetryGatewayFilterFactory() {
            @Override
            public Retry apply(Config config) {
                return Retry.of("custom-retry", RetryConfig.<Throwable>custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(50))
                    .retryOnException(e -> 
                        e instanceof IOException || 
                        e instanceof TimeoutException
                    )
                    .build());
            }
        };
    }
}
```

### Retry vs Circuit Breaker

| Aspect | Retry | Circuit Breaker |
|--------|-------|-----------------|
| **Purpose** | Handle transient failures | Prevent cascading failures |
| **When to use** | Service briefly down | Service consistently down |
| **Action** | Try again immediately/with backoff | Fail fast, return fallback |
| **Duration** | Milliseconds-seconds | Minutes (until service recovers) |

**Use Together:** Retry for quick recovery, circuit breaker for prolonged outages.

## Gateway Filters

### Filter Types

**1. Pre-Filters (Request Filters)**
- Execute **before** request is sent to backend
- Modify request (headers, path, body)
- Examples: Authentication, logging, rate limiting

**2. Post-Filters (Response Filters)**
- Execute **after** response from backend
- Modify response (headers, status, body)
- Examples: Add security headers, compress response

### Filter Execution Flow

```
Client Request
    ↓
┌───────────────────┐
│  Global Filters   │ ← Applied to ALL routes
│  (Pre-filters)    │
└────────┬──────────┘
         ↓
┌───────────────────┐
│  Route Filters    │ ← Applied to matched route
│  (Pre-filters)    │
└────────┬──────────┘
         ↓
    Backend Service
         ↓
┌───────────────────┐
│  Route Filters    │ ← Applied to matched route
│  (Post-filters)   │
└────────┬──────────┘
         ↓
┌───────────────────┐
│  Global Filters   │ ← Applied to ALL routes
│  (Post-filters)   │
└────────┬──────────┘
         ↓
   Client Response
```

### Global vs Route Filters

**Global Filters (Applied to All Routes):**
```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - AddRequestHeader=X-Gateway, API-Gateway
        - AddResponseHeader=X-Response-Time, ${ResponseTime}
        - name: Retry
          args:
            retries: 3
```

**Route Filters (Applied to Specific Route):**
```yaml
routes:
  - id: portfolio-service
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
    filters:
      - RewritePath=/api/portfolio/(?<segment>.*), /api/portfolio/${segment}
      - CircuitBreaker=portfolioCircuitBreaker
```

### Built-in Pre-Filters

**1. AddRequestHeader**
```yaml
filters:
  - AddRequestHeader=X-Request-Id, ${UUID}
  - AddRequestHeader=X-Request-Source, API-Gateway
  - AddRequestHeader=X-Forwarded-For, ${RemoteAddr}
```

**Use Case:** Add correlation IDs for request tracing

**2. AddRequestParameter**
```yaml
filters:
  - AddRequestParameter=source, gateway
```

**Transforms:**
```
Request: GET /api/portfolio/portfolios
Gateway: GET /api/portfolio/portfolios?source=gateway
```

**3. RemoveRequestHeader**
```yaml
filters:
  - RemoveRequestHeader=Cookie  # Remove sensitive data
  - RemoveRequestHeader=X-Internal-Auth
```

**4. SetRequestHeader**
```yaml
filters:
  - SetRequestHeader=X-User-Id, ${claims.sub}  # Override existing header
```

**5. ModifyRequestBody**
```yaml
filters:
  - name: ModifyRequestBody
    args:
      inClass: java.lang.String
      outClass: java.lang.String
      rewriteFunction: com.example.RequestBodyRewriter
```

**6. PrefixPath**
```yaml
filters:
  - PrefixPath=/v1  # Add /v1 to all requests
```

### Built-in Post-Filters

**1. AddResponseHeader**
```yaml
filters:
  - AddResponseHeader=X-Response-Time, ${ResponseTime}
  - AddResponseHeader=X-Powered-By, Spring-Cloud-Gateway
  - AddResponseHeader=Cache-Control, max-age=3600
```

**2. RemoveResponseHeader**
```yaml
filters:
  - RemoveResponseHeader=Server  # Hide server info
  - RemoveResponseHeader=X-Application-Context
```

**3. SetResponseHeader**
```yaml
filters:
  - SetResponseHeader=X-Custom-Header, CustomValue
```

**4. SetStatus**
```yaml
filters:
  - SetStatus=200  # Override response status
```

**5. DedupeResponseHeader**
```yaml
filters:
  - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials
```

**Removes duplicate headers** (useful when both gateway and service add same header)

**6. RedirectTo**
```yaml
filters:
  - RedirectTo=302, https://wealthmanagement.com/login
```

### Custom Global Filter (Java)

**Location:** `LoggingFilter.java`

```java
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Pre-filter: Log incoming request
        String requestId = UUID.randomUUID().toString();
        log.info("Request [{}]: {} {}", 
            requestId, 
            request.getMethod(), 
            request.getURI());
        
        long startTime = System.currentTimeMillis();
        
        // Add request ID to headers
        ServerHttpRequest modifiedRequest = request.mutate()
            .header("X-Request-Id", requestId)
            .build();
        
        ServerWebExchange modifiedExchange = exchange.mutate()
            .request(modifiedRequest)
            .build();
        
        // Continue filter chain
        return chain.filter(modifiedExchange)
            .then(Mono.fromRunnable(() -> {
                // Post-filter: Log response
                long duration = System.currentTimeMillis() - startTime;
                ServerHttpResponse response = exchange.getResponse();
                
                log.info("Response [{}]: {} - {}ms", 
                    requestId, 
                    response.getStatusCode(), 
                    duration);
                
                // Add duration header
                response.getHeaders().add("X-Response-Time", duration + "ms");
            }));
    }

    @Override
    public int getOrder() {
        return -1;  // Execute early (negative = higher priority)
    }
}
```

### Custom Gateway Filter Factory

**For route-specific custom filters:**

```java
@Component
public class AuthenticationGatewayFilterFactory 
    extends AbstractGatewayFilterFactory<AuthenticationGatewayFilterFactory.Config> {

    public AuthenticationGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // Validate JWT token
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            String token = authHeader.substring(7);
            if (!validateToken(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            
            // Add user info to request
            String userId = extractUserId(token);
            ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId)
                .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private boolean validateToken(String token) {
        // JWT validation logic
        return true;
    }

    private String extractUserId(String token) {
        // Extract user ID from JWT
        return "USER123";
    }

    public static class Config {
        private String headerName;
        // Getters/setters
    }
}
```

**Usage in YAML:**
```yaml
routes:
  - id: secure-route
    uri: http://portfolio-service:8081
    predicates:
      - Path=/api/portfolio/**
    filters:
      - Authentication  # Custom filter
```

### Filter Order

**Order determines execution sequence:**

```java
@Component
public class FirstFilter implements GlobalFilter, Ordered {
    @Override
    public int getOrder() {
        return 1;  // Executes first
    }
}

@Component
public class SecondFilter implements GlobalFilter, Ordered {
    @Override
    public int getOrder() {
        return 2;  // Executes second
    }
}
```

**Built-in Filter Orders:**
- `-2147483648` (Integer.MIN_VALUE) - Highest priority
- `-1` - Very high
- `0` - Default
- `1` - Low
- `2147483647` (Integer.MAX_VALUE) - Lowest priority

### Real-World Filter Examples

**1. Request Logging with Correlation ID**
```java
@Component
@Slf4j
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        String correlationId = request.getHeaders()
            .getFirst(CORRELATION_ID_HEADER);
        
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        ServerHttpRequest modifiedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build();
        
        log.info("[{}] {} {}", 
            correlationId, 
            request.getMethod(), 
            request.getURI());
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;  // Execute early
    }
}
```

**2. Security Headers Filter**
```java
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();
            
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
            headers.add("X-XSS-Protection", "1; mode=block");
            headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            headers.add("Content-Security-Policy", "default-src 'self'");
        }));
    }

    @Override
    public int getOrder() {
        return 100;  // Execute late (post-filter)
    }
}
```

**3. Rate Limiting Filter**
```java
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        
        AtomicInteger count = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        
        if (count.incrementAndGet() > 100) {  // 100 requests per minute
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -50;  // Execute early
    }
}
```

### Filter Best Practices

✅ **DO:**
- Keep filters lightweight (avoid blocking operations)
- Use appropriate order (negative for pre-filters)
- Log important events (requests, errors)
- Handle errors gracefully
- Use global filters for cross-cutting concerns

❌ **DON'T:**
- Make database calls in filters (blocks event loop)
- Modify request/response body unless necessary
- Throw exceptions (return error responses instead)
- Use filters for business logic (belongs in services)
- Create too many filters (performance impact)

### Testing Filters

**Unit Test Example:**
```java
@Test
void shouldAddCorrelationId() {
    CorrelationIdFilter filter = new CorrelationIdFilter();
    
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/portfolio").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    
    when(chain.filter(any())).thenReturn(Mono.empty());
    
    filter.filter(exchange, chain).block();
    
    String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
    assertThat(correlationId).isNotNull();
}
```

Guide 05 (API Gateway) is now complete with all 8 sections filled!
