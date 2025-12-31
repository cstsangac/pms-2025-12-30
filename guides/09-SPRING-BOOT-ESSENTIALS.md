# Spring Boot Essentials

## Table of Contents
1. [Spring Boot Overview](#spring-boot-overview)
2. [Dependency Injection](#dependency-injection)
3. [Annotations Explained](#annotations-explained)
4. [Auto-configuration](#auto-configuration)
5. [Properties Management](#properties-management)
6. [Profiles](#profiles)
7. [Actuator](#actuator)
8. [Spring Boot 3 Features](#spring-boot-3-features)

---

## Spring Boot Overview

### What is Spring Boot?

**Spring Boot** is an opinionated framework built on top of Spring Framework that simplifies Java application development by providing:

- **Convention over Configuration:** Sensible defaults, minimal XML
- **Embedded Servers:** Tomcat, Jetty, or Undertow built-in
- **Starter Dependencies:** Pre-configured dependency bundles
- **Auto-configuration:** Automatic bean configuration based on classpath
- **Production-ready Features:** Metrics, health checks, externalized config

**Traditional Spring vs Spring Boot:**

```java
// Traditional Spring (XML configuration)
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <!-- 20+ more lines of configuration -->
    </bean>
</beans>

// Spring Boot (Zero configuration)
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: password

// DataSource bean auto-configured! ✨
```

### Convention Over Configuration

**Spring Boot Philosophy:**
> "The framework should do the right thing by default."

**Examples in our project:**

| Convention | Auto-configured | Manual Config Needed? |
|-----------|----------------|---------------------|
| Embedded Tomcat | ✅ Port 8080 | ❌ No |
| Jackson JSON | ✅ Serialization | ❌ No |
| Logging | ✅ Logback + SLF4J | ❌ No |
| MongoDB | ✅ Connection pool | ❌ No |
| Kafka | ✅ Producer/Consumer | ❌ No |

### Starter Dependencies

**Spring Boot Starters** are curated dependency bundles.

**Portfolio Service Example:**

```xml
<!-- Instead of 15+ individual dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**This ONE dependency includes:**
- Spring MVC
- Tomcat (embedded server)
- Jackson (JSON)
- Hibernate Validator
- Logging (SLF4J + Logback)

**Common Starters in Our Project:**

```xml
<!-- Web REST APIs -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- MongoDB -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- Redis (Caching) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Main Application Class

**Every Spring Boot app starts here:**

```java
@SpringBootApplication
public class PortfolioServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioServiceApplication.class, args);
    }
}
```

**@SpringBootApplication = 3 annotations combined:**

```java
@SpringBootConfiguration  // Same as @Configuration
@EnableAutoConfiguration  // Magic happens here
@ComponentScan            // Scans package for beans
```

### Embedded Server

**No external Tomcat needed!**

```
Traditional Deployment:
1. Build WAR file
2. Install Tomcat
3. Deploy WAR to Tomcat
4. Configure server.xml

Spring Boot:
1. mvn clean package
2. java -jar portfolio-service.jar
Done! 🎉
```

**Running the app:**

```bash
# Build
mvn clean package

# Run (port 8081)
java -jar target/portfolio-service-1.0.0-SNAPSHOT.jar

# Or with Maven
mvn spring-boot:run

# Output:
Tomcat started on port(s): 8081 (http)
Started PortfolioServiceApplication in 3.2 seconds
```

### Fat JAR Structure

**Spring Boot creates "fat JAR" (uber JAR):**

```
portfolio-service.jar
├── BOOT-INF/
│   ├── classes/              ← Your compiled code
│   │   └── com/wealthmanagement/...
│   └── lib/                  ← All dependencies
│       ├── spring-web-6.0.x.jar
│       ├── mongodb-driver-5.0.x.jar
│       └── ... (50+ JARs)
├── META-INF/
│   └── MANIFEST.MF           ← Main-Class: JarLauncher
└── org/springframework/boot/loader/  ← Spring Boot loader
```

**Why Fat JAR?**
- ✅ Single deployable artifact
- ✅ No dependency hell
- ✅ Easy Docker deployment
- ✅ Cloud-friendly

### Key Benefits

**1. Rapid Development**
```java
// Create REST API in 5 lines
@RestController
@RequestMapping("/api/hello")
public class HelloController {
    @GetMapping
    public String hello() {
        return "Hello World!";
    }
}
// Run → http://localhost:8080/api/hello
// No XML, no config, just works!
```

**2. Production-Ready**
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  
# GET /actuator/health → {"status": "UP"}
# GET /actuator/metrics → JVM stats, HTTP requests, etc.
```

**3. Testing Made Easy**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PortfolioControllerTest {
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testGetPortfolio() {
        // Full Spring context loaded automatically
        ResponseEntity<Portfolio> response = 
            restTemplate.getForEntity("/api/portfolios/1", Portfolio.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

### Spring Boot vs Spring Framework

| Feature | Spring Framework | Spring Boot |
|---------|-----------------|-------------|
| Configuration | XML or Java Config | Auto-configuration |
| Server | External (Tomcat, JBoss) | Embedded (Tomcat, Jetty) |
| Dependency Mgmt | Manual (each JAR) | Starters (bundles) |
| Deployment | WAR → Server | Fat JAR |
| Development Time | Longer setup | Minutes to start |
| Use Case | Legacy apps | Microservices, modern apps |

## Dependency Injection

### What is Dependency Injection?

**Dependency Injection (DI)** is a design pattern where objects receive their dependencies from an external source rather than creating them.

**Without DI (Tight Coupling):**
```java
public class PortfolioService {
    private PortfolioRepository repository = new MongoPortfolioRepository();
    //  ❌ Hard-coded dependency
    //  ❌ Cannot swap implementations
    //  ❌ Hard to test (cannot mock)
}
```

**With DI (Loose Coupling):**
```java
@Service
public class PortfolioService {
    private final PortfolioRepository repository;
    
    @Autowired  // Optional in constructor injection
    public PortfolioService(PortfolioRepository repository) {
        this.repository = repository;
        //  ✅ Dependency injected
        //  ✅ Can swap implementations
        //  ✅ Easy to test (mock repository)
    }
}
```

### Types of Injection

**1. Constructor Injection (RECOMMENDED)**

```java
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;
    private final PortfolioMapper portfolioMapper;
    
    // Constructor auto-generated by Lombok
    // Spring automatically injects dependencies
}
```

**Why Constructor Injection?**
- ✅ **Immutability:** Fields are `final`
- ✅ **Required Dependencies:** Fails fast if missing
- ✅ **Testability:** Easy to create in tests
- ✅ **No @Autowired needed:** Spring auto-detects single constructor

**2. Setter Injection (NOT RECOMMENDED)**

```java
@Service
public class PortfolioService {
    private PortfolioRepository repository;
    
    @Autowired
    public void setRepository(PortfolioRepository repository) {
        this.repository = repository;
    }
    // ❌ Mutable (repository can change)
    // ❌ Optional dependencies (can be null)
}
```

**3. Field Injection (AVOID)**

```java
@Service
public class PortfolioService {
    @Autowired
    private PortfolioRepository repository;
    
    // ❌ Cannot be final
    // ❌ Hard to test (requires Spring context)
    // ❌ Hidden dependencies (not in constructor)
}
```

### Spring Container (IoC Container)

**Spring manages beans in ApplicationContext:**

```
┌─────────────────────────────────┐
│   ApplicationContext (IoC)      │
│                                 │
│  ┌────────────────────────┐    │
│  │ PortfolioRepository    │◄───┼─── Managed by Spring
│  │ (MongoDB impl)         │    │
│  └────────────────────────┘    │
│                                 │
│  ┌────────────────────────┐    │
│  │ PortfolioService       │◄───┼─── Injected here
│  │ (Business logic)       │    │
│  └────────────────────────┘    │
│                                 │
│  ┌────────────────────────┐    │
│  │ PortfolioController    │◄───┼─── Injected here
│  │ (REST API)             │    │
│  └────────────────────────┘    │
└─────────────────────────────────┘
```

### Bean Scopes

**1. Singleton (Default)**

```java
@Service  // Singleton by default
public class PortfolioService {
    // ONE instance for entire application
    // Shared by all requests
}
```

**2. Prototype**

```java
@Service
@Scope("prototype")
public class TransactionProcessor {
    // NEW instance every time it's requested
}
```

**3. Request (Web only)**

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    // ONE instance per HTTP request
}
```

**4. Session (Web only)**

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserSession {
    // ONE instance per HTTP session
}
```

### Bean Lifecycle

```
1. Instantiation
   Spring creates bean instance
   ↓
2. Populate Properties
   Dependencies injected
   ↓
3. setBeanName()
   Aware interfaces called
   ↓
4. @PostConstruct
   Initialization callback
   ↓
5. Bean Ready
   Bean in use
   ↓
6. @PreDestroy
   Cleanup callback
   ↓
7. Destruction
   Spring shuts down
```

**Lifecycle Callbacks Example:**

```java
@Service
public class PortfolioService {
    private final PortfolioRepository repository;
    
    public PortfolioService(PortfolioRepository repository) {
        this.repository = repository;
        log.info("1. Constructor called");
    }
    
    @PostConstruct
    public void init() {
        log.info("2. @PostConstruct: Bean initialized");
        // Perform initialization (load cache, validate config, etc.)
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("3. @PreDestroy: Bean destroyed");
        // Cleanup resources (close connections, flush cache, etc.)
    }
}
```

### Qualifier (Multiple Implementations)

**Problem: Multiple beans of same type**

```java
public interface NotificationService {
    void send(String message);
}

@Service
public class EmailNotificationService implements NotificationService {
    public void send(String message) { /* send email */ }
}

@Service
public class SmsNotificationService implements NotificationService {
    public void send(String message) { /* send SMS */ }
}

// Which one to inject? 🤔
```

**Solution 1: @Qualifier**

```java
@Service
public class UserService {
    private final NotificationService notificationService;
    
    public UserService(@Qualifier("emailNotificationService") NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
```

**Solution 2: @Primary**

```java
@Service
@Primary  // Default choice
public class EmailNotificationService implements NotificationService { }

@Service
public class SmsNotificationService implements NotificationService { }

// EmailNotificationService injected by default
```

**Solution 3: Custom Annotation**

```java
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Email {}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Sms {}

@Service
@Email
public class EmailNotificationService implements NotificationService { }

@Service
@Sms
public class SmsNotificationService implements NotificationService { }

// Usage
public UserService(@Email NotificationService notificationService) { }
```

### Circular Dependencies

**Problem:**

```java
@Service
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

@Service
public class ServiceB {
    private final ServiceA serviceA;
    
    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;  // ❌ Circular dependency!
    }
}
```

**Solution 1: Redesign (Best)**

```java
// Extract common logic to ServiceC
@Service
public class ServiceC {
    public void commonLogic() { }
}

@Service
public class ServiceA {
    private final ServiceC serviceC;
}

@Service
public class ServiceB {
    private final ServiceC serviceC;
}
```

**Solution 2: @Lazy**

```java
@Service
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(@Lazy ServiceB serviceB) {
        this.serviceB = serviceB;  // Lazy proxy injected
    }
}
```

### Real Example from Our Project

**Portfolio Service Dependencies:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    // 1. Data Access
    private final PortfolioRepository portfolioRepository;
    
    // 2. Caching
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 3. Messaging
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;
    
    // 4. Mapping
    private final PortfolioMapper portfolioMapper;
    
    // All injected via constructor by Spring!
    // No need for @Autowired with single constructor
}
```

**How Spring Creates This Bean:**

```
1. Spring finds @Service annotation
2. Creates PortfolioService bean definition
3. Detects constructor dependencies:
   - PortfolioRepository
   - RedisTemplate
   - KafkaTemplate
   - PortfolioMapper
4. Resolves each dependency from context
5. Calls constructor with dependencies
6. Stores bean in ApplicationContext
7. Returns bean to anyone who @Autowires PortfolioService
```

## Annotations Explained

### Core Stereotype Annotations

**Spring organizes beans into layers:**

```
┌──────────────────────┐
│   @RestController    │  ← Presentation Layer (REST APIs)
│   @Controller        │
└──────────┬───────────┘
           │
┌──────────▼───────────┐
│     @Service         │  ← Business Logic Layer
└──────────┬───────────┘
           │
┌──────────▼───────────┐
│   @Repository        │  ← Data Access Layer
└──────────────────────┘
```

### 1. @RestController

**Combines @Controller + @ResponseBody:**

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
        // ✅ Automatically serialized to JSON
    }
    
    @PostMapping
    public ResponseEntity<PortfolioDTO.Response> createPortfolio(
            @Valid @RequestBody PortfolioDTO.CreateRequest request) {
        PortfolioDTO.Response created = portfolioService.createPortfolio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

**@RestController Features:**
- ✅ Handles HTTP requests
- ✅ Returns JSON/XML (auto-serialized)
- ✅ Exception handling via @RestControllerAdvice
- ✅ Content negotiation (Accept header)

**HTTP Method Mappings:**

```java
@GetMapping("/portfolios")           // GET
@PostMapping("/portfolios")          // POST
@PutMapping("/portfolios/{id}")      // PUT
@DeleteMapping("/portfolios/{id}")   // DELETE
@PatchMapping("/portfolios/{id}")    // PATCH
```

### 2. @Service

**Business logic layer:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

    @Transactional
    public PortfolioDTO.Response createPortfolio(PortfolioDTO.CreateRequest request) {
        // Business logic
        Portfolio portfolio = portfolioMapper.toEntity(request);
        Portfolio saved = portfolioRepository.save(portfolio);
        
        // Publish event
        publishEvent(saved, EventType.PORTFOLIO_CREATED);
        
        return portfolioMapper.toResponse(saved);
    }
    
    // More business methods...
}
```

**@Service Characteristics:**
- ✅ Contains business logic
- ✅ Orchestrates repositories
- ✅ Handles transactions (@Transactional)
- ✅ Publishes events
- ✅ Validates business rules

### 3. @Repository

**Data access layer:**

```java
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {
    
    Optional<Portfolio> findByAccountNumber(String accountNumber);
    
    List<Portfolio> findByOwnerName(String ownerName);
    
    @Query("{ 'totalValue': { $gte: ?0 } }")
    List<Portfolio> findPortfoliosAboveValue(BigDecimal minValue);
}
```

**@Repository Features:**
- ✅ Marks data access component
- ✅ Exception translation (DB exceptions → DataAccessException)
- ✅ Spring Data auto-implements methods
- ✅ Transaction support

**Why @Repository?**

```java
// Without @Repository
try {
    // MongoDB query
} catch (MongoException e) {
    // ❌ MongoDB-specific exception (tight coupling)
}

// With @Repository
try {
    // MongoDB query
} catch (DataAccessException e) {
    // ✅ Spring generic exception (loose coupling)
    // Can swap MongoDB → PostgreSQL without changing code
}
```

### 4. @Configuration

**Java-based configuration:**

```java
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, PortfolioEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, PortfolioEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

**@Configuration Characteristics:**
- ✅ Replaces XML configuration
- ✅ Methods annotated with @Bean create beans
- ✅ Can import other configs (@Import)
- ✅ Can be conditional (@ConditionalOnProperty)

### 5. @Component

**Generic stereotype (catch-all):**

```java
@Component
public class PortfolioEventListener {

    @KafkaListener(topics = "portfolio-events", groupId = "notification-group")
    public void handlePortfolioEvent(PortfolioEvent event) {
        log.info("Received event: {}", event);
    }
}
```

**When to use @Component:**
- Utility classes
- Event listeners
- Schedulers
- Filters
- Anything not Controller/Service/Repository

### Request Handling Annotations

**1. @RequestMapping**

```java
@RequestMapping(
    value = "/api/portfolios",
    method = RequestMethod.GET,
    produces = MediaType.APPLICATION_JSON_VALUE
)
```

**2. @PathVariable**

```java
@GetMapping("/portfolios/{id}")
public Portfolio getPortfolio(@PathVariable String id) {
    // id = "PORT001"
}

@GetMapping("/portfolios/{portfolioId}/holdings/{holdingId}")
public Holding getHolding(
    @PathVariable String portfolioId,
    @PathVariable String holdingId
) { }
```

**3. @RequestParam**

```java
@GetMapping("/portfolios")
public List<Portfolio> searchPortfolios(
    @RequestParam(required = false) String ownerName,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size
) {
    // /portfolios?ownerName=John&page=0&size=20
}
```

**4. @RequestBody**

```java
@PostMapping("/portfolios")
public Portfolio createPortfolio(@RequestBody PortfolioDTO.CreateRequest request) {
    // Request body auto-deserialized from JSON
}
```

**5. @Valid**

```java
@PostMapping("/portfolios")
public Portfolio createPortfolio(@Valid @RequestBody PortfolioDTO.CreateRequest request) {
    // Triggers Bean Validation
    // @NotNull, @NotBlank, @Positive checked
}
```

### Validation Annotations

```java
public class CreateRequest {
    
    @NotBlank(message = "Account number is required")
    private String accountNumber;
    
    @NotNull(message = "Owner name is required")
    @Size(min = 2, max = 100, message = "Owner name must be 2-100 characters")
    private String ownerName;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @PositiveOrZero(message = "Initial cash must be positive or zero")
    private BigDecimal initialCash;
    
    @Pattern(regexp = "USD|EUR|GBP", message = "Currency must be USD, EUR, or GBP")
    private String currency;
}
```

### Caching Annotations

```java
@Service
public class PortfolioService {

    @Cacheable(value = "portfolios", key = "#id")
    public Portfolio getPortfolioById(String id) {
        // Result cached in Redis
        // Next call with same id → cached value returned
        return portfolioRepository.findById(id).orElseThrow();
    }
    
    @CachePut(value = "portfolios", key = "#result.id")
    public Portfolio updatePortfolio(String id, PortfolioDTO.UpdateRequest request) {
        // Update cache with new value
        Portfolio updated = /* update logic */;
        return updated;
    }
    
    @CacheEvict(value = "portfolios", key = "#id")
    public void deletePortfolio(String id) {
        // Remove from cache
        portfolioRepository.deleteById(id);
    }
    
    @CacheEvict(value = "portfolios", allEntries = true)
    public void clearAllPortfoliosCache() {
        // Clear entire cache
    }
}
```

### Transaction Annotations

```java
@Service
public class TransactionService {

    @Transactional
    public Transaction createTransaction(TransactionDTO.CreateRequest request) {
        // All operations within this method are transactional
        // If any fails → entire method rolled back
        
        Transaction transaction = transactionRepository.save(/* ... */);
        publishEvent(transaction);  // Also rolled back on failure
        
        return transaction;
    }
    
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions() {
        // Optimized for read-only operations
        return transactionRepository.findAll();
    }
    
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.SERIALIZABLE,
        timeout = 30,
        rollbackFor = Exception.class
    )
    public void complexOperation() {
        // Advanced transaction control
    }
}
```

### Async Annotations

```java
@Service
public class NotificationService {

    @Async
    public CompletableFuture<Void> sendEmailAsync(String to, String message) {
        // Runs in separate thread
        emailClient.send(to, message);
        return CompletableFuture.completedFuture(null);
    }
}

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
```

### Scheduled Annotations

```java
@Component
public class ScheduledTasks {

    @Scheduled(fixedRate = 60000)  // Every 60 seconds
    public void updateMarketPrices() {
        log.info("Fetching latest market prices...");
    }
    
    @Scheduled(cron = "0 0 9 * * MON-FRI")  // 9 AM weekdays
    public void dailyReport() {
        log.info("Generating daily report...");
    }
    
    @Scheduled(initialDelay = 5000, fixedDelay = 30000)
    public void healthCheck() {
        // Wait 5s, then run every 30s after previous completion
    }
}

@Configuration
@EnableScheduling
public class SchedulingConfig { }
```

### Conditional Annotations

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @ConditionalOnProperty(name = "database.type", havingValue = "mongodb")
    public MongoClient mongoClient() {
        return MongoClients.create("mongodb://localhost:27017");
    }
    
    @Bean
    @ConditionalOnProperty(name = "database.type", havingValue = "postgresql")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DataSource defaultDataSource() {
        // Only created if no other DataSource bean exists
        return new EmbeddedDatabaseBuilder().build();
    }
}
```

## Auto-configuration

### How Auto-configuration Works

**Spring Boot automatically configures beans based on:**
1. **Classpath dependencies:** What JARs are present?
2. **Properties:** What's in application.yml?
3. **Existing beans:** What's already configured?
4. **Conditions:** @Conditional annotations

**Example: MongoDB Auto-configuration**

```
1. Spring Boot detects spring-boot-starter-data-mongodb on classpath
   ↓
2. Checks application.yml for spring.data.mongodb.uri
   ↓
3. If not already configured, auto-creates:
   - MongoClient bean
   - MongoTemplate bean
   - Repository support
   ↓
4. Your repositories work automatically! ✨
```

### Auto-configuration Classes

**Behind the scenes:**

```java
// MongoAutoConfiguration (simplified)
@Configuration
@ConditionalOnClass(MongoClient.class)  // Only if MongoDB driver on classpath
@EnableConfigurationProperties(MongoProperties.class)
public class MongoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean  // Only if you haven't defined your own
    public MongoClient mongoClient(MongoProperties properties) {
        return MongoClients.create(properties.getUri());
    }
}
```

**What This Means:**
- ✅ Add dependency → Get configuration for free
- ✅ Override by defining your own bean
- ✅ Customize via application.yml

### Viewing Auto-configuration Report

**application.yml:**
```yaml
debug: true
```

**Output shows:**
```
Positive matches (Auto-configured):
-----------------------------------
MongoAutoConfiguration matched:
   - @ConditionalOnClass found required class 'com.mongodb.client.MongoClient'
   
KafkaAutoConfiguration matched:
   - @ConditionalOnClass found required class 'org.springframework.kafka.core.KafkaTemplate'

Negative matches (NOT Auto-configured):
---------------------------------------
JdbcTemplateAutoConfiguration did not match:
   - @ConditionalOnClass did not find required class 'org.springframework.jdbc.core.JdbcTemplate'
```

### Disabling Auto-configuration

**Method 1: Exclude specific auto-config**

```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class PortfolioServiceApplication { }
```

**Method 2: Via application.yml**

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

**When to disable:**
- Using custom configuration
- Don't need certain features
- Conflicts with existing beans

### Custom Auto-configuration

**Create your own auto-config:**

```java
@Configuration
@ConditionalOnClass(CustomService.class)
@EnableConfigurationProperties(CustomProperties.class)
public class CustomAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "custom.enabled", havingValue = "true")
    public CustomService customService(CustomProperties properties) {
        return new CustomService(properties);
    }
}
```

**META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:**
```
com.example.autoconfigure.CustomAutoConfiguration
```

### Common Auto-configurations in Our Project

| Dependency | Auto-configured Beans |
|-----------|----------------------|
| spring-boot-starter-web | DispatcherServlet, Tomcat, Jackson |
| spring-boot-starter-data-mongodb | MongoClient, MongoTemplate, Repositories |
| spring-boot-starter-data-redis | RedisConnectionFactory, RedisTemplate |
| spring-kafka | KafkaTemplate, ConsumerFactory |
| spring-cloud-gateway | RouteLocator, GatewayFilterFactory |

## Properties Management

### Configuration Files

**Spring Boot supports multiple formats:**

**1. application.yml (YAML - RECOMMENDED)**

```yaml
server:
  port: 8081

spring:
  application:
    name: portfolio-service
  data:
    mongodb:
      uri: mongodb://localhost:27017/portfolio_db
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: portfolio-service-group
      auto-offset-reset: earliest

redis:
  host: localhost
  port: 6379
  ttl-minutes: 10

logging:
  level:
    com.wealthmanagement: DEBUG
    org.springframework: INFO
```

**2. application.properties (Alternative)**

```properties
server.port=8081
spring.application.name=portfolio-service
spring.data.mongodb.uri=mongodb://localhost:27017/portfolio_db
```

**Why YAML?**
- ✅ More readable (hierarchical)
- ✅ Less repetitive
- ✅ Better for complex configurations

### @Value Annotation

**Inject single properties:**

```java
@Service
public class PortfolioService {

    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${redis.ttl-minutes}")
    private int redisTtlMinutes;
    
    @Value("${kafka.topic.portfolio-events:portfolio-events}")  // Default value
    private String portfolioEventsTopic;
    
    @Value("${feature.notifications.enabled:true}")
    private boolean notificationsEnabled;
}
```

**With SpEL (Spring Expression Language):**

```java
@Value("#{${redis.ttl-minutes} * 60}")  // Convert minutes to seconds
private int redisTtlSeconds;

@Value("#{'${kafka.topics}'.split(',')}")
private List<String> kafkaTopics;
```

### @ConfigurationProperties (RECOMMENDED)

**Type-safe configuration:**

**application.yml:**
```yaml
redis:
  host: localhost
  port: 6379
  ttl-minutes: 10
  enabled: true
```

**Configuration Class:**
```java
@Configuration
@ConfigurationProperties(prefix = "redis")
@Data
public class RedisProperties {
    private String host;
    private int port;
    private int ttlMinutes;
    private boolean enabled;
    
    // Getters/setters generated by Lombok
}
```

**Usage:**
```java
@Configuration
@RequiredArgsConstructor
public class RedisConfig {
    
    private final RedisProperties redisProperties;
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        template.setConnectionFactory(factory);
        
        return template;
    }
}
```

**Benefits of @ConfigurationProperties:**
- ✅ Type-safe (compile-time checking)
- ✅ Validation support (@NotNull, @Min, @Max)
- ✅ IDE auto-completion
- ✅ Group related properties
- ✅ Immutable with @ConstructorBinding

### Validation in Configuration

```java
@Configuration
@ConfigurationProperties(prefix = "redis")
@Validated
@Data
public class RedisProperties {
    
    @NotBlank(message = "Redis host is required")
    private String host;
    
    @Min(value = 1024, message = "Port must be >= 1024")
    @Max(value = 65535, message = "Port must be <= 65535")
    private int port;
    
    @Positive(message = "TTL must be positive")
    private int ttlMinutes;
}
```

### Property Placeholders

```yaml
app:
  name: Portfolio Service
  version: 1.0.0
  description: ${app.name} v${app.version}  # Uses other properties
  
server:
  port: ${PORT:8081}  # Environment variable PORT, default 8081
```

### Environment Variables

**Precedence (highest to lowest):**

```
1. Command line arguments
   java -jar app.jar --server.port=9090
   
2. Environment variables
   export SERVER_PORT=9090
   
3. application-{profile}.yml
   application-prod.yml
   
4. application.yml
   Default values
```

**Environment Variable Mapping:**

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017
```

**Equivalent Environment Variable:**
```bash
export SPRING_DATA_MONGODB_URI=mongodb://prod-server:27017
```

**Rules:**
- Uppercase
- `.` → `_`
- `-` → `_`

## Profiles

### What are Profiles?

**Profiles allow different configurations for different environments:**

```
Development → application-dev.yml
Testing → application-test.yml
Production → application-prod.yml
```

### Profile-specific Configuration

**application.yml (default):**
```yaml
spring:
  application:
    name: portfolio-service

logging:
  level:
    root: INFO
```

**application-dev.yml:**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/portfolio_db
  kafka:
    bootstrap-servers: localhost:9092

logging:
  level:
    com.wealthmanagement: DEBUG  # Detailed logs for development
    
redis:
  host: localhost
  port: 6379
```

**application-prod.yml:**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://prod-mongo:27017/portfolio_db?replicaSet=rs0
  kafka:
    bootstrap-servers: prod-kafka-1:9092,prod-kafka-2:9092,prod-kafka-3:9092

logging:
  level:
    com.wealthmanagement: INFO  # Less verbose in production
    
redis:
  host: prod-redis
  port: 6379
  password: ${REDIS_PASSWORD}  # From environment variable
```

### Activating Profiles

**Method 1: application.yml**
```yaml
spring:
  profiles:
    active: dev
```

**Method 2: Command line**
```bash
java -jar portfolio-service.jar --spring.profiles.active=prod
```

**Method 3: Environment variable**
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar portfolio-service.jar
```

**Method 4: IDE (IntelliJ)**
```
Run Configuration → Environment Variables → SPRING_PROFILES_ACTIVE=dev
```

### Multiple Profiles

```bash
java -jar app.jar --spring.profiles.active=prod,monitoring,secure
```

**Loads:**
- application.yml
- application-prod.yml
- application-monitoring.yml
- application-secure.yml

### Profile-specific Beans

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:h2:mem:testdb")
            .build();
    }
    
    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://prod-db:5432/mydb")
            .build();
    }
}
```

### Profile Groups

**application.yml:**
```yaml
spring:
  profiles:
    group:
      production:
        - prod
        - monitoring
        - secure
      development:
        - dev
        - debug
```

**Activate group:**
```bash
java -jar app.jar --spring.profiles.active=production
# Activates: prod, monitoring, secure
```

### Default Profile

**application.yml:**
```yaml
spring:
  profiles:
    default: dev  # Used if no profile specified
```

### Conditional on Profile

```java
@Service
@Profile("!prod")  // NOT production
public class MockEmailService implements EmailService {
    public void send(String to, String message) {
        log.info("MOCK EMAIL: {} - {}", to, message);
    }
}

@Service
@Profile("prod")
public class RealEmailService implements EmailService {
    public void send(String to, String message) {
        // Actually send email via SMTP
    }
}
```

## Actuator

### What is Spring Boot Actuator?

**Actuator provides production-ready features:**
- Health checks
- Metrics
- Application info
- Thread dumps
- HTTP trace
- Environment properties

**Add dependency:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Enabling Endpoints

**application.yml:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env,loggers
      base-path: /actuator
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

### Health Endpoint

**Check application health:**

```bash
GET http://localhost:8081/actuator/health

Response:
{
  "status": "UP",
  "components": {
    "mongo": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.2.3"
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "kafka-cluster-1"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500000000000,
        "free": 250000000000,
        "threshold": 10485760
      }
    }
  }
}
```

### Custom Health Indicator

```java
@Component
public class PortfolioServiceHealthIndicator implements HealthIndicator {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Override
    public Health health() {
        try {
            long count = portfolioRepository.count();
            
            if (count >= 0) {
                return Health.up()
                    .withDetail("portfolioCount", count)
                    .withDetail("status", "Database connection OK")
                    .build();
            } else {
                return Health.down()
                    .withDetail("error", "Unexpected portfolio count")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}
```

### Metrics Endpoint

**View application metrics:**

```bash
GET http://localhost:8081/actuator/metrics

Response:
{
  "names": [
    "jvm.memory.used",
    "jvm.memory.max",
    "jvm.gc.pause",
    "http.server.requests",
    "kafka.producer.request.total",
    "cache.gets",
    "cache.puts"
  ]
}
```

**Get specific metric:**

```bash
GET http://localhost:8081/actuator/metrics/http.server.requests

Response:
{
  "name": "http.server.requests",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1523.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 42.5
    },
    {
      "statistic": "MAX",
      "value": 0.156
    }
  ],
  "availableTags": [
    {
      "tag": "uri",
      "values": ["/api/portfolios", "/api/portfolios/{id}"]
    },
    {
      "tag": "method",
      "values": ["GET", "POST", "PUT", "DELETE"]
    },
    {
      "tag": "status",
      "values": ["200", "201", "404", "500"]
    }
  ]
}
```

### Custom Metrics

```java
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final MeterRegistry meterRegistry;
    private final PortfolioRepository portfolioRepository;

    public PortfolioDTO.Response createPortfolio(PortfolioDTO.CreateRequest request) {
        // Increment counter
        meterRegistry.counter("portfolios.created", 
            "owner", request.getOwnerName()).increment();
        
        // Time the operation
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            Portfolio created = /* create portfolio */;
            
            sample.stop(meterRegistry.timer("portfolios.creation.time"));
            
            return portfolioMapper.toResponse(created);
        } catch (Exception e) {
            meterRegistry.counter("portfolios.creation.errors").increment();
            throw e;
        }
    }
    
    @Scheduled(fixedRate = 60000)
    public void recordPortfolioMetrics() {
        long totalPortfolios = portfolioRepository.count();
        
        // Gauge for current value
        meterRegistry.gauge("portfolios.total", totalPortfolios);
    }
}
```

### Info Endpoint

**application.yml:**

```yaml
info:
  app:
    name: Portfolio Management Service
    description: Wealth management portfolio service
    version: 1.0.0
  build:
    artifact: '@project.artifactId@'
    version: '@project.version@'
    time: '@maven.build.timestamp@'
  team:
    name: Portfolio Team
    email: portfolio-team@example.com
```

**Response:**

```bash
GET http://localhost:8081/actuator/info

{
  "app": {
    "name": "Portfolio Management Service",
    "description": "Wealth management portfolio service",
    "version": "1.0.0"
  },
  "build": {
    "artifact": "portfolio-service",
    "version": "1.0.0-SNAPSHOT",
    "time": "2025-12-30T10:00:00Z"
  },
  "team": {
    "name": "Portfolio Team",
    "email": "portfolio-team@example.com"
  }
}
```

### Environment Endpoint

**View all properties:**

```bash
GET http://localhost:8081/actuator/env

Response:
{
  "activeProfiles": ["dev"],
  "propertySources": [
    {
      "name": "systemEnvironment",
      "properties": {
        "PATH": {...},
        "JAVA_HOME": {...}
      }
    },
    {
      "name": "applicationConfig: [classpath:/application-dev.yml]",
      "properties": {
        "spring.data.mongodb.uri": {
          "value": "mongodb://localhost:27017/portfolio_db"
        }
      }
    }
  ]
}
```

### Loggers Endpoint

**View log levels:**

```bash
GET http://localhost:8081/actuator/loggers

GET http://localhost:8081/actuator/loggers/com.wealthmanagement.portfolio
{
  "configuredLevel": "DEBUG",
  "effectiveLevel": "DEBUG"
}
```

**Change log level at runtime:**

```bash
POST http://localhost:8081/actuator/loggers/com.wealthmanagement.portfolio
Content-Type: application/json

{
  "configuredLevel": "TRACE"
}

# No restart needed! Log level changed immediately.
```

### Prometheus Integration

**Add dependency:**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**application.yml:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**Scrape metrics:**

```bash
GET http://localhost:8081/actuator/prometheus

# Prometheus format
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 1.2345678E8

# HELP http_server_requests_seconds  
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="GET",status="200",uri="/api/portfolios",} 1523.0
http_server_requests_seconds_sum{method="GET",status="200",uri="/api/portfolios",} 42.5
```

### Securing Actuator Endpoints

**application.yml:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info  # Only expose safe endpoints
  endpoint:
    health:
      show-details: when-authorized  # Hide details from unauthenticated users
```

**With Spring Security:**

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

## Spring Boot 3 Features

### What's New in Spring Boot 3?

**Major Changes:**
- ✅ **Java 17 baseline** (minimum requirement)
- ✅ **Jakarta EE 9+** (javax → jakarta namespace)
- ✅ **Native compilation** (GraalVM)
- ✅ **Observability improvements** (Micrometer Tracing)
- ✅ **HTTP interface clients**
- ✅ **Problem Details (RFC 7807)**

### Java 17 Requirement

**Our project uses Java 17 features:**

**1. Records (Data Carriers):**

```java
// Instead of traditional DTO
@Data
@Builder
public class PortfolioDTO {
    private String id;
    private String accountNumber;
    private String ownerName;
}

// Use Java 17 record
public record PortfolioDTO(
    String id,
    String accountNumber,
    String ownerName
) {
    // Automatically gets: constructor, getters, equals, hashCode, toString
}
```

**2. Text Blocks:**

```java
// Old way
String json = "{\n" +
              "  \"accountNumber\": \"ACC001\",\n" +
              "  \"ownerName\": \"John Doe\"\n" +
              "}";

// New way (Java 17)
String json = """
    {
      "accountNumber": "ACC001",
      "ownerName": "John Doe"
    }
    """;
```

**3. Pattern Matching for instanceof:**

```java
// Old way
if (event instanceof PortfolioEvent) {
    PortfolioEvent portfolioEvent = (PortfolioEvent) event;
    processPortfolioEvent(portfolioEvent);
}

// New way
if (event instanceof PortfolioEvent portfolioEvent) {
    processPortfolioEvent(portfolioEvent);  // Already cast!
}
```

**4. Switch Expressions:**

```java
// Used in our Notification Service
String message = switch (event.getEventType()) {
    case TRANSACTION_CREATED -> 
        String.format("Transaction %s created", event.getTransactionId());
    case TRANSACTION_COMPLETED -> 
        String.format("Transaction %s completed", event.getTransactionId());
    case TRANSACTION_FAILED -> 
        String.format("Transaction %s failed", event.getTransactionId());
    default -> 
        String.format("Transaction %s update", event.getTransactionId());
};
```

### Jakarta EE Namespace Change

**Spring Boot 2.x:**
```java
import javax.servlet.http.HttpServletRequest;
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;
```

**Spring Boot 3.x:**
```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
```

**Migration required:**
- Find: `javax.`
- Replace: `jakarta.`

### Native Compilation (GraalVM)

**Build native executable:**

**pom.xml:**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.graalvm.buildtools</groupId>
            <artifactId>native-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**Build:**
```bash
mvn -Pnative native:compile

# Creates native executable (no JVM needed!)
# Startup time: ~0.1s (vs ~3s with JVM)
# Memory: ~50MB (vs ~300MB with JVM)
```

**Benefits:**
- ✅ Lightning-fast startup
- ✅ Lower memory footprint
- ✅ Instant scale-to-zero
- ✅ Perfect for serverless/containers

**Limitations:**
- ❌ Longer build time (minutes)
- ❌ Some reflection/dynamic features not supported
- ❌ Debugging harder

### Observability with Micrometer Tracing

**Distributed tracing across microservices:**

**Add dependencies:**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**application.yml:**

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% of requests (reduce in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**Automatic tracing:**

```
Request Flow:
1. Frontend → API Gateway
   TraceId: abc123, SpanId: span-1
   
2. API Gateway → Portfolio Service
   TraceId: abc123, SpanId: span-2, ParentId: span-1
   
3. Portfolio Service → MongoDB
   TraceId: abc123, SpanId: span-3, ParentId: span-2
   
4. Portfolio Service → Kafka
   TraceId: abc123, SpanId: span-4, ParentId: span-2
```

**View in Zipkin:**
```
http://localhost:9411

Shows complete trace with:
- Request duration
- Each service involved
- Database queries
- Kafka publishes
- Error stack traces
```

### HTTP Interface Clients

**Declarative HTTP clients (like Feign):**

**Old way (RestTemplate):**
```java
@Service
public class PortfolioClient {
    private final RestTemplate restTemplate;
    
    public Portfolio getPortfolio(String id) {
        return restTemplate.getForObject(
            "http://portfolio-service/api/portfolios/" + id,
            Portfolio.class
        );
    }
}
```

**New way (HTTP Interface):**

```java
@HttpExchange("/api/portfolios")
public interface PortfolioClient {
    
    @GetExchange("/{id}")
    Portfolio getPortfolio(@PathVariable String id);
    
    @PostExchange
    Portfolio createPortfolio(@RequestBody CreateRequest request);
    
    @PutExchange("/{id}")
    Portfolio updatePortfolio(@PathVariable String id, @RequestBody UpdateRequest request);
    
    @DeleteExchange("/{id}")
    void deletePortfolio(@PathVariable String id);
}
```

**Configuration:**

```java
@Configuration
public class ClientConfig {

    @Bean
    public PortfolioClient portfolioClient() {
        WebClient webClient = WebClient.builder()
            .baseUrl("http://portfolio-service")
            .build();
        
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builder(WebClientAdapter.forClient(webClient))
            .build();
        
        return factory.createClient(PortfolioClient.class);
    }
}
```

### Problem Details (RFC 7807)

**Standardized error responses:**

**Enable:**
```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

**Old error response:**
```json
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Portfolio not found",
  "path": "/api/portfolios/123"
}
```

**New RFC 7807 response:**
```json
{
  "type": "https://api.example.com/errors/not-found",
  "title": "Portfolio Not Found",
  "status": 404,
  "detail": "Portfolio with ID 123 does not exist",
  "instance": "/api/portfolios/123",
  "timestamp": "2025-12-30T10:00:00"
}
```

**Custom Problem Details:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ProblemDetail handlePortfolioNotFound(PortfolioNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problemDetail.setType(URI.create("https://api.example.com/errors/portfolio-not-found"));
        problemDetail.setTitle("Portfolio Not Found");
        problemDetail.setProperty("portfolioId", ex.getPortfolioId());
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
}
```

### Virtual Threads (Preview in Java 21)

**Enable virtual threads:**

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**What are Virtual Threads?**
- Lightweight threads (thousands vs hundreds)
- Better scalability for I/O-bound apps
- No code changes needed
- JVM manages scheduling

**Before (Platform Threads):**
```
Max concurrent requests: ~200-500
(Limited by thread pool size)
```

**After (Virtual Threads):**
```
Max concurrent requests: ~10,000+
(Limited by CPU/memory, not threads)
```

### Enhanced Docker Support

**Build optimized Docker images:**

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jre-jammy

# Use layered JAR for better caching
COPY target/*.jar app.jar

# Extract layers
RUN java -Djarmode=layertools -jar app.jar extract

# Copy layers separately (better Docker cache)
COPY dependencies/ ./
COPY spring-boot-loader/ ./
COPY snapshot-dependencies/ ./
COPY application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
```

**Benefits:**
- ✅ Faster builds (cached layers)
- ✅ Smaller image updates
- ✅ Better CI/CD performance

### Configuration Properties Validation

**Enhanced validation in Spring Boot 3:**

```java
@ConfigurationProperties(prefix = "redis")
@Validated
public class RedisProperties {

    @NotBlank
    private String host;
    
    @Min(1024)
    @Max(65535)
    private int port;
    
    @DurationMin(seconds = 1)
    @DurationMax(minutes = 60)
    private Duration timeout;
    
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    private String keyPrefix;
}
```

### Summary: Why Spring Boot 3?

**Performance:**
- ✅ Java 17 optimizations
- ✅ Native compilation option
- ✅ Virtual threads (when using Java 21)

**Developer Experience:**
- ✅ Records for DTOs
- ✅ HTTP interface clients
- ✅ Better error responses (Problem Details)

**Observability:**
- ✅ Built-in distributed tracing
- ✅ Micrometer integration
- ✅ Better metrics

**Standards:**
- ✅ Jakarta EE compliance
- ✅ RFC 7807 problem details
- ✅ Modern Java features

---

## Summary

**Spring Boot Essentials Covered:**

1. **Spring Boot Overview**
   - Convention over configuration
   - Starter dependencies
   - Embedded servers
   - Fat JAR deployment

2. **Dependency Injection**
   - Constructor injection (recommended)
   - Bean scopes and lifecycle
   - @Qualifier for multiple implementations
   - Circular dependency solutions

3. **Annotations**
   - @RestController, @Service, @Repository
   - @RequestMapping, @PathVariable, @RequestBody
   - @Cacheable, @Transactional, @Async
   - @Scheduled, @ConditionalOnProperty

4. **Auto-configuration**
   - How Spring Boot detects and configures beans
   - Disabling auto-configuration
   - Custom auto-configuration

5. **Properties Management**
   - application.yml vs application.properties
   - @Value vs @ConfigurationProperties
   - Property validation
   - Environment variables

6. **Profiles**
   - dev, test, prod configurations
   - Profile-specific beans
   - Profile activation
   - Profile groups

7. **Actuator**
   - Health checks
   - Metrics (JVM, HTTP, custom)
   - Prometheus integration
   - Securing endpoints

8. **Spring Boot 3 Features**
   - Java 17 requirement (records, text blocks, switch expressions)
   - Jakarta EE namespace (javax → jakarta)
   - Native compilation with GraalVM
   - Micrometer Tracing (distributed tracing)
   - HTTP interface clients
   - Problem Details (RFC 7807)
   - Virtual threads support

**For Interviews:**
- Explain why constructor injection is preferred
- Describe how auto-configuration works
- Walk through Actuator endpoints
- Discuss Spring Boot 3 benefits (Java 17, native compilation, observability)
- Demonstrate profile usage for different environments
- Show how to create custom metrics and health indicators
