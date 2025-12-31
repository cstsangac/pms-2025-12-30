# Kafka Event-Driven Architecture Guide

## Table of Contents
1. [Event-Driven Concepts](#event-driven-concepts)
2. [Kafka Setup](#kafka-setup)
3. [Event Design](#event-design)
4. [Producer Implementation](#producer-implementation)
5. [Consumer Implementation](#consumer-implementation)
6. [Topics and Partitioning](#topics-and-partitioning)
7. [Error Handling](#error-handling)
8. [Testing Events](#testing-events)

---

## Event-Driven Concepts

### What is Event-Driven Architecture (EDA)?

Event-Driven Architecture is a design pattern where services communicate by **producing and consuming events** rather than making direct API calls.

### Synchronous vs Asynchronous Communication

#### Synchronous (REST/HTTP)
```
Service A ──HTTP POST──> Service B
          <──Response───
  (waits for response)
```

**Characteristics:**
- Request-Response pattern
- Caller **waits** for response
- Tight coupling between services
- Immediate feedback
- Fails if Service B is down

#### Asynchronous (Events/Kafka)
```
Service A ──Event──> Kafka ──> Service B
  (continues immediately)        (processes when ready)
```

**Characteristics:**
- Fire-and-forget pattern
- Caller **doesn't wait** for response
- Loose coupling between services
- Eventual consistency
- Service B can be down temporarily (events buffered)

### Why Use Events in This Project?

#### Use Case: Portfolio Created

**Without Events (Direct API Calls):**
```java
// Portfolio Service
public Portfolio createPortfolio(CreateRequest request) {
    Portfolio portfolio = save(request);
    
    // Direct coupling - must call notification service
    notificationClient.sendNotification(
        "Portfolio created for " + portfolio.getClientName());
    
    return portfolio;
}
```

❌ **Problems:**
- Portfolio Service knows about Notification Service
- Notification Service must be available
- If notification fails, should portfolio creation fail?
- Harder to add new services that need to know about portfolios

**With Events (Kafka):**
```java
// Portfolio Service
public Portfolio createPortfolio(CreateRequest request) {
    Portfolio portfolio = save(request);
    
    // Publish event - don't care who listens
    publishEvent(PORTFOLIO_CREATED, portfolio);
    
    return portfolio;
}
```

✅ **Benefits:**
- Portfolio Service doesn't know about consumers
- Notification Service can be down - event is buffered
- Easy to add new consumers (analytics, audit, etc.)
- Services are decoupled

### Benefits of Event-Driven Architecture

1. **Loose Coupling**
   - Services don't need to know about each other
   - Add/remove consumers without changing producers

2. **Scalability**
   - Consumers can process events at their own pace
   - Multiple consumers can process same events
   - Partitioning for parallel processing

3. **Resilience**
   - Events are persisted in Kafka
   - Consumers can catch up after downtime
   - No cascading failures

4. **Flexibility**
   - Multiple services can react to same event
   - Easy to add new event consumers
   - Historical event replay

5. **Audit Trail**
   - All events are logged
   - Can reconstruct system state from events
   - Debugging and compliance

### Trade-offs

❌ **Challenges:**
- **Complexity:** More moving parts (Kafka, topics, consumers)
- **Eventual Consistency:** Data may not be immediately consistent across services
- **Debugging:** Harder to trace flow across services
- **Ordering:** Events may arrive out of order (mitigated with partitions)
- **Testing:** Need to test async behavior

✅ **When to Use Events:**
- Notification systems
- Audit logging
- Analytics and reporting
- Long-running processes
- Multiple services interested in same data change

✅ **When to Use Sync (REST):**
- Need immediate response
- Simple request-response workflows
- Critical path operations
- User-facing operations requiring instant feedback

## Kafka Setup

### Kafka Architecture Components

```
┌─────────────┐
│  Zookeeper  │  ← Manages cluster metadata
└─────────────┘
       ↓
┌─────────────┐
│    Kafka    │  ← Message broker
│   Broker    │
└─────────────┘
   ↓       ↓
Topics   Topics
```

### Docker Compose Configuration

**Location:** `docker-compose.yml`

#### Zookeeper
```yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.5.0
  container_name: zookeeper
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
  ports:
    - "2181:2181"
  networks:
    - pms-network
```

**What is Zookeeper?**
- Manages Kafka cluster metadata
- Tracks broker status
- Manages topic configurations
- Leader election for partitions
- **Required** for Kafka to run (though Kafka 3.0+ can run without it)

#### Kafka Broker
```yaml
kafka:
  image: confluentinc/cp-kafka:7.5.0
  container_name: kafka
  depends_on:
    - zookeeper
  ports:
    - "9092:9092"    # External access
    - "29092:29092"  # Internal Docker network
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    
    # Listener configuration
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
    
    # Performance settings
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
  networks:
    - pms-network
```

**Key Settings Explained:**

- `KAFKA_BROKER_ID: 1` - Unique broker identifier
- `KAFKA_ZOOKEEPER_CONNECT` - Connect to Zookeeper
- **Listeners:**
  - `PLAINTEXT://kafka:29092` - For Docker containers (service-to-service)
  - `PLAINTEXT_HOST://localhost:9092` - For host machine (testing)
- `REPLICATION_FACTOR: 1` - Single broker (dev environment)
- `AUTO_CREATE_TOPICS_ENABLE: true` - Auto-create topics when first used

### Verifying Kafka is Running

```powershell
# Check container status
docker-compose ps kafka

# Check Kafka logs
docker logs kafka --tail=50

# Connect to Kafka container
docker exec -it kafka bash

# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe a topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic portfolio-events
```

### Health Check

Kafka health check in docker-compose:
```yaml
healthcheck:
  test: kafka-topics --bootstrap-server localhost:9092 --list
  interval: 10s
  timeout: 5s
  retries: 5
```

## Event Design

### Event Structure Best Practices

Good events are:
1. **Self-contained** - All necessary information included
2. **Immutable** - Once published, never changed
3. **Timestamped** - Know when it happened
4. **Typed** - Clear event type/category
5. **Versioned** - Handle schema evolution (future)

### Portfolio Event

**Location:** `portfolio-service/src/main/java/.../event/PortfolioEvent.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioEvent {
    
    private EventType eventType;       // What happened
    private String portfolioId;        // Which portfolio
    private String clientId;           // Which client
    private String accountNumber;      // Account identifier
    private BigDecimal totalValue;     // Current total value
    private LocalDateTime timestamp;   // When it happened
    
    public enum EventType {
        PORTFOLIO_CREATED,
        PORTFOLIO_UPDATED,
        PORTFOLIO_DELETED,
        HOLDING_ADDED,
        HOLDING_UPDATED,
        HOLDING_REMOVED
    }
}
```

**Why This Structure?**

✅ **eventType** - Consumer can route based on event type  
✅ **portfolioId** - Key for Kafka partitioning  
✅ **clientId** - For client-specific notifications  
✅ **totalValue** - Avoid consumers querying portfolio service  
✅ **timestamp** - Audit trail and ordering  

### Transaction Event

**Location:** `transaction-service/src/main/java/.../event/TransactionEvent.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    
    private EventType eventType;
    private String transactionId;
    private String portfolioId;
    private String accountNumber;
    private String symbol;
    private TransactionType transactionType;  // BUY or SELL
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;
    
    public enum EventType {
        TRANSACTION_CREATED,
        TRANSACTION_PROCESSING,
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        TRANSACTION_CANCELLED
    }
    
    public enum TransactionType {
        BUY, SELL
    }
}
```

### Event Naming Conventions

**Format:** `<ENTITY>_<ACTION>` (past tense)

✅ Good:
- `PORTFOLIO_CREATED` (not `CREATE_PORTFOLIO`)
- `HOLDING_ADDED` (not `ADD_HOLDING`)
- `TRANSACTION_COMPLETED` (not `COMPLETE_TRANSACTION`)

**Why Past Tense?** Events describe **what happened**, not commands.

### Event Payload Guidelines

**Include:**
- Entity ID (for partitioning)
- Event type
- Timestamp
- Key business data (values, status)
- Correlation IDs (for tracing)

**Avoid:**
- Entire entity (can be large)
- Sensitive data (SSN, passwords)
- Data that changes frequently (use IDs, let consumers fetch if needed)

### Event Evolution

For future schema changes:

```java
// Version 1
public class PortfolioEvent {
    private EventType eventType;
    private String portfolioId;
}

// Version 2 (add field, maintain backwards compatibility)
public class PortfolioEvent {
    private EventType eventType;
    private String portfolioId;
    private String clientEmail;  // New optional field
}
```

**Rules:**
- Add fields (don't remove)
- Make new fields optional
- Use versioning if breaking changes needed

## Producer Implementation

### Spring Kafka Producer Setup

**Location:** `portfolio-service/src/main/java/.../service/PortfolioService.java`

#### 1. Inject KafkaTemplate

```java
@Service
@RequiredArgsConstructor
public class PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    private final KafkaTemplate<String, PortfolioEvent> kafkaTemplate;
    
    private static final String PORTFOLIO_EVENTS_TOPIC = "portfolio-events";
}
```

**KafkaTemplate<K, V>:**
- `K` = Key type (String - portfolioId)
- `V` = Value type (PortfolioEvent - the message)

#### 2. Publish Event Method

```java
private void publishEvent(PortfolioEvent.EventType eventType, Portfolio portfolio) {
    // 1. Build event object
    PortfolioEvent event = PortfolioEvent.builder()
        .eventType(eventType)
        .portfolioId(portfolio.getId())
        .clientId(portfolio.getClientId())
        .accountNumber(portfolio.getAccountNumber())
        .totalValue(portfolio.getTotalValue())
        .timestamp(LocalDateTime.now())
        .build();
    
    // 2. Send to Kafka
    kafkaTemplate.send(
        PORTFOLIO_EVENTS_TOPIC,  // Topic name
        portfolio.getId(),        // Key (for partitioning)
        event                     // Value (event payload)
    );
    
    log.debug("Published event: {} for portfolio: {}", 
        eventType, portfolio.getId());
}
```

**Key Components:**
- **Topic:** Where the event goes (`portfolio-events`)
- **Key:** Used for partitioning (same key → same partition → ordering)
- **Value:** The actual event data (automatically serialized to JSON)

#### 3. Call from Business Logic

```java
@Transactional
public PortfolioDTO.Response createPortfolio(PortfolioDTO.CreateRequest request) {
    // Business logic
    Portfolio portfolio = save(request);
    
    // Publish event (fire-and-forget)
    publishEvent(PortfolioEvent.EventType.PORTFOLIO_CREATED, portfolio);
    
    return toResponse(portfolio);
}
```

### Async vs Sync Publishing

#### Default: Async (Fire-and-Forget)
```java
kafkaTemplate.send(topic, key, event);  // Returns immediately
```

#### Sync (Wait for Confirmation)
```java
CompletableFuture<SendResult<String, PortfolioEvent>> future = 
    kafkaTemplate.send(topic, key, event);

try {
    SendResult<String, PortfolioEvent> result = future.get(5, TimeUnit.SECONDS);
    log.info("Event sent to partition: {}", result.getRecordMetadata().partition());
} catch (Exception e) {
    log.error("Failed to send event", e);
}
```

**When to Use Sync:**
- Critical events where you need confirmation
- Testing
- When failure should stop the workflow

**When to Use Async:**
- Normal notifications
- Audit logging
- Non-critical events
- Better performance

### Producer Configuration

**Location:** `application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # Wait for all replicas (reliability)
      retries: 3  # Retry failed sends
```

**Serializers:**
- `StringSerializer` - Converts key to bytes
- `JsonSerializer` - Converts event object to JSON

**acks (Acknowledgment):**
- `0` - Don't wait (fire-and-forget, fastest, risky)
- `1` - Wait for leader (balanced)
- `all` - Wait for all replicas (slowest, safest)

### Topic Configuration

**Location:** `config/KafkaConfig.java`

```java
@Configuration
public class KafkaConfig {
    
    @Value("${app.kafka.topics.portfolio-events}")
    private String portfolioEventsTopic;
    
    @Bean
    public NewTopic portfolioEventsTopic() {
        return TopicBuilder
            .name(portfolioEventsTopic)
            .partitions(3)       // 3 partitions for parallelism
            .replicas(1)         // Single broker (dev)
            .build();
    }
}
```

**Topic Creation:**
- Auto-created when first message is sent (if `auto.create.topics.enable=true`)
- Or explicitly created with `NewTopic` bean
- Prefer explicit creation for production

### Producer Best Practices

✅ **Use Meaningful Keys:** Portfolio ID, Client ID (enables ordering and partitioning)  
✅ **Include Timestamp:** For debugging and ordering  
✅ **Log Events:** Help with debugging  
✅ **Handle Failures:** Retry logic or dead letter queue  
✅ **Keep Events Small:** Don't include entire entities  
✅ **Use Schema:** Define event structure clearly  

## Consumer Implementation

### Spring Kafka Consumer Setup

**Location:** `notification-service/src/main/java/.../listener/EventListener.java`

#### Complete Consumer Example

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListener {
    
    private final NotificationService notificationService;
    
    @KafkaListener(
        topics = "${app.kafka.topics.portfolio-events}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handlePortfolioEvent(
            @Payload PortfolioEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        log.info("Received portfolio event: {} from topic: {}, partition: {}, offset: {}, key: {}",
            event.getEventType(), topic, partition, offset, key);
        
        try {
            // Process the event
            notificationService.processPortfolioEvent(event);
            log.info("Successfully processed portfolio event: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Error processing portfolio event: {}", event.getEventType(), e);
            // In production: dead letter queue or retry
        }
    }
}
```

### Key Annotations Explained

#### @KafkaListener
```java
@KafkaListener(
    topics = "portfolio-events",           // Topic to subscribe to
    groupId = "notification-service-group" // Consumer group ID
)
```

**What it does:**
- Automatically subscribes to topic(s)
- Deserializes messages
- Calls method for each message
- Manages offset commits

#### @Payload
```java
public void handle(@Payload PortfolioEvent event) {
    // event is automatically deserialized from JSON
}
```

**Deserialization:**
- JSON string → PortfolioEvent object
- Automatic with `JsonDeserializer`

#### @Header - Access Kafka Metadata
```java
@Header(KafkaHeaders.RECEIVED_KEY) String key
@Header(KafkaHeaders.RECEIVED_PARTITION) int partition
@Header(KafkaHeaders.RECEIVED_TOPIC) String topic
@Header(KafkaHeaders.OFFSET) long offset
@Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp
```

**Useful for:**
- Logging
- Debugging
- Partitioning logic
- Idempotency checks

### Processing Events

**Location:** `notification-service/src/main/java/.../service/NotificationService.java`

```java
@Service
@Slf4j
public class NotificationService {
    
    public void processPortfolioEvent(PortfolioEvent event) {
        log.info("Processing portfolio event: {}", event.getEventType());
        
        String notification = buildPortfolioNotification(event);
        sendNotification(event.getClientId(), notification);
    }
    
    private String buildPortfolioNotification(PortfolioEvent event) {
        return switch (event.getEventType()) {
            case PORTFOLIO_CREATED ->
                String.format("Portfolio %s created for client %s",
                    event.getPortfolioId(), event.getClientId());
            
            case PORTFOLIO_UPDATED ->
                String.format("Portfolio %s updated. Total: $%s",
                    event.getPortfolioId(), event.getTotalValue());
            
            case HOLDING_ADDED ->
                String.format("New holding added to portfolio %s",
                    event.getPortfolioId());
            
            // ... other cases
        };
    }
    
    private void sendNotification(String recipient, String message) {
        // In production: SendGrid, Twilio, Firebase, etc.
        log.info("===== NOTIFICATION =====");
        log.info("To: {}", recipient);
        log.info("Message: {}", message);
        log.info("========================");
    }
}
```

### Consumer Configuration

**Location:** `application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service-group
      auto-offset-reset: earliest  # Start from beginning if no offset
      enable-auto-commit: true     # Auto-commit offsets
      auto-commit-interval: 1000   # Commit every 1 second
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"  # Trust all packages for JSON deserialization
```

**Key Settings:**

**group-id:**
- Identifies this consumer group
- Multiple instances = load balancing
- Different groups = all receive same message

**auto-offset-reset:**
- `earliest` - Read from beginning (if no saved offset)
- `latest` - Read only new messages
- `none` - Throw error if no offset

**enable-auto-commit:**
- `true` - Automatically commit offsets (simpler)
- `false` - Manual commit (more control)

### Consumer Groups

```
Topic: portfolio-events (3 partitions)

Consumer Group A: notification-service-group
  Consumer 1 ← Partition 0
  Consumer 2 ← Partition 1
  Consumer 3 ← Partition 2

Consumer Group B: analytics-service-group
  Consumer 1 ← Partition 0, 1, 2
```

**Same Group = Load Balancing:**
- Each partition consumed by only one consumer in the group
- Scale by adding more consumers (up to # of partitions)

**Different Groups = Broadcasting:**
- Each group receives all messages
- Notification and Analytics both process same events

### Error Handling

```java
@KafkaListener(topics = "portfolio-events", groupId = "notification-group")
public void handlePortfolioEvent(@Payload PortfolioEvent event) {
    try {
        processEvent(event);
    } catch (RetryableException e) {
        log.warn("Retryable error, message will be reprocessed", e);
        throw e;  // Kafka will retry
    } catch (Exception e) {
        log.error("Non-retryable error, sending to DLQ", e);
        sendToDeadLetterQueue(event, e);
        // Don't throw - message is considered processed
    }
}
```

**Strategies:**
1. **Retry** - Throw exception, Kafka redelivers
2. **Dead Letter Queue** - Send failed messages to separate topic
3. **Skip** - Log and continue (data loss)
4. **Circuit Breaker** - Stop consuming if too many failures

## Topics and Partitioning

### What is a Topic?

A **topic** is a category or feed name to which messages are published.

```
Topic: portfolio-events
  │
  ├─ Partition 0: [msg1, msg2, msg5, ...]
  ├─ Partition 1: [msg3, msg6, msg7, ...]
  └─ Partition 2: [msg4, msg8, msg9, ...]
```

### Topics in This Project

1. **portfolio-events**
   - Source: Portfolio Service
   - Consumers: Notification Service, (future: Analytics, Audit)
   - Events: PORTFOLIO_CREATED, HOLDING_ADDED, etc.

2. **transaction-events**
   - Source: Transaction Service
   - Consumers: Notification Service
   - Events: TRANSACTION_COMPLETED, etc.

### Partitioning

**Why Partition?**
- **Scalability:** Multiple consumers process in parallel
- **Ordering:** Messages with same key go to same partition (ordered)
- **Performance:** Distribute load across brokers

### How Partitioning Works

```java
// Producer sends with key
kafkaTemplate.send(
    "portfolio-events",
    portfolio.getId(),  // Key: "p123"
    event
);
```

**Kafka determines partition:**
```
partition = hash(key) % number_of_partitions

key="p123" → hash=12345 → 12345 % 3 = 0 → Partition 0
key="p456" → hash=67890 → 67890 % 3 = 0 → Partition 0  
key="p789" → hash=11111 → 11111 % 3 = 2 → Partition 2
```

**Same key → Same partition → Ordered!**

### Partition Strategy

**Use portfolio ID as key:**
```java
kafkaTemplate.send(topic, portfolio.getId(), event);
```

**Result:**
- All events for portfolio "p123" go to same partition
- Events for "p123" are processed in order
- Events for "p456" can be processed in parallel (different partition)

### Choosing Number of Partitions

**portfolio-events: 3 partitions**

**Factors:**
- **Throughput:** More partitions = more parallelism
- **Consumers:** Can scale up to # of partitions
- **Ordering:** Need ordering per portfolio (not globally)

**Example:**
```
10 portfolios ÷ 3 partitions = ~3-4 portfolios per partition

Partition 0: p1, p4, p7, p10
Partition 1: p2, p5, p8
Partition 2: p3, p6, p9
```

### Topic Configuration

```java
@Bean
public NewTopic portfolioEventsTopic() {
    return TopicBuilder
        .name("portfolio-events")
        .partitions(3)              // Number of partitions
        .replicas(1)                // Replication factor (1 = no replication)
        .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7 days
        .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")  // Compression
        .build();
}
```

**Settings:**
- **partitions:** How many partitions (parallelism)
- **replicas:** Copies for fault tolerance (1 = dev, 3 = prod)
- **retention:** How long to keep messages (default: 7 days)
- **compression:** Reduce storage/network (snappy, gzip, lz4)

### Viewing Topic Details

```powershell
# List all topics
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 \
    --describe --topic portfolio-events

# Output:
Topic: portfolio-events  PartitionCount: 3  ReplicationFactor: 1
  Partition: 0  Leader: 1  Replicas: 1  Isr: 1
  Partition: 1  Leader: 1  Replicas: 1  Isr: 1
  Partition: 2  Leader: 1  Replicas: 1  Isr: 1
```

### Message Ordering Guarantees

✅ **Within Partition:** Messages are ordered
❌ **Across Partitions:** No ordering guarantee

**Example:**
```
Partition 0: Event1 → Event2 → Event3  (ordered)
Partition 1: Event4 → Event5 → Event6  (ordered)

Global order: Could be Event1, Event4, Event2, Event5, Event3, Event6
```

**Solution:** Use same key for events that need ordering.

## Error Handling

### Retry Strategy

```java
@KafkaListener(topics = "portfolio-events", groupId = "notification-group")
public void handleEvent(@Payload PortfolioEvent event) {
    try {
        processEvent(event);
    } catch (Exception e) {
        log.error("Failed to process event: {}", event, e);
        throw e;  // Kafka will retry
    }
}
```

**Automatic Retry:**
- Consumer throws exception → message redelivered
- Configure max retries in application.yml

### Dead Letter Queue (DLQ)

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, PortfolioEvent> kafkaListenerContainerFactory() {
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(kafkaTemplate),
        new FixedBackOff(1000L, 3L)  // 3 retries with 1s delay
    ));
    return factory;
}
```

**Failed messages → portfolio-events.DLT topic**

### Idempotency

```java
private Set<String> processedEventIds = new HashSet<>();

public void handleEvent(@Payload PortfolioEvent event) {
    String eventId = event.getPortfolioId() + "-" + event.getTimestamp();
    
    if (processedEventIds.contains(eventId)) {
        log.warn("Duplicate event, skipping: {}", eventId);
        return;
    }
    
    processEvent(event);
    processedEventIds.add(eventId);
}
```

**Production:** Use Redis/Database for distributed deduplication.

## Testing Events

### Watch Events in Real-Time

```powershell
# Watch notification service logs
docker logs notification-service --tail=50 -f

# Create a portfolio (triggers event)
$portfolio = @{
    clientId = "CLIENT003"
    clientName = "Test User"
    accountNumber = "ACC-99999999"
    currency = "USD"
    cashBalance = 50000.00
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8081/api/portfolio/portfolios `
    -Method Post -Body $portfolio -ContentType "application/json"

# You'll see in logs:
# Received portfolio event: PORTFOLIO_CREATED from topic: portfolio-events...
# Processing portfolio event: PORTFOLIO_CREATED
# ===== NOTIFICATION =====
# To: CLIENT003
# Message: Portfolio ... created successfully for client CLIENT003
```

### Consume Events from Console

```powershell
# Connect to Kafka container
docker exec -it kafka bash

# Consume from beginning
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic portfolio-events \
    --from-beginning \
    --property print.key=true \
    --property print.timestamp=true

# Output:
CreateTime:1735560000000  p123  {"eventType":"PORTFOLIO_CREATED",...}
```

### Produce Test Event Manually

```powershell
# Connect to Kafka
docker exec -it kafka bash

# Send test message
echo '{"eventType":"PORTFOLIO_CREATED","portfolioId":"test123"}' | \
    kafka-console-producer --bootstrap-server localhost:9092 \
    --topic portfolio-events
```

### Integration Test

```java
@SpringBootTest
@EmbeddedKafka(topics = "portfolio-events")
class EventListenerTest {
    
    @Autowired
    private KafkaTemplate<String, PortfolioEvent> kafkaTemplate;
    
    @Test
    void shouldConsumePortfolioEvent() throws Exception {
        PortfolioEvent event = PortfolioEvent.builder()
            .eventType(EventType.PORTFOLIO_CREATED)
            .portfolioId("test123")
            .build();
        
        kafkaTemplate.send("portfolio-events", "test123", event);
        
        // Wait for consumer to process
        Thread.sleep(2000);
        
        // Verify notification was sent
        verify(notificationService).processPortfolioEvent(any());
    }
}
```

---

## Summary

### Key Concepts
1. **Events** - Messages describing what happened
2. **Producer** - Publishes events to Kafka topics
3. **Consumer** - Subscribes and processes events
4. **Topics** - Categories for events
5. **Partitions** - Enable parallelism and ordering
6. **Consumer Groups** - Load balancing or broadcasting

### Interview Talking Points

**Q: Why Kafka over REST?**  
A: Loose coupling, async processing, buffering, scalability, multiple consumers.

**Q: How do you ensure message ordering?**  
A: Use partition keys - same key → same partition → ordered.

**Q: What if a consumer is down?**  
A: Kafka stores messages. Consumer catches up when restarted.

**Q: How do you handle duplicate messages?**  
A: Idempotency - track processed events, deduplicate.

**Q: What happens if event processing fails?**  
A: Retry with backoff, then dead letter queue.

### Next Steps
- Study Redis caching (Guide 03)
- Understand how events enable loose coupling
- Practice explaining async vs sync trade-offs
