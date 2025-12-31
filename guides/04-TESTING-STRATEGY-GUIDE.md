# Testing Strategy Guide

## Table of Contents
1. [Testing Pyramid](#testing-pyramid)
2. [Unit Testing](#unit-testing)
3. [Integration Testing](#integration-testing)
4. [Mocking Strategy](#mocking-strategy)
5. [Test Containers](#test-containers)
6. [API Testing](#api-testing)
7. [Coverage Goals](#coverage-goals)
8. [Running Tests](#running-tests)

---

## Testing Pyramid

### Test Distribution Strategy

```
           /\
          /E2E\           ← 10% (Selenium, Playwright)
         /------\
        /  API   \        ← 20% (MockMvc, integration tests)
       /----------\
      /    Unit    \      ← 70% (JUnit, Mockito)
     /--------------\
```

### Layer Breakdown

**1. Unit Tests (70%)**
- **Purpose:** Test individual classes in isolation
- **Speed:** Fastest (milliseconds)
- **Cost:** Cheapest to maintain
- **Example:** `PortfolioServiceTest.java`
  - Tests business logic without database
  - Mocks all dependencies
  - Validates edge cases

**2. Integration Tests (20%)**
- **Purpose:** Test component interactions
- **Speed:** Moderate (seconds)
- **Cost:** Medium maintenance
- **Example:** `PortfolioControllerTest.java`
  - Tests REST endpoints with MockMvc
  - Spring context loaded
  - Service layer mocked

**3. End-to-End Tests (10%)**
- **Purpose:** Test complete user flows
- **Speed:** Slowest (minutes)
- **Cost:** Expensive to maintain
- **Example:** Frontend Playwright tests
  - Browser automation
  - Full system running
  - Critical user journeys

### Why This Ratio?

| Aspect | Unit | Integration | E2E |
|--------|------|-------------|-----|
| **Execution Time** | 1ms | 100ms | 10s |
| **Flakiness** | Low | Medium | High |
| **Debugging Ease** | Easy | Moderate | Hard |
| **Maintenance** | Low | Medium | High |

**Rule of Thumb:** More tests at the bottom = faster feedback, cheaper maintenance

## Unit Testing

### JUnit 5 Basics

**Location:** `PortfolioServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Portfolio Service Tests")
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

    @InjectMocks
    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        // Arrange: Set up test data
        createRequest = PortfolioDTO.CreateRequest.builder()
                .clientId("CLIENT123")
                .clientName("John Doe")
                .build();
    }

    @Test
    @DisplayName("Should create portfolio successfully")
    void shouldCreatePortfolioSuccessfully() {
        // Given (Arrange)
        when(portfolioRepository.save(any())).thenReturn(portfolio);

        // When (Act)
        PortfolioDTO.Response result = portfolioService.createPortfolio(createRequest);

        // Then (Assert)
        assertThat(result.getClientId()).isEqualTo("CLIENT123");
        verify(portfolioRepository).save(any(Portfolio.class));
    }
}
```

### Key Annotations

| Annotation | Purpose | Example |
|------------|---------|---------|
| `@ExtendWith(MockitoExtension.class)` | Enable Mockito for JUnit 5 | Class level |
| `@Mock` | Create mock dependency | Repository, KafkaTemplate |
| `@InjectMocks` | Inject mocks into class under test | PortfolioService |
| `@BeforeEach` | Run before every test | Setup test data |
| `@Test` | Mark method as test | Test methods |
| `@DisplayName` | Human-readable test name | "Should create portfolio" |

### AAA Pattern (Arrange-Act-Assert)

```java
@Test
void shouldCalculateTotalValueCorrectly() {
    // Arrange - Set up test conditions
    Portfolio portfolio = createPortfolioWithHoldings();
    when(repository.findById("PORT001")).thenReturn(Optional.of(portfolio));

    // Act - Execute the behavior
    PortfolioDTO.Summary summary = portfolioService.getPortfolioSummary("PORT001");

    // Assert - Verify the outcome
    assertThat(summary.getTotalValue())
        .isEqualByComparingTo(new BigDecimal("150000.00"));
}
```

### AssertJ Fluent Assertions

```java
// Basic assertions
assertThat(result).isNotNull();
assertThat(result.getId()).isEqualTo("PORT001");

// BigDecimal comparison
assertThat(portfolio.getCashBalance())
    .isEqualByComparingTo(new BigDecimal("100000.00"));

// Collections
assertThat(portfolios).hasSize(3);
assertThat(holdings).extracting("symbol").contains("AAPL", "GOOGL");

// Exceptions
assertThatThrownBy(() -> service.getPortfolio("INVALID"))
    .isInstanceOf(PortfolioNotFoundException.class)
    .hasMessage("Portfolio not found with id: INVALID");
```

## Integration Testing

### Spring Boot Test Annotations

**Location:** `PortfolioControllerTest.java`

```java
@WebMvcTest(PortfolioController.class)
@DisplayName("Portfolio Controller Tests")
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    void shouldCreatePortfolioSuccessfully() throws Exception {
        // Given
        when(portfolioService.createPortfolio(any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("PORT001"))
            .andExpect(jsonPath("$.clientId").value("CLIENT123"));
    }
}
```

### Test Slice Annotations

| Annotation | What It Tests | Loaded Components | Use Case |
|------------|---------------|-------------------|----------|
| `@WebMvcTest` | **Web Layer** | Controllers, Jackson, Validators | Test REST endpoints |
| `@DataMongoTest` | **MongoDB Layer** | Repositories, MongoDB | Test database queries |
| `@SpringBootTest` | **Full Context** | Entire application | End-to-end integration |
| `@MockBean` | **Mock Dependency** | Spring-managed mock | Replace real beans |

### @WebMvcTest Example

```java
@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {
    // Only loads:
    // - PortfolioController
    // - Jackson (JSON serialization)
    // - Validation framework
    // Does NOT load:
    // - Service layer (use @MockBean)
    // - Database connections
    // - Kafka producers
}
```

**Benefits:**
- ✅ Fast (no full context startup)
- ✅ Focused (only web layer)
- ✅ Isolated (no external dependencies)

### @SpringBootTest Example

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class PortfolioIntegrationTest {
    
    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateAndRetrievePortfolio() {
        // Full integration test with real database
        ResponseEntity<PortfolioDTO.Response> response = 
            restTemplate.postForEntity("/portfolios", createRequest, PortfolioDTO.Response.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
```

### MockMvc Cheat Sheet

```java
// POST request
mockMvc.perform(post("/portfolios")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json))
    .andExpect(status().isCreated());

// GET request
mockMvc.perform(get("/portfolios/{id}", "PORT001"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value("PORT001"));

// PUT request
mockMvc.perform(put("/portfolios/{id}/cash", "PORT001")
        .param("amount", "5000"))
    .andExpect(status().isOk());

// DELETE request
mockMvc.perform(delete("/portfolios/{id}", "PORT001"))
    .andExpect(status().isNoContent());

// Error handling
mockMvc.perform(get("/portfolios/{id}", "INVALID"))
    .andExpect(status().isNotFound())
    .andExpect(jsonPath("$.message").value("Portfolio not found"));
```

## Mocking Strategy

### When to Mock

✅ **DO Mock:**
- External services (Kafka, Redis)
- Database repositories
- Third-party APIs
- Slow dependencies
- Non-deterministic behavior (random, time)

❌ **DON'T Mock:**
- The class under test
- Value objects (DTOs, entities)
- Simple utility methods
- The entire system (defeats testing purpose)

### Mockito Basics

```java
// 1. Creating mocks
@Mock
private PortfolioRepository repository;

// 2. Stubbing behavior
when(repository.findById("PORT001"))
    .thenReturn(Optional.of(portfolio));

// 3. Verifying interactions
verify(repository).save(any(Portfolio.class));
verify(kafkaTemplate).send(eq("portfolio-events"), any());

// 4. Argument matching
when(repository.save(argThat(p -> p.getCashBalance().compareTo(BigDecimal.ZERO) > 0)))
    .thenReturn(portfolio);

// 5. Exception stubbing
when(repository.findById("INVALID"))
    .thenThrow(new PortfolioNotFoundException("Portfolio not found"));
```

### Verification Modes

```java
// Verify exact calls
verify(repository, times(1)).save(any());
verify(repository, times(2)).findById(anyString());

// Verify never called
verify(repository, never()).delete(any());

// Verify at least/most
verify(kafkaTemplate, atLeast(1)).send(anyString(), any());
verify(kafkaTemplate, atMost(3)).send(anyString(), any());

// Verify no more interactions
verifyNoMoreInteractions(repository);
```

### Argument Captors

```java
@Test
void shouldPublishPortfolioCreatedEvent() {
    // Given
    ArgumentCaptor<PortfolioEvent> eventCaptor = ArgumentCaptor.forClass(PortfolioEvent.class);
    
    // When
    portfolioService.createPortfolio(createRequest);
    
    // Then
    verify(kafkaTemplate).send(eq("portfolio-events"), eventCaptor.capture());
    
    PortfolioEvent capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.getEventType()).isEqualTo("PORTFOLIO_CREATED");
    assertThat(capturedEvent.getPortfolioId()).isEqualTo("PORT001");
}
```

### Mock vs Spy

```java
// Mock - All methods return default values
@Mock
private PortfolioRepository repository;  // All methods stubbed

// Spy - Partial mocking (real methods unless stubbed)
@Spy
private PortfolioMapper mapper = new PortfolioMapperImpl();  // Real methods work
when(mapper.toEntity(any())).thenCallRealMethod();  // Can override specific methods
```

### Best Practices

**1. Verify Behavior, Not Implementation**
```java
// ❌ Bad - Too coupled to implementation
verify(repository).findById("PORT001");
verify(mapper).toResponse(portfolio);
verify(kafkaTemplate).send("portfolio-events", event);

// ✅ Good - Verify outcome
PortfolioDTO.Response result = service.getPortfolio("PORT001");
assertThat(result.getId()).isEqualTo("PORT001");
```

**2. Use Argument Matchers Consistently**
```java
// ❌ Bad - Mixing concrete and matchers
verify(kafkaTemplate).send("portfolio-events", any());  // Error!

// ✅ Good - All matchers or all concrete
verify(kafkaTemplate).send(eq("portfolio-events"), any());
```

**3. Reset Mocks Between Tests**
```java
@BeforeEach
void setUp() {
    // Mockito automatically resets @Mock fields
    // Manual reset only if using static Mockito.mock()
    Mockito.reset(customMock);
}
```

## Test Containers

### What is Testcontainers?

**Testcontainers** is a Java library that provides lightweight, throwaway instances of Docker containers for integration testing.

**Benefits:**
- ✅ Real database instances (not H2 in-memory)
- ✅ Test against production environment
- ✅ Automatic cleanup after tests
- ✅ Isolated test execution

### MongoDB Testcontainer

**Dependency:** Add to `pom.xml`
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**Usage Example:**
```java
@SpringBootTest
@Testcontainers
class PortfolioRepositoryIntegrationTest {

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl);
    }

    @Autowired
    private PortfolioRepository repository;

    @Test
    void shouldSaveAndFindPortfolio() {
        // Given
        Portfolio portfolio = Portfolio.builder()
                .clientId("CLIENT123")
                .accountNumber("ACC001")
                .build();

        // When
        Portfolio saved = repository.save(portfolio);
        Optional<Portfolio> found = repository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getClientId()).isEqualTo("CLIENT123");
    }
}
```

### Redis Testcontainer

```java
@SpringBootTest
@Testcontainers
class CacheIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private CacheManager cacheManager;

    @Test
    void shouldCachePortfolio() {
        Cache cache = cacheManager.getCache("portfolios");
        cache.put("PORT001", portfolioResponse);
        
        assertThat(cache.get("PORT001")).isNotNull();
    }
}
```

### Kafka Testcontainer

```java
@SpringBootTest
@Testcontainers
class KafkaEventIntegrationTest {

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, PortfolioEvent> kafkaTemplate;

    @Test
    void shouldPublishEvent() throws Exception {
        // When
        PortfolioEvent event = new PortfolioEvent("PORTFOLIO_CREATED", "PORT001");
        kafkaTemplate.send("portfolio-events", event).get();

        // Then - Consumer receives event (use CountDownLatch or Awaitility)
    }
}
```

### Testcontainers Best Practices

**1. Reuse Containers Across Tests**
```java
@Testcontainers
class MyIntegrationTests {
    // static container - shared across all tests in class
    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7");
}
```

**2. Wait Strategies**
```java
@Container
static GenericContainer<?> customContainer = new GenericContainer<>("myapp:latest")
    .waitingFor(Wait.forHttp("/health")
        .forStatusCode(200)
        .withStartupTimeout(Duration.ofMinutes(2)));
```

**3. Network Communication**
```java
@Container
static Network network = Network.newNetwork();

@Container
static MongoDBContainer mongo = new MongoDBContainer("mongo:7")
    .withNetwork(network)
    .withNetworkAliases("mongodb");

@Container
static GenericContainer<?> app = new GenericContainer<>("myapp:latest")
    .withNetwork(network)
    .withEnv("MONGO_HOST", "mongodb");
```

## API Testing

### MockMvc Deep Dive

**MockMvc** tests Spring MVC controllers without starting a full HTTP server.

**Setup:**
```java
@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;  // JSON serialization

    @MockBean
    private PortfolioService portfolioService;
}
```

### Testing POST Endpoints

```java
@Test
void shouldCreatePortfolio() throws Exception {
    // Given
    PortfolioDTO.CreateRequest request = PortfolioDTO.CreateRequest.builder()
            .clientId("CLIENT123")
            .clientName("John Doe")
            .accountNumber("ACC001")
            .currency("USD")
            .cashBalance(new BigDecimal("100000.00"))
            .build();

    PortfolioDTO.Response response = PortfolioDTO.Response.builder()
            .id("PORT001")
            .clientId("CLIENT123")
            .build();

    when(portfolioService.createPortfolio(any())).thenReturn(response);

    // When & Then
    mockMvc.perform(post("/portfolios")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").value("PORT001"))
        .andExpect(jsonPath("$.clientId").value("CLIENT123"))
        .andDo(print());  // Print request/response for debugging
}
```

### Testing GET Endpoints

```java
@Test
void shouldGetPortfolioById() throws Exception {
    // Given
    when(portfolioService.getPortfolioById("PORT001")).thenReturn(response);

    // When & Then
    mockMvc.perform(get("/portfolios/{id}", "PORT001")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("PORT001"))
        .andExpect(jsonPath("$.clientName").value("John Doe"))
        .andExpect(jsonPath("$.cashBalance").value(100000.00));
}

@Test
void shouldGetAllPortfolios() throws Exception {
    // Given
    List<PortfolioDTO.Summary> portfolios = List.of(summary1, summary2, summary3);
    when(portfolioService.getAllPortfolios()).thenReturn(portfolios);

    // When & Then
    mockMvc.perform(get("/portfolios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].id").value("PORT001"))
        .andExpect(jsonPath("$[1].id").value("PORT002"));
}
```

### Testing PUT Endpoints

```java
@Test
void shouldUpdateCashBalance() throws Exception {
    // Given
    when(portfolioService.updateCashBalance("PORT001", new BigDecimal("5000")))
        .thenReturn(updatedResponse);

    // When & Then
    mockMvc.perform(put("/portfolios/{id}/cash", "PORT001")
            .param("amount", "5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cashBalance").value(105000.00));
}
```

### Testing DELETE Endpoints

```java
@Test
void shouldDeletePortfolio() throws Exception {
    // Given
    doNothing().when(portfolioService).deletePortfolio("PORT001");

    // When & Then
    mockMvc.perform(delete("/portfolios/{id}", "PORT001"))
        .andExpect(status().isNoContent());

    verify(portfolioService).deletePortfolio("PORT001");
}
```

### Testing Error Responses

```java
@Test
void shouldReturn404WhenPortfolioNotFound() throws Exception {
    // Given
    when(portfolioService.getPortfolioById("INVALID"))
        .thenThrow(new PortfolioNotFoundException("Portfolio not found with id: INVALID"));

    // When & Then
    mockMvc.perform(get("/portfolios/{id}", "INVALID"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Portfolio not found with id: INVALID"))
        .andExpect(jsonPath("$.timestamp").exists());
}

@Test
void shouldReturn400WhenValidationFails() throws Exception {
    // Given
    PortfolioDTO.CreateRequest invalidRequest = PortfolioDTO.CreateRequest.builder()
        .clientId("")  // Invalid - empty
        .cashBalance(new BigDecimal("-1000"))  // Invalid - negative
        .build();

    // When & Then
    mockMvc.perform(post("/portfolios")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").exists())
        .andExpect(jsonPath("$.errors.clientId").exists());
}
```

### JsonPath Expressions

```java
// Simple field
.andExpect(jsonPath("$.id").value("PORT001"))

// Nested field
.andExpect(jsonPath("$.holdings[0].symbol").value("AAPL"))

// Array size
.andExpect(jsonPath("$.holdings.length()").value(3))

// Array contains
.andExpect(jsonPath("$.holdings[*].symbol").value(hasItem("GOOGL")))

// Null check
.andExpect(jsonPath("$.closedDate").doesNotExist())

// Number comparison
.andExpect(jsonPath("$.totalValue").value(greaterThan(100000.0)))

// Regex matching
.andExpect(jsonPath("$.accountNumber").value(matchesPattern("ACC\\d{3}")))
```

### WebTestClient (for WebFlux)

If using Spring WebFlux instead of MVC:

```java
@WebFluxTest(PortfolioController.class)
class PortfolioControllerWebFluxTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldCreatePortfolio() {
        webTestClient.post()
            .uri("/portfolios")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(PortfolioDTO.Response.class)
            .value(response -> assertThat(response.getId()).isEqualTo("PORT001"));
    }
}
```

## Coverage Goals

### Industry Standards

| Coverage Type | Target | Why |
|---------------|--------|-----|
| **Line Coverage** | **80%+** | Most commonly tracked metric |
| **Branch Coverage** | **70%+** | Ensures all if/else paths tested |
| **Method Coverage** | **85%+** | All public methods should be tested |
| **Class Coverage** | **90%+** | Nearly all classes have tests |

### Jacoco Maven Plugin

**Location:** `pom.xml` (parent or service level)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <!-- Prepare agent before tests -->
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <!-- Generate report after tests -->
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <!-- Enforce coverage requirements -->
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>PACKAGE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.80</minimum>
                                    </limit>
                                    <limit>
                                        <counter>BRANCH</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.70</minimum>
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Generate Coverage Report

```powershell
# Run tests and generate coverage
mvn clean test

# View report
# Open: target/site/jacoco/index.html
```

**Report Structure:**
```
target/site/jacoco/
├── index.html           # Overall summary
├── com.wealthmanagement.portfolio/
│   ├── service/
│   │   └── PortfolioService.html
│   ├── controller/
│   │   └── PortfolioController.html
│   └── repository/
```

### Reading Jacoco Reports

**Color Coding:**
- 🟢 **Green:** Covered (executed during tests)
- 🟡 **Yellow:** Partially covered (some branches missed)
- 🔴 **Red:** Not covered (never executed)

**Example Report:**
```
Package: com.wealthmanagement.portfolio.service
┌─────────────────┬────────────┬──────────────┬─────────────┐
│ Class           │ Line Cov   │ Branch Cov   │ Method Cov  │
├─────────────────┼────────────┼──────────────┼─────────────┤
│ PortfolioService│ 85% (170/200) │ 72% (36/50) │ 90% (18/20) │
│ HoldingService  │ 78% (95/122)  │ 68% (21/31) │ 85% (11/13) │
└─────────────────┴────────────┴──────────────┴─────────────┘
```

### Excluding Code from Coverage

```java
// Exclude entire class
@ExcludeFromCodeCoverage
public class PortfolioApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}

// Or use Jacoco configuration
<configuration>
    <excludes>
        <exclude>**/config/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/Application.class</exclude>
    </excludes>
</configuration>
```

### What NOT to Test (Diminishing Returns)

❌ **Skip these:**
- Getters/Setters (Lombok-generated)
- Configuration classes (Spring auto-configuration)
- DTOs (simple data containers)
- Main application class
- Constants/Enums

✅ **Focus on these:**
- Business logic (Service layer)
- Controllers (API contracts)
- Repositories (custom queries)
- Validators
- Exception handlers
- Utility methods

### Coverage vs Quality

⚠️ **Remember:** 100% coverage ≠ bug-free code

```java
// 100% coverage, but poor test
@Test
void testCreatePortfolio() {
    service.createPortfolio(request);  // No assertions!
}

// Better test
@Test
void shouldCreatePortfolioAndPublishEvent() {
    PortfolioDTO.Response result = service.createPortfolio(request);
    
    assertThat(result.getId()).isNotNull();
    verify(kafkaTemplate).send(eq("portfolio-events"), any());
    verify(cacheManager.getCache("portfolios")).put(result.getId(), result);
}
```

**Quality Metrics:**
- ✅ Assertions verify expected behavior
- ✅ Edge cases tested (null, empty, invalid)
- ✅ Error paths covered
- ✅ Interactions verified (mocks)

## Running Tests

### Maven Commands

**Run all tests:**
```powershell
mvn test
```

**Run tests for specific service:**
```powershell
cd portfolio-service
mvn test
```

**Run specific test class:**
```powershell
mvn test -Dtest=PortfolioServiceTest
```

**Run specific test method:**
```powershell
mvn test -Dtest=PortfolioServiceTest#shouldCreatePortfolioSuccessfully
```

**Run tests with coverage:**
```powershell
mvn clean test jacoco:report
```

**Skip tests during build:**
```powershell
mvn clean install -DskipTests
```

**Run tests in parallel:**
```powershell
mvn test -T 4  # 4 threads
```

### IDE Integration (IntelliJ IDEA)

**Run Single Test:**
1. Right-click test method → **Run 'shouldCreatePortfolio()'**
2. Keyboard: `Ctrl + Shift + F10`

**Run Test Class:**
1. Right-click test class → **Run 'PortfolioServiceTest'**

**Run with Coverage:**
1. Right-click → **Run 'PortfolioServiceTest' with Coverage**
2. Keyboard: `Ctrl + Shift + F10` + Coverage icon

**Debug Test:**
1. Set breakpoint in test or production code
2. Right-click → **Debug 'shouldCreatePortfolio()'**
3. Keyboard: `Ctrl + Shift + F9`

**Rerun Failed Tests:**
1. After test run, click **Rerun Failed Tests** icon in test panel

### Continuous Integration (CI/CD)

**GitHub Actions Example:**

`.github/workflows/build.yml`
```yaml
name: Build and Test

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run tests
      run: mvn clean test
    
    - name: Generate coverage report
      run: mvn jacoco:report
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        files: ./target/site/jacoco/jacoco.xml
```

### Test Execution Order

**Default:** Random (non-deterministic)

**Ordered Execution:**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortfolioServiceTest {

    @Test
    @Order(1)
    void shouldCreatePortfolio() { }

    @Test
    @Order(2)
    void shouldAddHolding() { }

    @Test
    @Order(3)
    void shouldDeletePortfolio() { }
}
```

**Best Practice:** Tests should be independent! Avoid ordered tests unless integration testing specific flows.

### Test Profiles

**application-test.yml:**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/test_db
  redis:
    host: localhost
    port: 6379
  kafka:
    bootstrap-servers: localhost:9092

logging:
  level:
    com.wealthmanagement: DEBUG
```

**Activate profile:**
```java
@SpringBootTest
@ActiveProfiles("test")
class PortfolioIntegrationTest {
    // Uses application-test.yml configuration
}
```

### Troubleshooting

**Problem:** Tests fail in CI but pass locally
```
Reason: Different environments (local vs CI)
Solution:
- Use Testcontainers for consistent environment
- Check timezone differences
- Verify database state cleanup
```

**Problem:** Flaky tests (intermittent failures)
```
Reason: Race conditions, timing issues, shared state
Solution:
- Use Awaitility for async operations
- Add proper test isolation (@DirtiesContext)
- Fix test dependencies
```

**Problem:** Slow test execution
```
Reason: Full Spring context loading, slow dependencies
Solution:
- Use @WebMvcTest instead of @SpringBootTest
- Mock slow dependencies
- Run tests in parallel (Maven Surefire)
- Use test slicing
```

**Example - Awaitility for async:**
```java
@Test
void shouldProcessEventAsynchronously() {
    // Trigger async operation
    kafkaTemplate.send("portfolio-events", event);

    // Wait for async completion
    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
            verify(notificationService).sendNotification(any());
        });
}
```

### Test Reporting

**Surefire Reports:**
```
target/
├── surefire-reports/
│   ├── TEST-com.wealthmanagement.portfolio.PortfolioServiceTest.xml
│   └── com.wealthmanagement.portfolio.PortfolioServiceTest.txt
```

**HTML Report Plugin:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-report-plugin</artifactId>
    <version>3.0.0</version>
</plugin>
```

```powershell
mvn surefire-report:report
# View: target/site/surefire-report.html
```
