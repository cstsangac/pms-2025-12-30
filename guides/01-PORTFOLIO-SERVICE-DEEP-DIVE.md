# Portfolio Service - Deep Dive

## Table of Contents
1. [Service Overview](#service-overview)
2. [Project Structure](#project-structure)
3. [Domain Model](#domain-model)
4. [Controller Layer](#controller-layer)
5. [Service Layer](#service-layer)
6. [Repository Layer](#repository-layer)
7. [DTOs and Mapping](#dtos-and-mapping)
8. [Exception Handling](#exception-handling)
9. [Validation](#validation)
10. [Configuration](#configuration)

---

## Service Overview

### Purpose
The Portfolio Service is the **core business service** that manages client investment portfolios and their holdings. It's the heart of the wealth management platform.

### Key Responsibilities
- **Portfolio Management:** Create, read, update portfolios
- **Holdings Management:** Add/update/remove securities (stocks, bonds, ETFs)
- **Valuation:** Calculate total portfolio values
- **Event Publishing:** Notify other services of portfolio changes via Kafka
- **Caching:** Use Redis to improve read performance

### Architecture Role
- **Domain Owner:** Owns all portfolio and holding data
- **Event Producer:** Publishes portfolio lifecycle events to Kafka
- **Cache User:** Leverages Redis for frequently accessed portfolios
- **API Provider:** Exposes REST APIs for frontend and other services

### Technology Stack
- **Framework:** Spring Boot 3.2.1 with Java 17
- **Database:** MongoDB (portfolio_db collection)
- **Cache:** Redis for performance optimization
- **Messaging:** Kafka for event-driven architecture
- **Documentation:** OpenAPI 3.0 (Swagger UI)
- **Monitoring:** Spring Boot Actuator

### Port & Endpoints
- **Service Port:** 8081
- **Base Path:** `/api/portfolio`
- **Swagger UI:** http://localhost:8081/api/portfolio/swagger-ui.html
- **Health Check:** http://localhost:8081/api/portfolio/actuator/health

## Project Structure

### Directory Layout
```
portfolio-service/
├── src/
│   ├── main/
│   │   ├── java/com/wealthmanagement/portfolio/
│   │   │   ├── config/              # Configuration classes
│   │   │   │   ├── KafkaConfig.java
│   │   │   │   ├── MongoConfig.java
│   │   │   │   └── OpenAPIConfig.java
│   │   │   ├── controller/          # REST endpoints
│   │   │   │   └── PortfolioController.java
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   ├── HoldingDTO.java
│   │   │   │   └── PortfolioDTO.java
│   │   │   ├── event/               # Kafka event models
│   │   │   │   └── PortfolioEvent.java
│   │   │   ├── exception/           # Custom exceptions
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── PortfolioNotFoundException.java
│   │   │   │   └── ResourceAlreadyExistsException.java
│   │   │   ├── mapper/              # DTO ↔ Entity mapping
│   │   │   │   └── PortfolioMapper.java
│   │   │   ├── model/               # Domain entities
│   │   │   │   ├── Holding.java
│   │   │   │   └── Portfolio.java
│   │   │   ├── repository/          # Database layer
│   │   │   │   └── PortfolioRepository.java
│   │   │   ├── service/             # Business logic
│   │   │   │   └── PortfolioService.java
│   │   │   └── PortfolioServiceApplication.java  # Main class
│   │   └── resources/
│   │       └── application.yml      # Configuration
│   └── test/
│       └── java/com/wealthmanagement/portfolio/
│           ├── controller/          # Controller tests
│           ├── service/             # Service tests
│           └── repository/          # Repository tests
├── Dockerfile
└── pom.xml                          # Maven dependencies
```

### Package Organization (Layered Architecture)

**Controller Layer** → **Service Layer** → **Repository Layer** → **Database**

1. **Controller:** HTTP requests/responses
2. **Service:** Business logic, transactions, caching
3. **Repository:** Database operations
4. **Model:** Domain entities (Portfolio, Holding)
5. **DTO:** API contracts (request/response objects)
6. **Mapper:** Convert between DTOs and entities
7. **Event:** Kafka event payloads
8. **Exception:** Error handling
9. **Config:** Spring configuration beans

## Domain Model

### Portfolio Entity

**Location:** `model/Portfolio.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portfolios")
public class Portfolio {
    @Id
    private String id;                    // MongoDB generated ID
    
    private String clientId;              // External client identifier
    private String clientName;            // Client's full name
    private String accountNumber;         // Unique account number
    private String currency;              // USD, EUR, etc.
    
    private BigDecimal totalValue;        // Total portfolio value
    private BigDecimal cashBalance;       // Available cash
    
    private List<Holding> holdings;       // Embedded holdings
    
    private PortfolioStatus status;       // ACTIVE, INACTIVE, etc.
    
    @CreatedDate
    private LocalDateTime createdAt;      // Auto-populated
    
    @LastModifiedDate
    private LocalDateTime updatedAt;      // Auto-updated
}
```

**Key Annotations:**
- `@Document(collection = "portfolios")` - MongoDB collection name
- `@Id` - Primary key, MongoDB generates ObjectId as String
- `@CreatedDate` / `@LastModifiedDate` - Automatic timestamp management
- `@Data` - Lombok generates getters/setters/toString/equals/hashCode
- `@Builder` - Builder pattern for object creation

**Portfolio Status Enum:**
```java
public enum PortfolioStatus {
    ACTIVE,      // Normal operating status
    INACTIVE,    // Temporarily disabled
    SUSPENDED,   // Regulatory hold
    CLOSED       // Permanently closed
}
```

### Holding Entity (Embedded Document)

**Location:** `model/Holding.java`

Holding is **embedded** within Portfolio (not a separate collection):

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Holding {
    private String symbol;               // Stock ticker (AAPL, MSFT)
    private String name;                 // Company name
    private Integer quantity;            // Number of shares
    private BigDecimal purchasePrice;    // Cost basis per share
    private BigDecimal currentPrice;     // Current market price
    private BigDecimal marketValue;      // quantity × currentPrice
    private BigDecimal unrealizedGainLoss; // Profit/loss
    private BigDecimal unrealizedGainLossPercentage; // % gain/loss
    private AssetType assetType;         // STOCK, BOND, ETF, etc.
    private LocalDateTime purchaseDate;  // When acquired
}
```

**Asset Type Enum:**
```java
public enum AssetType {
    STOCK, BOND, ETF, MUTUAL_FUND, CRYPTOCURRENCY, COMMODITY
}
```

### Domain Relationships

```
Portfolio (1) ──── (many) Holding
   ↓
MongoDB Collection: portfolios
   └── holdings: [Array of embedded documents]
```

**Why Embedded?** Holdings belong to a portfolio and are always accessed together, making embedding more efficient than separate collections.

### Business Rules in the Model

1. **Total Value Calculation:** `totalValue = cashBalance + sum(all holdings.marketValue)`
2. **Holding Market Value:** `marketValue = quantity × currentPrice`
3. **Unrealized Gain/Loss:** `unrealizedGainLoss = (currentPrice - purchasePrice) × quantity`
4. **Default Values:** New portfolios start with `totalValue = cashBalance`, empty holdings list

## Controller Layer

**Location:** `controller/PortfolioController.java`

The controller handles HTTP requests and delegates to the service layer.

### Class Structure

```java
@Slf4j
@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
@Tag(name = "Portfolio Management", description = "APIs for managing client portfolios")
public class PortfolioController {
    private final PortfolioService portfolioService;
}
```

**Key Annotations:**
- `@RestController` - Combines @Controller + @ResponseBody (returns JSON)
- `@RequestMapping("/portfolios")` - Base path for all endpoints
- `@RequiredArgsConstructor` - Lombok generates constructor for final fields (dependency injection)
- `@Slf4j` - Lombok logging
- `@Tag` - OpenAPI documentation grouping

### Endpoints Overview

| HTTP Method | Endpoint | Purpose |
|------------|----------|---------|
| POST | `/portfolios` | Create new portfolio |
| GET | `/portfolios/{id}` | Get portfolio by ID |
| GET | `/portfolios/account/{accountNumber}` | Get by account number |
| GET | `/portfolios/client/{clientId}` | Get all portfolios for a client |
| GET | `/portfolios` | Get all portfolios (summary) |
| PUT | `/portfolios/{id}` | Update portfolio |
| POST | `/portfolios/{id}/holdings` | Add holding to portfolio |
| PUT | `/portfolios/{id}/holdings/{symbol}` | Update holding |
| DELETE | `/portfolios/{id}/holdings/{symbol}` | Remove holding |

### Detailed Endpoint Examples

#### 1. Create Portfolio
```java
@PostMapping
@Operation(summary = "Create a new portfolio")
@ApiResponses(value = {
    @ApiResponse(responseCode = "201", description = "Portfolio created"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "409", description = "Already exists")
})
public ResponseEntity<PortfolioDTO.Response> createPortfolio(
        @Valid @RequestBody PortfolioDTO.CreateRequest request) {
    
    log.info("REST request to create portfolio for client: {}", request.getClientId());
    PortfolioDTO.Response response = portfolioService.createPortfolio(request);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
}
```

**Key Points:**
- `@Valid` - Triggers bean validation on the request DTO
- Returns `201 CREATED` status
- OpenAPI annotations document the endpoint
- Logs the operation for monitoring

#### 2. Get Portfolio by ID
```java
@GetMapping("/{id}")
@Operation(summary = "Get portfolio by ID")
public ResponseEntity<PortfolioDTO.Response> getPortfolioById(
        @Parameter(description = "Portfolio ID") @PathVariable String id) {
    
    log.info("REST request to get portfolio: {}", id);
    PortfolioDTO.Response response = portfolioService.getPortfolioById(id);
    return ResponseEntity.ok(response);
}
```

**Key Points:**
- `@PathVariable` - Extracts ID from URL path
- Returns `200 OK` status
- Service layer handles caching (controller doesn't know about it)

#### 3. Get All Portfolios (Summary)
```java
@GetMapping
@Operation(summary = "Get all portfolios summary")
public ResponseEntity<List<PortfolioDTO.Summary>> getAllPortfoliosSummary() {
    log.info("REST request to get all portfolios summary");
    List<PortfolioDTO.Summary> summaries = portfolioService.getAllPortfoliosSummary();
    return ResponseEntity.ok(summaries);
}
```

**Why Summary?** Returns lightweight objects without full holdings array for performance.

#### 4. Add Holding
```java
@PostMapping("/{id}/holdings")
@Operation(summary = "Add holding to portfolio")
public ResponseEntity<PortfolioDTO.Response> addHolding(
        @PathVariable String id,
        @Valid @RequestBody HoldingDTO.CreateRequest request) {
    
    log.info("Adding holding {} to portfolio {}", request.getSymbol(), id);
    PortfolioDTO.Response response = portfolioService.addHolding(id, request);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
}
```

**Key Points:**
- Nested resource pattern: `/portfolios/{id}/holdings`
- Returns updated portfolio after adding holding
- Triggers cache eviction automatically

### Controller Responsibilities

✅ **Does:**
- Accept HTTP requests
- Validate input (`@Valid`)
- Log request info
- Call service layer
- Return appropriate HTTP status codes
- Document APIs with OpenAPI annotations

❌ **Does NOT:**
- Contain business logic
- Access database directly
- Handle caching
- Publish Kafka events
- Handle complex error scenarios (delegated to exception handler)

## Service Layer

**Location:** `service/PortfolioService.java`

This is where the **business logic** lives. The service orchestrates operations across multiple layers.

### Class Structure

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;
    
    private static final String PORTFOLIO_EVENTS_TOPIC = "portfolio-events";
}
```

**Dependencies (Constructor Injection):**
- `PortfolioRepository` - Database access
- `PortfolioMapper` - DTO ↔ Entity conversion
- `KafkaTemplate` - Event publishing

### Key Methods Deep Dive

#### 1. Create Portfolio
```java
@Transactional
public PortfolioDTO.Response createPortfolio(PortfolioDTO.CreateRequest request) {
    log.info("Creating portfolio for client: {}", request.getClientId());
    
    // 1. Check for duplicates
    if (portfolioRepository.existsByAccountNumber(request.getAccountNumber())) {
        throw new ResourceAlreadyExistsException(
            "Portfolio with account number already exists");
    }
    
    // 2. Map DTO to entity
    Portfolio portfolio = portfolioMapper.toEntity(request);
    
    // 3. Set business defaults
    portfolio.setTotalValue(request.getCashBalance());
    portfolio.setStatus(Portfolio.PortfolioStatus.ACTIVE);
    
    // 4. Save to database
    Portfolio saved = portfolioRepository.save(portfolio);
    
    // 5. Publish Kafka event
    publishEvent(PortfolioEvent.EventType.PORTFOLIO_CREATED, saved);
    
    // 6. Return response DTO
    return portfolioMapper.toResponse(saved);
}
```

**Key Concepts:**
- `@Transactional` - Database transaction boundaries
- **Validation** - Check account number uniqueness
- **Event Publishing** - Notify other services asynchronously
- **Separation of Concerns** - Controller doesn't know about Kafka/database

#### 2. Get Portfolio by ID (With Caching)
```java
@Cacheable(value = "portfolios", key = "#id")
public PortfolioDTO.Response getPortfolioById(String id) {
    log.debug("Fetching portfolio with ID: {}", id);
    
    Portfolio portfolio = portfolioRepository.findById(id)
        .orElseThrow(() -> new PortfolioNotFoundException(
            "Portfolio not found with ID: " + id));
    
    return portfolioMapper.toResponse(portfolio);
}
```

**Caching Logic:**
1. First call → Database query → Store in Redis cache
2. Subsequent calls → Return from cache (instant)
3. Cache key: `portfolios::6953bcf8336b5f7dcd43a2d6`

#### 3. Update Portfolio (Cache Eviction)
```java
@Transactional
@CacheEvict(value = "portfolios", key = "#id")
public PortfolioDTO.Response updatePortfolio(
        String id, PortfolioDTO.UpdateRequest request) {
    
    log.info("Updating portfolio with ID: {}", id);
    
    // 1. Fetch existing portfolio
    Portfolio portfolio = portfolioRepository.findById(id)
        .orElseThrow(() -> new PortfolioNotFoundException("Not found"));
    
    // 2. Apply updates from DTO
    portfolioMapper.updateEntityFromRequest(request, portfolio);
    
    // 3. Recalculate totals
    recalculateTotalValue(portfolio);
    
    // 4. Save changes
    Portfolio updated = portfolioRepository.save(portfolio);
    
    // 5. Publish update event
    publishEvent(PortfolioEvent.EventType.PORTFOLIO_UPDATED, updated);
    
    return portfolioMapper.toResponse(updated);
}
```

**Cache Eviction:**
- `@CacheEvict` removes stale data from Redis
- Next read will fetch fresh data and cache it again

#### 4. Add Holding
```java
@Transactional
@CacheEvict(value = "portfolios", key = "#portfolioId")
public PortfolioDTO.Response addHolding(
        String portfolioId, HoldingDTO.AddRequest request) {
    
    log.info("Adding holding {} to portfolio {}", 
        request.getSymbol(), portfolioId);
    
    Portfolio portfolio = findPortfolioOrThrow(portfolioId);
    
    // Check if holding already exists
    if (portfolio.getHoldings().stream()
            .anyMatch(h -> h.getSymbol().equals(request.getSymbol()))) {
        throw new ResourceAlreadyExistsException("Holding already exists");
    }
    
    // Create and add new holding
    Holding holding = portfolioMapper.toHoldingEntity(request);
    holding.calculateMarketValue();
    holding.calculateUnrealizedGainLoss();
    portfolio.getHoldings().add(holding);
    
    // Recalculate portfolio total
    recalculateTotalValue(portfolio);
    
    Portfolio updated = portfolioRepository.save(portfolio);
    publishEvent(PortfolioEvent.EventType.HOLDING_ADDED, updated);
    
    return portfolioMapper.toResponse(updated);
}
```

**Business Logic:**
- Validate no duplicate symbols
- Calculate holding metrics (market value, gain/loss)
- Update portfolio total value
- Publish event for downstream services

### Helper Methods

```java
private void recalculateTotalValue(Portfolio portfolio) {
    BigDecimal holdingsValue = portfolio.getHoldings().stream()
        .map(Holding::getMarketValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    portfolio.setTotalValue(
        portfolio.getCashBalance().add(holdingsValue));
}

private void publishEvent(PortfolioEvent.EventType type, Portfolio portfolio) {
    PortfolioEvent event = PortfolioEvent.builder()
        .eventType(type)
        .portfolioId(portfolio.getId())
        .clientName(portfolio.getClientName())
        .totalValue(portfolio.getTotalValue())
        .timestamp(LocalDateTime.now())
        .build();
    
    kafkaTemplate.send(PORTFOLIO_EVENTS_TOPIC, 
        portfolio.getId(), event);
    
    log.info("Published event: {} for portfolio: {}", 
        type, portfolio.getId());
}
```

### Service Responsibilities

✅ **Does:**
- Business logic and rules
- Transaction management (`@Transactional`)
- Orchestrate repository and mapper
- Publish Kafka events
- Manage caching (`@Cacheable`, `@CacheEvict`)
- Throw business exceptions
- Calculate values (totals, gains/losses)

❌ **Does NOT:**
- Handle HTTP concerns (status codes, headers)
- Know about JSON/XML serialization
- Directly manipulate MongoDB queries
- Handle authentication/authorization

## Repository Layer

**Location:** `repository/PortfolioRepository.java`

The repository is a simple interface that Spring Data MongoDB implements automatically.

### Interface Definition

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    Optional<Portfolio> findByAccountNumber(String accountNumber);
    
    List<Portfolio> findByClientId(String clientId);
    
    List<Portfolio> findByStatus(Portfolio.PortfolioStatus status);
    
    boolean existsByAccountNumber(String accountNumber);
}
```

### Spring Data MongoDB Magic

**Inherited from MongoRepository:**
- `save(Portfolio)` - Insert or update
- `findById(String)` - Find by primary key
- `findAll()` - Get all documents
- `delete(Portfolio)` - Remove document
- `count()` - Count documents
- `existsById(String)` - Check existence

**Custom Query Methods (Auto-implemented):**
Spring Data MongoDB generates the query from the method name:

```java
findByAccountNumber(String accountNumber)
// Translates to: db.portfolios.find({ accountNumber: "ACC-123" })

findByClientId(String clientId)
// Translates to: db.portfolios.find({ clientId: "CLIENT001" })

findByStatus(Portfolio.PortfolioStatus status)
// Translates to: db.portfolios.find({ status: "ACTIVE" })

existsByAccountNumber(String accountNumber)
// Translates to: db.portfolios.findOne({ accountNumber: "ACC-123" }) != null
```

### Method Naming Conventions

| Keyword | MongoDB Operation | Example |
|---------|------------------|----------|
| `findBy` | Find documents | `findByClientName(String name)` |
| `existsBy` | Check existence | `existsByClientId(String id)` |
| `countBy` | Count documents | `countByStatus(Status status)` |
| `deleteBy` | Remove documents | `deleteByAccountNumber(String number)` |

### Custom Queries (if needed)

For complex queries, you can use `@Query` annotation:

```java
@Query("{ 'totalValue': { $gte: ?0, $lte: ?1 } }")
List<Portfolio> findPortfoliosByValueRange(BigDecimal min, BigDecimal max);

@Query("{ 'holdings.symbol': ?0 }")
List<Portfolio> findPortfoliosWithSymbol(String symbol);
```

### Indexing for Performance

Add indexes in `MongoConfig.java` or use `@Indexed` annotation:

```java
@Document(collection = "portfolios")
public class Portfolio {
    @Indexed(unique = true)
    private String accountNumber;  // Unique index for fast lookups
    
    @Indexed
    private String clientId;  // Index for client queries
}
```

**Why Index?**
- `accountNumber` - Frequent lookups, must be unique
- `clientId` - Common query pattern (get all portfolios for a client)

## DTOs and Mapping

**Location:** `dto/PortfolioDTO.java`, `dto/HoldingDTO.java`, `mapper/PortfolioMapper.java`

### Why DTOs?

**Never expose domain entities directly in APIs!**

✅ **Benefits:**
- Decouple API contract from database schema
- Hide sensitive fields (internal IDs, audit fields)
- Different representations for different use cases (Summary vs Full)
- API versioning without changing domain model

### Portfolio DTOs

#### 1. CreateRequest
```java
public static class CreateRequest {
    @NotBlank(message = "Client ID is required")
    private String clientId;
    
    @NotBlank(message = "Client name is required")
    private String clientName;
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @NotNull(message = "Initial cash balance is required")
    @Positive(message = "Cash balance must be positive")
    private BigDecimal cashBalance;
}
```

**Validation Annotations:**
- `@NotBlank` - String must not be null, empty, or whitespace
- `@NotNull` - Field must be present
- `@Positive` - Number must be > 0

#### 2. Response (Full Details)
```java
public static class Response {
    private String id;
    private String clientId;
    private String clientName;
    private String accountNumber;
    private String currency;
    private BigDecimal totalValue;
    private BigDecimal cashBalance;
    private List<HoldingDTO.Response> holdings;  // Full holdings array
    private Portfolio.PortfolioStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Used for:** Single portfolio retrieval, detailed view.

#### 3. Summary (Lightweight)
```java
public static class Summary {
    private String id;
    private String clientId;
    private String clientName;
    private String accountNumber;
    private BigDecimal totalValue;
    private BigDecimal cashBalance;
    private Integer holdingsCount;  // Just count, not full array!
    private Portfolio.PortfolioStatus status;
}
```

**Used for:** List views, dashboard summaries.
**Performance:** Much faster - no need to load/serialize holdings array.

#### 4. UpdateRequest
```java
public static class UpdateRequest {
    private BigDecimal cashBalance;  // Optional
    private Portfolio.PortfolioStatus status;  // Optional
}
```

**Partial Update:** Only fields provided will be updated.

### Mapping with MapStruct

**Location:** `mapper/PortfolioMapper.java`

MapStruct is a **compile-time** code generator for bean mapping.

```java
@Mapper(componentModel = "spring")
public interface PortfolioMapper {
    
    // DTO → Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Portfolio toEntity(PortfolioDTO.CreateRequest request);
    
    // Entity → Response DTO
    PortfolioDTO.Response toResponse(Portfolio portfolio);
    
    // Entity → Summary DTO
    @Mapping(target = "holdingsCount", expression = "java(portfolio.getHoldings().size())")
    PortfolioDTO.Summary toSummary(Portfolio portfolio);
    
    // List mapping
    List<PortfolioDTO.Response> toResponseList(List<Portfolio> portfolios);
    
    // Update entity from DTO (partial update)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(PortfolioDTO.UpdateRequest request, @MappingTarget Portfolio portfolio);
}
```

**Key Features:**
- `@Mapper(componentModel = "spring")` - Generates Spring bean
- `@Mapping(target = "x", ignore = true)` - Don't map this field
- `@Mapping(target = "x", expression = "java(...)")` - Custom mapping logic
- `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` - Skip null fields in updates
- `@MappingTarget` - Update existing object instead of creating new one

### Mapping Flow Examples

**Create Portfolio:**
```
CreateRequest → toEntity() → Portfolio (new) → save() → toResponse() → Response
```

**Get Portfolio:**
```
findById() → Portfolio → toResponse() → Response
```

**List Portfolios:**
```
findAll() → List<Portfolio> → toSummary() → List<Summary>
```

**Update Portfolio:**
```
findById() → Portfolio → updateEntityFromRequest() → save() → toResponse()
```

## Exception Handling

**Location:** `exception/` package

### Custom Exceptions

#### PortfolioNotFoundException
```java
public class PortfolioNotFoundException extends RuntimeException {
    public PortfolioNotFoundException(String message) {
        super(message);
    }
}
```

**When thrown:** Portfolio ID not found, holding symbol not found.

#### ResourceAlreadyExistsException
```java
public class ResourceAlreadyExistsException extends RuntimeException {
    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
```

**When thrown:** Duplicate account number, duplicate holding symbol.

### Global Exception Handler

**Location:** `exception/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(PortfolioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePortfolioNotFound(PortfolioNotFoundException ex) {
        log.error("Portfolio not found: {}", ex.getMessage());
        return ErrorResponse.builder()
            .status(404)
            .error("Not Found")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleResourceAlreadyExists(ResourceAlreadyExistsException ex) {
        log.error("Resource already exists: {}", ex.getMessage());
        return ErrorResponse.builder()
            .status(409)
            .error("Conflict")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
        
        return ErrorResponse.builder()
            .status(400)
            .error("Validation Failed")
            .message("Invalid input")
            .details(errors)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ErrorResponse.builder()
            .status(500)
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

**Key Concepts:**
- `@RestControllerAdvice` - Global exception handling for all controllers
- `@ExceptionHandler(XException.class)` - Handle specific exception type
- `@ResponseStatus(HttpStatus.X)` - Set HTTP status code
- Converts exceptions to consistent JSON error responses

### Error Response Structure

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Portfolio not found with ID: abc123",
  "timestamp": "2025-12-30T12:00:00",
  "details": []
}
```

### Exception Flow Example

```
Controller: getPortfolioById("invalid-id")
    ↓
Service: portfolioRepository.findById("invalid-id")
    ↓
Repository: returns Optional.empty()
    ↓
Service: .orElseThrow(() → throws PortfolioNotFoundException)
    ↓
GlobalExceptionHandler: catches exception
    ↓
HTTP Response: 404 with JSON error body
```

## Validation

### Bean Validation (JSR-380)

Spring Boot uses **Hibernate Validator** to validate DTOs automatically.

### How It Works

1. **Add validation annotations to DTO:**
```java
public class CreateRequest {
    @NotBlank(message = "Client ID is required")
    private String clientId;
    
    @NotNull
    @Positive
    private BigDecimal cashBalance;
}
```

2. **Add @Valid to controller:**
```java
public ResponseEntity<Response> createPortfolio(
        @Valid @RequestBody CreateRequest request) {
    // Validation happens automatically before this code runs
}
```

3. **Validation fails → throws MethodArgumentNotValidException**
4. **GlobalExceptionHandler catches it → returns 400 Bad Request**

### Common Validation Annotations

| Annotation | Purpose | Example |
|------------|---------|----------|
| `@NotNull` | Value must not be null | `@NotNull BigDecimal amount` |
| `@NotBlank` | String not null/empty/whitespace | `@NotBlank String name` |
| `@NotEmpty` | Collection not null or empty | `@NotEmpty List<Holding>` |
| `@Size(min, max)` | String/collection size | `@Size(min=3, max=50) String name` |
| `@Min(value)` | Number ≥ value | `@Min(0) Integer quantity` |
| `@Max(value)` | Number ≤ value | `@Max(100) Integer percentage` |
| `@Positive` | Number > 0 | `@Positive BigDecimal price` |
| `@PositiveOrZero` | Number ≥ 0 | `@PositiveOrZero BigDecimal balance` |
| `@Email` | Valid email format | `@Email String email` |
| `@Pattern(regex)` | Matches regex | `@Pattern(regexp="[A-Z]+") String symbol` |

### Custom Validators

For complex business rules:

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AccountNumberValidator.class)
public @interface ValidAccountNumber {
    String message() default "Invalid account number format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class AccountNumberValidator 
        implements ConstraintValidator<ValidAccountNumber, String> {
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        return value.matches("^ACC-\\d{8}$");  // ACC-12345678
    }
}

// Usage:
public class CreateRequest {
    @ValidAccountNumber
    private String accountNumber;
}
```

### Validation Error Response

When validation fails:

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid input",
  "timestamp": "2025-12-30T12:00:00",
  "details": [
    "clientId: Client ID is required",
    "cashBalance: must be greater than 0"
  ]
}
```

## Configuration

**Location:** `src/main/resources/application.yml` and `config/` package

### application.yml

```yaml
spring:
  application:
    name: portfolio-service
  
  # MongoDB Configuration
  data:
    mongodb:
      uri: mongodb://localhost:27017/portfolio_db
      auto-index-creation: true
  
  # Redis Configuration
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
  
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hour in milliseconds
  
  # Kafka Configuration
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: portfolio-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"

server:
  port: 8081
  servlet:
    context-path: /api/portfolio

# Actuator Endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

# Logging
logging:
  level:
    com.wealthmanagement.portfolio: DEBUG
    org.springframework.data.mongodb: INFO
    org.springframework.kafka: INFO
```

### MongoDB Configuration

**Location:** `config/MongoConfig.java`

```java
@Configuration
@EnableMongoAuditing
public class MongoConfig {
    // @EnableMongoAuditing enables @CreatedDate and @LastModifiedDate
}
```

**Key Settings:**
- `auto-index-creation: true` - Automatically create indexes from `@Indexed` annotations
- `@EnableMongoAuditing` - Populates `createdAt` and `updatedAt` fields automatically

### Redis Cache Configuration

**Location:** `config/RedisConfig.java`

```java
@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // Cache expires after 1 hour
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
```

**Key Concepts:**
- `@EnableCaching` - Activates Spring's caching annotations
- `entryTtl` - How long cached items remain valid
- Serializers convert Java objects ↔ Redis (JSON format)

### Kafka Configuration

**Location:** `config/KafkaConfig.java`

```java
@Configuration
public class KafkaConfig {
    
    @Bean
    public NewTopic portfolioEventsTopic() {
        return TopicBuilder.name("portfolio-events")
            .partitions(3)      // 3 partitions for parallelism
            .replicas(1)        // Single broker (dev environment)
            .build();
    }
}
```

**Kafka Settings Explained:**
- `bootstrap-servers` - Kafka broker addresses
- `key-serializer` - How to serialize message keys (String)
- `value-serializer` - How to serialize message values (JSON)
- `group-id` - Consumer group for this service
- `auto-offset-reset: earliest` - Read from beginning if no offset exists

### OpenAPI Configuration

**Location:** `config/OpenAPIConfig.java`

```java
@Configuration
public class OpenAPIConfig {
    
    @Bean
    public OpenAPI portfolioServiceAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Portfolio Service API")
                .version("1.0")
                .description("Wealth management portfolio service")
                .contact(new Contact()
                    .name("Portfolio Team")
                    .email("portfolio@wealthmanagement.com")));
    }
}
```

### Environment-Specific Configuration

For different environments (dev, test, prod):

**application-dev.yml:**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/portfolio_db
  redis:
    host: localhost
logging:
  level:
    com.wealthmanagement: DEBUG
```

**application-prod.yml:**
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}  # Environment variable
  redis:
    host: ${REDIS_HOST}
logging:
  level:
    com.wealthmanagement: INFO
```

**Activate profile:**
```bash
java -jar portfolio-service.jar --spring.profiles.active=prod
```

### Configuration Properties

For custom configuration:

```java
@Configuration
@ConfigurationProperties(prefix = "portfolio")
@Data
public class PortfolioProperties {
    private int maxHoldingsPerPortfolio = 50;
    private BigDecimal minCashBalance = new BigDecimal("1000.00");
}
```

**application.yml:**
```yaml
portfolio:
  max-holdings-per-portfolio: 100
  min-cash-balance: 5000.00
```

---

## Summary

### Key Takeaways

1. **Layered Architecture:** Controller → Service → Repository → Database
2. **Dependency Injection:** Constructor injection with `@RequiredArgsConstructor`
3. **DTOs:** Never expose entities, use request/response DTOs
4. **Mapping:** MapStruct for DTO ↔ Entity conversion
5. **Validation:** Bean validation with `@Valid` and annotations
6. **Caching:** Redis with `@Cacheable` / `@CacheEvict`
7. **Events:** Kafka for async communication
8. **Exceptions:** Global exception handler for consistent errors
9. **Configuration:** YAML files + Java config classes

### Interview Talking Points

✔ **"Why separate DTOs from entities?"**  
→ Decouple API contract from database schema, hide sensitive fields, different representations.

✔ **"Why use Redis caching?"**  
→ Reduce database load, improve response time, portfolios are read-heavy.

✔ **"Why publish Kafka events?"**  
→ Loose coupling, async communication, event-driven architecture, notification service doesn't need to call portfolio service directly.

✔ **"How do you handle transactions?"**  
→ `@Transactional` on service methods, MongoDB doesn't support multi-document transactions by default but we use single-document atomicity.

✔ **"How would you scale this service?"**  
→ Horizontal scaling (multiple instances), stateless design, cache for reads, database sharding by clientId, Kafka partitions for events.

### Next Steps

- Practice explaining data flow for create/read/update operations
- Walk through code with debugger
- Try adding a new endpoint
- Review test files to understand testing patterns
- Read the Kafka guide next to understand event publishing
