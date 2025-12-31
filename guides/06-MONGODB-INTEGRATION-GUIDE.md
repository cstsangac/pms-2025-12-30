# MongoDB Integration Guide

## Table of Contents
1. [NoSQL Concepts](#nosql-concepts)
2. [MongoDB Setup](#mongodb-setup)
3. [Domain Modeling](#domain-modeling)
4. [Spring Data MongoDB](#spring-data-mongodb)
5. [Repositories](#repositories)
6. [Queries](#queries)
7. [Indexes](#indexes)
8. [Aggregations](#aggregations)

---

## NoSQL Concepts

### Document Database

**MongoDB** is a NoSQL document database that stores data in flexible, JSON-like documents.

**Key Characteristics:**
- **Schema-less:** Documents in the same collection can have different fields
- **Embedded Documents:** Related data can be nested (denormalization)
- **Horizontal Scalability:** Sharding for distributed data
- **Flexible Queries:** Rich query language and indexing

### SQL vs NoSQL Comparison

| Aspect | SQL (Relational) | MongoDB (Document) |
|--------|-----------------|-------------------|
| **Data Model** | Tables with rows/columns | Collections with documents |
| **Schema** | Fixed schema (DDL) | Flexible schema |
| **Relationships** | Foreign keys, joins | Embedded or referenced |
| **Scaling** | Vertical (bigger server) | Horizontal (more servers) |
| **Transactions** | ACID by default | ACID since v4.0 |
| **Use Case** | Structured, relational | Flexible, hierarchical |

### Collections vs Tables

**SQL Table:**
```sql
CREATE TABLE portfolios (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL,
    cash_balance DECIMAL(19,2),
    created_at TIMESTAMP
);

CREATE TABLE holdings (
    id INT PRIMARY KEY,
    portfolio_id VARCHAR(36),
    symbol VARCHAR(10),
    quantity INT,
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);
```

**MongoDB Collection:**
```json
{
  "_id": "PORT001",
  "clientId": "CLIENT123",
  "cashBalance": 100000.00,
  "createdAt": ISODate("2025-12-30T10:00:00Z"),
  "holdings": [
    {
      "symbol": "AAPL",
      "quantity": 100,
      "averagePrice": 150.00
    },
    {
      "symbol": "GOOGL",
      "quantity": 50,
      "averagePrice": 2800.00
    }
  ]
}
```

**Key Difference:** Holdings are **embedded** in MongoDB (one document), not separate table with foreign key.

### BSON Format

**BSON** (Binary JSON) is MongoDB's storage format.

**JSON:**
```json
{
  "name": "John Doe",
  "age": 30,
  "balance": 100000.50
}
```

**BSON Benefits:**
- ✅ Binary encoding (faster parsing)
- ✅ Additional data types (Date, Decimal128, ObjectId)
- ✅ Embedded documents
- ✅ Efficient for storage and traversal

**BSON Data Types:**
- `String`, `Int32`, `Int64`, `Double`
- `Decimal128` (for financial precision)
- `Date` (UTC datetime)
- `ObjectId` (unique 12-byte identifier)
- `Array`, `Object` (embedded documents)
- `Boolean`, `Null`

### When to Use MongoDB

✅ **Good For:**
- Flexible, evolving schema
- Hierarchical data (nested structures)
- High write throughput
- Rapid prototyping
- Catalogs, content management

❌ **Not Ideal For:**
- Complex multi-table joins
- Transactions across multiple documents (use cases requiring strong ACID guarantees across entities)
- When data is highly normalized

## MongoDB Setup

### Docker Configuration

**Location:** `docker-compose.yml`

```yaml
mongodb:
  image: mongo:7
  container_name: mongodb
  ports:
    - "27017:27017"
  environment:
    MONGO_INITDB_ROOT_USERNAME: admin
    MONGO_INITDB_ROOT_PASSWORD: admin123
    MONGO_INITDB_DATABASE: portfolio_db
  volumes:
    - mongodb-data:/data/db
  networks:
    - pms-network
  healthcheck:
    test: echo 'db.runCommand("ping").ok' | mongosh localhost:27017/test --quiet
    interval: 10s
    timeout: 5s
    retries: 5
```

**Key Points:**
- **Image:** MongoDB 7 (latest stable)
- **Port:** 27017 (default MongoDB port)
- **Volume:** Persist data across container restarts
- **Health Check:** Verify MongoDB is responsive

### Spring Boot Dependencies

**Location:** `portfolio-service/pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

### Application Configuration

**Location:** `application.yml`

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://admin:admin123@localhost:27017/portfolio_db?authSource=admin
      auto-index-creation: true
```

**URI Components:**
- `mongodb://` - Protocol
- `admin:admin123@` - Username:Password
- `localhost:27017` - Host:Port
- `/portfolio_db` - Database name
- `?authSource=admin` - Authentication database

### Database Initialization

MongoDB automatically creates databases and collections on first insert.

**Manual Creation (Optional):**
```javascript
// Connect to MongoDB shell
mongosh mongodb://admin:admin123@localhost:27017

// Switch to database
use portfolio_db

// Create collection with validation
db.createCollection("portfolios", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["clientId", "accountNumber", "currency"],
      properties: {
        clientId: {
          bsonType: "string",
          description: "Client ID is required"
        },
        cashBalance: {
          bsonType: "decimal",
          minimum: 0,
          description: "Cash balance must be non-negative"
        }
      }
    }
  }
})
```

### Verify Connection

**PowerShell:**
```powershell
# Check MongoDB is running
docker ps | Select-String mongodb

# Connect to MongoDB shell
docker exec -it mongodb mongosh -u admin -p admin123

# List databases
show dbs

# Switch to portfolio_db
use portfolio_db

# List collections
show collections

# Count documents
db.portfolios.countDocuments()
```

### Connection Pooling

Spring Boot auto-configures connection pooling:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://admin:admin123@localhost:27017/portfolio_db
      # Connection pool settings (defaults shown)
      # maxPoolSize: 100
      # minPoolSize: 0
      # maxConnectionIdleTime: 60000ms
      # maxConnectionLifeTime: 0ms
```

## Domain Modeling

### @Document Annotation

**Location:** `Portfolio.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portfolios")
public class Portfolio {

    @Id
    private String id;

    private String clientId;
    private String clientName;
    private String accountNumber;
    
    @Builder.Default
    private BigDecimal cashBalance = BigDecimal.ZERO;
    
    @Builder.Default
    private List<Holding> holdings = new ArrayList<>();
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

### Key Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@Document(collection = "portfolios")` | Map to MongoDB collection | Class level |
| `@Id` | Primary key (auto-generated if null) | `private String id;` |
| `@Field("client_name")` | Custom field name in DB | `private String clientName;` |
| `@Indexed` | Create index on field | `@Indexed(unique = true)` |
| `@CreatedDate` | Auto-populate on insert | `private LocalDateTime createdAt;` |
| `@LastModifiedDate` | Auto-update on save | `private LocalDateTime updatedAt;` |
| `@Transient` | Exclude field from persistence | Calculated fields |

### Embedded Documents

**Holding (Embedded):**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Holding {
    private String symbol;
    private String companyName;
    private Integer quantity;
    private BigDecimal averagePrice;
    private BigDecimal currentPrice;
    private BigDecimal totalValue;
}
```

**No @Document annotation** - Holding is embedded within Portfolio.

**Stored in MongoDB:**
```json
{
  "_id": "PORT001",
  "clientId": "CLIENT123",
  "holdings": [
    {
      "symbol": "AAPL",
      "quantity": 100,
      "averagePrice": 150.00
    }
  ]
}
```

### Embedded vs Referenced

**Embedded (Denormalized):**
```java
@Document(collection = "portfolios")
public class Portfolio {
    @Id
    private String id;
    private List<Holding> holdings;  // Embedded
}
```

**Benefits:**
- ✅ One query to get everything
- ✅ Atomic updates
- ✅ Better read performance

**Drawbacks:**
- ❌ Document size limit (16MB)
- ❌ Data duplication

**Referenced (Normalized):**
```java
@Document(collection = "portfolios")
public class Portfolio {
    @Id
    private String id;
    @DBRef
    private List<Holding> holdings;  // Referenced
}

@Document(collection = "holdings")
public class Holding {
    @Id
    private String id;
    private String portfolioId;  // Foreign key-like
}
```

**Benefits:**
- ✅ No duplication
- ✅ Update one place

**Drawbacks:**
- ❌ Multiple queries (like SQL join)
- ❌ Slower reads

**Rule of Thumb:** Use embedded for one-to-few relationships, referenced for one-to-many.

### ID Generation

**Auto-generated ObjectId:**
```java
@Id
private String id;  // MongoDB generates: "507f1f77bcf86cd799439011"
```

**Custom ID:**
```java
@Id
private String id;

// Set manually before save
portfolio.setId("PORT-" + UUID.randomUUID());
```

### Auditing

Enable automatic timestamp tracking:

**Configuration:**
```java
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
```

**Entity:**
```java
@Document
public class Portfolio {
    @CreatedDate
    private LocalDateTime createdAt;  // Auto-set on insert
    
    @LastModifiedDate
    private LocalDateTime updatedAt;  // Auto-set on update
}
```

## Spring Data MongoDB

### MongoRepository Interface

**Location:** `PortfolioRepository.java`

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {
    // Inherits CRUD methods from MongoRepository
}
```

**Inherited Methods:**
```java
// Create/Update
Portfolio save(Portfolio portfolio);
List<Portfolio> saveAll(Iterable<Portfolio> portfolios);

// Read
Optional<Portfolio> findById(String id);
List<Portfolio> findAll();
List<Portfolio> findAllById(Iterable<String> ids);
boolean existsById(String id);
long count();

// Delete
void deleteById(String id);
void delete(Portfolio portfolio);
void deleteAll();
```

### CRUD Operations Example

**Service Layer:**
```java
@Service
public class PortfolioService {

    private final PortfolioRepository repository;

    // CREATE
    public PortfolioDTO.Response createPortfolio(PortfolioDTO.CreateRequest request) {
        Portfolio portfolio = Portfolio.builder()
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .accountNumber(request.getAccountNumber())
                .cashBalance(request.getCashBalance())
                .status(Portfolio.PortfolioStatus.ACTIVE)
                .build();

        Portfolio saved = repository.save(portfolio);  // MongoDB insert
        return mapper.toResponse(saved);
    }

    // READ
    public PortfolioDTO.Response getPortfolioById(String id) {
        Portfolio portfolio = repository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + id));
        return mapper.toResponse(portfolio);
    }

    // UPDATE
    public PortfolioDTO.Response updateCashBalance(String id, BigDecimal amount) {
        Portfolio portfolio = repository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found: " + id));
        
        portfolio.setCashBalance(portfolio.getCashBalance().add(amount));
        Portfolio updated = repository.save(portfolio);  // MongoDB update
        
        return mapper.toResponse(updated);
    }

    // DELETE
    public void deletePortfolio(String id) {
        if (!repository.existsById(id)) {
            throw new PortfolioNotFoundException("Portfolio not found: " + id);
        }
        repository.deleteById(id);  // MongoDB delete
    }
}
```

### MongoTemplate (Advanced)

For complex operations beyond repository methods:

```java
@Service
public class PortfolioService {

    private final MongoTemplate mongoTemplate;

    public List<Portfolio> findByComplexCriteria() {
        Query query = new Query();
        query.addCriteria(Criteria.where("cashBalance").gte(10000)
                .and("status").is(Portfolio.PortfolioStatus.ACTIVE));
        query.with(Sort.by(Sort.Direction.DESC, "totalValue"));
        query.limit(10);

        return mongoTemplate.find(query, Portfolio.class);
    }

    public void updateMultiple() {
        Query query = new Query(Criteria.where("status").is("INACTIVE"));
        Update update = new Update().set("status", Portfolio.PortfolioStatus.CLOSED);
        
        mongoTemplate.updateMulti(query, update, Portfolio.class);
    }
}
```

### Repository vs Template

| Feature | MongoRepository | MongoTemplate |
|---------|----------------|---------------|
| **Usage** | Interface with method names | Programmatic queries |
| **Type Safety** | Compile-time | Runtime |
| **Simplicity** | High (less code) | Low (more verbose) |
| **Flexibility** | Limited | Full control |
| **Use Case** | Standard CRUD | Complex queries, bulk ops |

**Best Practice:** Use MongoRepository for 80% of cases, MongoTemplate for complex operations.

## Repositories

### Custom Query Methods

Spring Data MongoDB auto-generates queries from method names:

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    // findBy{FieldName}
    Optional<Portfolio> findByAccountNumber(String accountNumber);
    List<Portfolio> findByClientId(String clientId);
    List<Portfolio> findByStatus(Portfolio.PortfolioStatus status);

    // Multiple fields with And/Or
    List<Portfolio> findByClientIdAndStatus(String clientId, Portfolio.PortfolioStatus status);
    List<Portfolio> findByStatusOrCurrency(Portfolio.PortfolioStatus status, String currency);

    // Comparison operators
    List<Portfolio> findByCashBalanceGreaterThan(BigDecimal amount);
    List<Portfolio> findByTotalValueLessThanEqual(BigDecimal amount);
    List<Portfolio> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // String operations
    List<Portfolio> findByClientNameContaining(String name);
    List<Portfolio> findByClientNameStartingWith(String prefix);
    List<Portfolio> findByCurrencyIn(List<String> currencies);

    // Existence checks
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByClientIdAndStatus(String clientId, Portfolio.PortfolioStatus status);

    // Count
    long countByStatus(Portfolio.PortfolioStatus status);

    // Limit results
    Portfolio findFirstByClientIdOrderByCreatedAtDesc(String clientId);
    List<Portfolio> findTop10ByStatusOrderByTotalValueDesc(Portfolio.PortfolioStatus status);

    // Delete
    void deleteByClientId(String clientId);
    long deleteByStatus(Portfolio.PortfolioStatus status);
}
```

### Method Naming Convention

**Pattern:** `findBy{Property}{Operator}{Property}...`

**Supported Keywords:**
- `And`, `Or`
- `GreaterThan`, `GreaterThanEqual`, `LessThan`, `LessThanEqual`
- `Between`, `In`, `NotIn`
- `Like`, `NotLike`, `StartingWith`, `EndingWith`, `Containing`
- `IsNull`, `IsNotNull`
- `True`, `False`
- `OrderBy{Property}Asc`, `OrderBy{Property}Desc`

### @Query Annotation

For complex queries beyond method naming:

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    // Custom MongoDB query
    @Query("{ 'clientId': ?0, 'status': ?1 }")
    List<Portfolio> findCustom(String clientId, Portfolio.PortfolioStatus status);

    // Query with projection (select specific fields)
    @Query(value = "{ 'status': ?0 }", fields = "{ 'clientId': 1, 'totalValue': 1 }")
    List<Portfolio> findSummaryByStatus(Portfolio.PortfolioStatus status);

    // Regex query
    @Query("{ 'clientName': { $regex: ?0, $options: 'i' } }")
    List<Portfolio> searchByClientName(String pattern);

    // Complex query with nested fields
    @Query("{ 'holdings.symbol': ?0 }")
    List<Portfolio> findByHoldingSymbol(String symbol);

    // Aggregation query
    @Query("{ 'cashBalance': { $gte: ?0 }, 'totalValue': { $lte: ?1 } }")
    List<Portfolio> findByBalanceRange(BigDecimal minCash, BigDecimal maxValue);

    // Delete with custom query
    @Query(value = "{ 'status': 'CLOSED' }", delete = true)
    List<Portfolio> removeClosedPortfolios();
}
```

### Query Parameters

**Named Parameters:**
```java
@Query("{ 'clientId': :#{#clientId}, 'status': :#{#status} }")
List<Portfolio> findByClientAndStatus(@Param("clientId") String clientId, 
                                      @Param("status") Portfolio.PortfolioStatus status);
```

**SpEL Expressions:**
```java
@Query("{ 'createdAt': { $gte: :#{#start}, $lte: :#{#end} } }")
List<Portfolio> findByDateRange(@Param("start") LocalDateTime start, 
                                @Param("end") LocalDateTime end);
```

### Sorting and Pagination

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    // Method name sorting
    List<Portfolio> findByStatusOrderByTotalValueDesc(Portfolio.PortfolioStatus status);

    // Parameterized sorting
    List<Portfolio> findByStatus(Portfolio.PortfolioStatus status, Sort sort);

    // Pagination
    Page<Portfolio> findByStatus(Portfolio.PortfolioStatus status, Pageable pageable);
}
```

**Service Usage:**
```java
public Page<PortfolioDTO.Summary> getPortfoliosPaginated(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("totalValue").descending());
    Page<Portfolio> portfolios = repository.findByStatus(Portfolio.PortfolioStatus.ACTIVE, pageable);
    return portfolios.map(mapper::toSummary);
}
```

## Queries

### Query DSL with Criteria

**MongoTemplate Example:**

```java
@Service
public class PortfolioService {

    private final MongoTemplate mongoTemplate;

    public List<Portfolio> searchPortfolios(PortfolioSearchCriteria criteria) {
        Query query = new Query();

        // Dynamic criteria building
        if (criteria.getClientId() != null) {
            query.addCriteria(Criteria.where("clientId").is(criteria.getClientId()));
        }

        if (criteria.getMinBalance() != null) {
            query.addCriteria(Criteria.where("cashBalance").gte(criteria.getMinBalance()));
        }

        if (criteria.getStatus() != null) {
            query.addCriteria(Criteria.where("status").is(criteria.getStatus()));
        }

        // Sorting
        query.with(Sort.by(Sort.Direction.DESC, "totalValue"));

        // Pagination
        query.skip(criteria.getPage() * criteria.getSize());
        query.limit(criteria.getSize());

        return mongoTemplate.find(query, Portfolio.class);
    }
}
```

### Criteria Operations

```java
// Equality
Criteria.where("status").is(Portfolio.PortfolioStatus.ACTIVE)

// Comparison
Criteria.where("cashBalance").gt(10000)
Criteria.where("cashBalance").gte(10000)
Criteria.where("totalValue").lt(100000)
Criteria.where("totalValue").lte(100000)

// Range
Criteria.where("cashBalance").gte(10000).lte(100000)

// In/Not In
Criteria.where("status").in(Arrays.asList("ACTIVE", "INACTIVE"))
Criteria.where("currency").nin(Arrays.asList("EUR", "GBP"))

// Null checks
Criteria.where("closedDate").isNull()
Criteria.where("clientName").ne(null)

// Regex
Criteria.where("clientName").regex("^John", "i")  // Case-insensitive

// Exists
Criteria.where("holdings").exists(true)
Criteria.where("holdings").size(0)  // Empty array

// Nested fields
Criteria.where("holdings.symbol").is("AAPL")
Criteria.where("holdings.0.quantity").gte(100)  // First holding

// AND/OR
Criteria criteria = new Criteria().andOperator(
    Criteria.where("status").is("ACTIVE"),
    Criteria.where("cashBalance").gte(10000)
);

Criteria criteria = new Criteria().orOperator(
    Criteria.where("status").is("ACTIVE"),
    Criteria.where("totalValue").gte(100000)
);
```

### findBy Method Examples

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    // Simple equality
    List<Portfolio> findByClientId(String clientId);

    // Comparison
    List<Portfolio> findByCashBalanceGreaterThan(BigDecimal amount);
    List<Portfolio> findByTotalValueLessThanEqual(BigDecimal amount);

    // Between (range)
    List<Portfolio> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Portfolio> findByCashBalanceBetween(BigDecimal min, BigDecimal max);

    // Like/Containing
    List<Portfolio> findByClientNameContaining(String keyword);
    List<Portfolio> findByClientNameStartingWith(String prefix);

    // In collection
    List<Portfolio> findByCurrencyIn(List<String> currencies);
    List<Portfolio> findByStatusIn(List<Portfolio.PortfolioStatus> statuses);

    // And/Or combinations
    List<Portfolio> findByClientIdAndStatus(String clientId, Portfolio.PortfolioStatus status);
    List<Portfolio> findByStatusOrCashBalanceGreaterThan(
        Portfolio.PortfolioStatus status, BigDecimal balance);

    // Ordering
    List<Portfolio> findByStatusOrderByTotalValueDesc(Portfolio.PortfolioStatus status);
    List<Portfolio> findByClientIdOrderByCreatedAtAsc(String clientId);

    // Limit results
    Portfolio findFirstByClientIdOrderByCreatedAtDesc(String clientId);
    List<Portfolio> findTop5ByStatusOrderByTotalValueDesc(Portfolio.PortfolioStatus status);

    // Count
    long countByStatus(Portfolio.PortfolioStatus status);
    long countByCashBalanceGreaterThan(BigDecimal amount);

    // Exists
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByClientIdAndStatus(String clientId, Portfolio.PortfolioStatus status);
}
```

### Complex Query Example

```java
public List<Portfolio> findHighValueActivePortfolios() {
    Query query = new Query();
    
    query.addCriteria(new Criteria().andOperator(
        Criteria.where("status").is(Portfolio.PortfolioStatus.ACTIVE),
        Criteria.where("totalValue").gte(new BigDecimal("100000")),
        Criteria.where("holdings").exists(true).ne(Collections.emptyList())
    ));
    
    query.with(Sort.by(Sort.Direction.DESC, "totalValue"));
    query.limit(20);
    
    return mongoTemplate.find(query, Portfolio.class);
}
```

### Native MongoDB Query

```java
@Query("{ $and: [ { 'status': 'ACTIVE' }, { 'totalValue': { $gte: ?0 } }, { 'holdings': { $exists: true, $ne: [] } } ] }")
List<Portfolio> findActiveHighValuePortfolios(BigDecimal minValue);
```

## Indexes

### Why Indexes?

**Without Index:**
```
Query: db.portfolios.find({ clientId: "CLIENT123" })
Scan: 100,000 documents → 250ms
```

**With Index:**
```
Query: db.portfolios.find({ clientId: "CLIENT123" })
Index lookup → 5ms (50x faster!)
```

### @Indexed Annotation

**Location:** `Portfolio.java`

```java
@Document(collection = "portfolios")
public class Portfolio {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accountNumber;  // Unique index

    @Indexed
    private String clientId;  // Single field index

    @Indexed
    private Portfolio.PortfolioStatus status;

    private BigDecimal cashBalance;
}
```

### Index Types

**1. Single Field Index**
```java
@Indexed
private String clientId;
```

**MongoDB:**
```javascript
db.portfolios.createIndex({ clientId: 1 })
```

**2. Unique Index**
```java
@Indexed(unique = true)
private String accountNumber;
```

**MongoDB:**
```javascript
db.portfolios.createIndex({ accountNumber: 1 }, { unique: true })
```

**3. Compound Index**
```java
@Document(collection = "portfolios")
@CompoundIndex(name = "client_status_idx", def = "{'clientId': 1, 'status': 1}")
public class Portfolio {
    // Fields...
}
```

**MongoDB:**
```javascript
db.portfolios.createIndex({ clientId: 1, status: 1 })
```

**4. Text Index**
```java
@Indexed(type = IndexType.TEXT)
private String clientName;
```

**MongoDB:**
```javascript
db.portfolios.createIndex({ clientName: "text" })
```

### Index Configuration

```java
@Document(collection = "portfolios")
@CompoundIndex(name = "client_status_idx", def = "{'clientId': 1, 'status': 1}")
@CompoundIndex(name = "value_balance_idx", def = "{'totalValue': -1, 'cashBalance': -1}")
public class Portfolio {

    @Indexed(unique = true)
    private String accountNumber;

    @Indexed
    private String clientId;

    @Indexed(sparse = true)  // Only index if field exists
    private LocalDateTime closedDate;

    @Indexed(expireAfterSeconds = 3600)  // TTL index (auto-delete after 1 hour)
    private LocalDateTime tempToken;
}
```

### Enable Auto-Index Creation

**application.yml:**
```yaml
spring:
  data:
    mongodb:
      auto-index-creation: true  # Spring creates indexes on startup
```

### Manual Index Creation

**MongoTemplate:**
```java
@Configuration
public class MongoIndexConfig {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(Portfolio.class);

        // Single field index
        indexOps.ensureIndex(new Index().on("clientId", Sort.Direction.ASC));

        // Unique index
        indexOps.ensureIndex(new Index().on("accountNumber", Sort.Direction.ASC).unique());

        // Compound index
        indexOps.ensureIndex(new Index()
            .on("clientId", Sort.Direction.ASC)
            .on("status", Sort.Direction.ASC)
            .named("client_status_idx"));

        // Text index
        indexOps.ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
            .onField("clientName")
            .build());
    }
}
```

### Verify Indexes

**MongoDB Shell:**
```javascript
// List all indexes
db.portfolios.getIndexes()

// Output:
[
  { "v": 2, "key": { "_id": 1 }, "name": "_id_" },
  { "v": 2, "key": { "accountNumber": 1 }, "name": "accountNumber", "unique": true },
  { "v": 2, "key": { "clientId": 1 }, "name": "clientId" },
  { "v": 2, "key": { "clientId": 1, "status": 1 }, "name": "client_status_idx" }
]
```

### Query Performance with Indexes

**Explain Query:**
```javascript
db.portfolios.find({ clientId: "CLIENT123" }).explain("executionStats")
```

**Without Index:**
```json
{
  "executionStats": {
    "executionTimeMillis": 250,
    "totalDocsExamined": 100000,
    "nReturned": 5
  }
}
```

**With Index:**
```json
{
  "executionStats": {
    "executionTimeMillis": 5,
    "totalDocsExamined": 5,
    "nReturned": 5
  }
}
```

### Index Best Practices

✅ **DO:**
- Index fields used in queries (where, sort)
- Unique indexes for unique fields (accountNumber)
- Compound indexes for common query combinations
- Limit indexes (each index slows writes)

❌ **DON'T:**
- Index every field (storage overhead)
- Create duplicate indexes
- Index low-cardinality fields (status with 2 values)
- Forget to test query performance

**Rule of Thumb:** Index fields in WHERE clauses and ORDER BY clauses of frequent queries.

## Aggregations

### Aggregation Pipeline

MongoDB's aggregation framework processes data through stages (like Unix pipes).

**Stages:**
```
Collection → $match → $group → $sort → $project → Result
```

### MongoTemplate Aggregation

```java
@Service
public class PortfolioAnalyticsService {

    private final MongoTemplate mongoTemplate;

    public List<ClientPortfolioSummary> getClientSummaries() {
        Aggregation aggregation = Aggregation.newAggregation(
            // Stage 1: Filter active portfolios
            Aggregation.match(Criteria.where("status").is(Portfolio.PortfolioStatus.ACTIVE)),
            
            // Stage 2: Group by clientId
            Aggregation.group("clientId")
                .count().as("portfolioCount")
                .sum("totalValue").as("totalValue")
                .sum("cashBalance").as("totalCash")
                .first("clientName").as("clientName"),
            
            // Stage 3: Sort by total value descending
            Aggregation.sort(Sort.Direction.DESC, "totalValue"),
            
            // Stage 4: Limit to top 10
            Aggregation.limit(10)
        );

        AggregationResults<ClientPortfolioSummary> results = 
            mongoTemplate.aggregate(aggregation, "portfolios", ClientPortfolioSummary.class);
        
        return results.getMappedResults();
    }
}

@Data
class ClientPortfolioSummary {
    private String clientId;
    private String clientName;
    private int portfolioCount;
    private BigDecimal totalValue;
    private BigDecimal totalCash;
}
```

### Common Aggregation Stages

**1. $match - Filter documents**
```java
Aggregation.match(Criteria.where("status").is("ACTIVE"))
Aggregation.match(Criteria.where("cashBalance").gte(10000))
```

**2. $group - Group and aggregate**
```java
Aggregation.group("clientId")
    .count().as("count")
    .sum("totalValue").as("totalValue")
    .avg("cashBalance").as("avgCash")
    .min("createdAt").as("firstPortfolio")
    .max("updatedAt").as("lastUpdate")
    .first("clientName").as("clientName")
    .last("accountNumber").as("latestAccount")
```

**3. $project - Select/transform fields**
```java
Aggregation.project("clientId", "totalValue")
    .and("cashBalance").as("cash")
    .andExpression("totalValue - cashBalance").as("investedValue")
    .andExclude("_id")
```

**4. $sort - Sort results**
```java
Aggregation.sort(Sort.Direction.DESC, "totalValue")
Aggregation.sort(Sort.by(
    Sort.Order.desc("totalValue"),
    Sort.Order.asc("clientName")
))
```

**5. $limit and $skip - Pagination**
```java
Aggregation.skip(20)   // Skip first 20
Aggregation.limit(10)  // Take next 10
```

**6. $unwind - Flatten arrays**
```java
// Convert holdings array to separate documents
Aggregation.unwind("holdings")
```

### Real-World Examples

**Example 1: Top Holdings Across All Portfolios**
```java
public List<HoldingSummary> getTopHoldings() {
    Aggregation aggregation = Aggregation.newAggregation(
        // Match active portfolios
        Aggregation.match(Criteria.where("status").is("ACTIVE")),
        
        // Unwind holdings array
        Aggregation.unwind("holdings"),
        
        // Group by symbol
        Aggregation.group("holdings.symbol")
            .sum("holdings.quantity").as("totalQuantity")
            .sum("holdings.totalValue").as("totalValue")
            .count().as("portfolioCount")
            .first("holdings.companyName").as("companyName"),
        
        // Sort by total value
        Aggregation.sort(Sort.Direction.DESC, "totalValue"),
        
        // Top 10
        Aggregation.limit(10),
        
        // Rename _id to symbol
        Aggregation.project()
            .and("_id").as("symbol")
            .andInclude("companyName", "totalQuantity", "totalValue", "portfolioCount")
            .andExclude("_id")
    );

    return mongoTemplate.aggregate(aggregation, "portfolios", HoldingSummary.class)
        .getMappedResults();
}
```

**Example 2: Portfolio Distribution by Status**
```java
public List<StatusDistribution> getStatusDistribution() {
    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.group("status")
            .count().as("count")
            .sum("totalValue").as("totalValue")
            .avg("cashBalance").as("avgCashBalance"),
        
        Aggregation.sort(Sort.Direction.DESC, "count")
    );

    return mongoTemplate.aggregate(aggregation, "portfolios", StatusDistribution.class)
        .getMappedResults();
}
```

**Example 3: Monthly Portfolio Creation Trend**
```java
public List<MonthlyStats> getMonthlyCreationTrend() {
    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.project()
            .andExpression("year(createdAt)").as("year")
            .andExpression("month(createdAt)").as("month")
            .andInclude("totalValue"),
        
        Aggregation.group("year", "month")
            .count().as("portfoliosCreated")
            .sum("totalValue").as("totalValue"),
        
        Aggregation.sort(Sort.Direction.DESC, "year", "month")
    );

    return mongoTemplate.aggregate(aggregation, "portfolios", MonthlyStats.class)
        .getMappedResults();
}
```

### Native MongoDB Aggregation

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    @Aggregation("{ $group: { _id: '$status', count: { $sum: 1 } } }")
    List<StatusCount> countByStatus();

    @Aggregation(pipeline = {
        "{ $match: { status: 'ACTIVE' } }",
        "{ $group: { _id: '$clientId', totalValue: { $sum: '$totalValue' } } }",
        "{ $sort: { totalValue: -1 } }"
    })
    List<ClientTotal> getClientTotals();
}
```

### Aggregation Performance Tips

✅ **DO:**
- Use `$match` early to filter documents
- Use indexes for `$match` and `$sort` stages
- Project only needed fields (`$project`)
- Use `$limit` to reduce result size

❌ **DON'T:**
- Unwind large arrays without filtering first
- Sort large datasets without index
- Include unnecessary fields in projection

**Example - Optimized Pipeline:**
```java
Aggregation aggregation = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("status").is("ACTIVE")),  // ✅ Filter early (uses index)
    Aggregation.project("clientId", "totalValue"),              // ✅ Select only needed fields
    Aggregation.group("clientId").sum("totalValue").as("total"),
    Aggregation.sort(Sort.Direction.DESC, "total"),             // ✅ Sort after grouping (smaller dataset)
    Aggregation.limit(10)                                       // ✅ Limit results
);
```
