# Transaction Service Guide

## Table of Contents
1. [Service Overview](#service-overview)
2. [Transaction Model](#transaction-model)
3. [State Machine](#state-machine)
4. [Transaction Processing](#transaction-processing)
5. [Event Publishing](#event-publishing)
6. [Validation Rules](#validation-rules)
7. [Error Handling](#error-handling)

---

## Service Overview

### Purpose

The **Transaction Service** handles all financial transactions for portfolios in the wealth management system.

**Core Responsibilities:**
- Process buy/sell transactions
- Handle deposits and withdrawals
- Record dividend payments
- Calculate transaction costs (price × quantity + commission)
- Manage transaction lifecycle (PENDING → PROCESSING → COMPLETED/FAILED)
- Publish transaction events to Kafka

### Architecture Position

```
┌─────────────────┐
│   API Gateway   │
│   (Port 8080)   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│  Transaction Service    │
│     (Port 8082)         │
│                         │
│  - REST API             │
│  - Business Logic       │
│  - State Machine        │
└─────┬──────────┬────────┘
      │          │
      ▼          ▼
┌──────────┐  ┌────────┐
│ MongoDB  │  │ Kafka  │
│ (Txn DB) │  │ Events │
└──────────┘  └────────┘
```

### Integration Points

**1. Incoming Integrations (Consumers):**
- **API Gateway:** Routes client requests to transaction endpoints
- **Frontend:** Portfolio dashboard initiates buy/sell transactions

**2. Outgoing Integrations (Producers):**
- **MongoDB:** Persists transaction records
- **Kafka:** Publishes events for other services to consume
  - `TRANSACTION_CREATED`
  - `TRANSACTION_PROCESSING`
  - `TRANSACTION_COMPLETED`
  - `TRANSACTION_FAILED`

**3. Event Consumers (Other Services):**
- **Portfolio Service:** Updates holdings based on completed transactions
- **Notification Service:** Sends alerts for transaction status changes

### Key Features

**1. Transaction Types**
- **BUY:** Purchase securities
- **SELL:** Sell securities
- **DIVIDEND:** Dividend payments received
- **DEPOSIT:** Cash deposit into portfolio
- **WITHDRAWAL:** Cash withdrawal from portfolio

**2. Automatic Calculations**
```java
Amount = Quantity × Price
TotalAmount = Amount + Commission

Example:
BUY 100 AAPL @ $150
Amount = 100 × $150 = $15,000
Commission = $10
TotalAmount = $15,010
```

**3. State Management**
- Tracks transaction lifecycle through multiple states
- Ensures transactions can't be processed twice
- Handles failures gracefully

**4. Event-Driven Architecture**
- Publishes events at each state transition
- Enables asynchronous processing by other services
- Decouples transaction processing from portfolio updates

### Service Boundaries

**What Transaction Service DOES:**
✅ Create and validate transactions
✅ Manage transaction state
✅ Calculate amounts and fees
✅ Publish transaction events
✅ Store transaction history

**What Transaction Service DOESN'T DO:**
❌ Update portfolio holdings (Portfolio Service responsibility)
❌ Send notifications (Notification Service responsibility)
❌ Execute trades with external brokers (simulated)
❌ Manage user authentication (API Gateway responsibility)

### Technology Stack

**Dependencies:**
```xml
<!-- Spring Boot Web -->
spring-boot-starter-web

<!-- MongoDB -->
spring-boot-starter-data-mongodb

<!-- Kafka -->
spring-kafka

<!-- Validation -->
spring-boot-starter-validation

<!-- MapStruct for DTOs -->
mapstruct

<!-- Lombok -->
lombok
```

**Database:**
- MongoDB database: `transaction_db`
- Collection: `transactions`
- Index on: `portfolioId`, `status`, `transactionDate`

**Messaging:**
- Kafka topic: `transaction-events`
- Event types: CREATED, PROCESSING, COMPLETED, FAILED

### API Endpoints

```
POST   /api/transaction/transactions          Create new transaction
GET    /api/transaction/transactions          Get all transactions
GET    /api/transaction/transactions/{id}     Get transaction by ID
GET    /api/transaction/transactions/portfolio/{portfolioId}  Get by portfolio
POST   /api/transaction/transactions/{id}/process  Process transaction manually
```

### Real-World Flow Example

**User initiates BUY transaction:**

```
1. User clicks "Buy 100 AAPL @ $150" in UI
   ↓
2. Frontend → API Gateway → Transaction Service
   POST /api/transaction/transactions
   {
     "portfolioId": "PORT001",
     "type": "BUY",
     "symbol": "AAPL",
     "quantity": 100,
     "price": 150.00
   }
   ↓
3. Transaction Service:
   - Creates Transaction (status: PENDING)
   - Calculates amount ($15,000)
   - Saves to MongoDB
   - Publishes TRANSACTION_CREATED event
   - Auto-processes transaction
   ↓
4. Processing:
   - Updates status to PROCESSING
   - Publishes TRANSACTION_PROCESSING event
   - Simulates trade execution
   - Updates status to COMPLETED
   - Publishes TRANSACTION_COMPLETED event
   ↓
5. Portfolio Service (Kafka listener):
   - Receives TRANSACTION_COMPLETED event
   - Updates portfolio holdings (adds 100 AAPL)
   - Deducts cash ($15,010 including commission)
   ↓
6. Notification Service (Kafka listener):
   - Receives TRANSACTION_COMPLETED event
   - Sends email: "Your BUY order for 100 AAPL is complete"
   ↓
7. Response to client:
   {
     "id": "TXN001",
     "portfolioId": "PORT001",
     "type": "BUY",
     "symbol": "AAPL",
     "quantity": 100,
     "price": 150.00,
     "amount": 15000.00,
     "commission": 10.00,
     "totalAmount": 15010.00,
     "status": "COMPLETED",
     "transactionDate": "2025-12-30T10:00:00"
   }
```

### Why Separate Transaction Service?

**Benefits of Microservice Separation:**
1. **Single Responsibility:** Only handles transactions
2. **Independent Scaling:** Can scale transaction processing separately
3. **Clear Boundaries:** Transaction logic isolated from portfolio management
4. **Event Sourcing:** Transaction history is immutable audit trail
5. **Flexibility:** Can change transaction processing without affecting portfolios

## Transaction Model

### Domain Model

**Location:** `Transaction.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {

    @Id
    private String id;

    private String portfolioId;
    private String accountNumber;
    private TransactionType type;
    private String symbol;
    private String assetName;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal totalAmount;
    private String currency;
    private TransactionStatus status;
    private String notes;

    @CreatedDate
    private LocalDateTime transactionDate;

    private LocalDateTime processedDate;

    public enum TransactionType {
        BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL
    }

    public enum TransactionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    public void calculateTotalAmount() {
        if (amount != null && commission != null) {
            this.totalAmount = amount.add(commission);
        } else if (amount != null) {
            this.totalAmount = amount;
        }
    }
}
```

### Field Descriptions

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| **id** | String | Unique transaction ID (MongoDB ObjectId) | `"TXN001"` |
| **portfolioId** | String | Associated portfolio | `"PORT001"` |
| **accountNumber** | String | Account number | `"ACC001"` |
| **type** | Enum | Transaction type | `BUY`, `SELL` |
| **symbol** | String | Security ticker symbol | `"AAPL"` |
| **assetName** | String | Full asset name | `"Apple Inc."` |
| **quantity** | BigDecimal | Number of shares/units | `100` |
| **price** | BigDecimal | Price per unit | `150.00` |
| **amount** | BigDecimal | Total amount (quantity × price) | `15000.00` |
| **commission** | BigDecimal | Transaction fee | `10.00` |
| **totalAmount** | BigDecimal | Amount + commission | `15010.00` |
| **currency** | String | Currency code | `"USD"` |
| **status** | Enum | Current transaction state | `COMPLETED` |
| **notes** | String | Optional notes | `"Market order"` |
| **transactionDate** | LocalDateTime | When created | `2025-12-30T10:00:00` |
| **processedDate** | LocalDateTime | When completed | `2025-12-30T10:00:05` |

### Transaction Types

**1. BUY - Purchase Securities**
```java
Transaction buyTransaction = Transaction.builder()
    .portfolioId("PORT001")
    .type(TransactionType.BUY)
    .symbol("AAPL")
    .quantity(new BigDecimal("100"))
    .price(new BigDecimal("150.00"))
    .amount(new BigDecimal("15000.00"))
    .commission(new BigDecimal("10.00"))
    .totalAmount(new BigDecimal("15010.00"))
    .status(TransactionStatus.PENDING)
    .build();
```

**Effect on Portfolio:**
- Add 100 shares of AAPL to holdings
- Deduct $15,010 from cash balance

**2. SELL - Sell Securities**
```java
Transaction sellTransaction = Transaction.builder()
    .portfolioId("PORT001")
    .type(TransactionType.SELL)
    .symbol("GOOGL")
    .quantity(new BigDecimal("50"))
    .price(new BigDecimal("2800.00"))
    .amount(new BigDecimal("140000.00"))
    .commission(new BigDecimal("10.00"))
    .totalAmount(new BigDecimal("139990.00"))  // Less commission
    .status(TransactionStatus.PENDING)
    .build();
```

**Effect on Portfolio:**
- Remove 50 shares of GOOGL from holdings
- Add $139,990 to cash balance (amount - commission)

**3. DIVIDEND - Dividend Payment**
```java
Transaction dividendTransaction = Transaction.builder()
    .portfolioId("PORT001")
    .type(TransactionType.DIVIDEND)
    .symbol("MSFT")
    .quantity(new BigDecimal("200"))  // Shares held
    .price(new BigDecimal("0.68"))    // Dividend per share
    .amount(new BigDecimal("136.00")) // 200 × $0.68
    .commission(BigDecimal.ZERO)
    .totalAmount(new BigDecimal("136.00"))
    .status(TransactionStatus.COMPLETED)
    .build();
```

**Effect on Portfolio:**
- Add $136 to cash balance
- Holdings unchanged

**4. DEPOSIT - Add Cash**
```java
Transaction depositTransaction = Transaction.builder()
    .portfolioId("PORT001")
    .type(TransactionType.DEPOSIT)
    .amount(new BigDecimal("10000.00"))
    .commission(BigDecimal.ZERO)
    .totalAmount(new BigDecimal("10000.00"))
    .status(TransactionStatus.COMPLETED)
    .build();
```

**Effect on Portfolio:**
- Add $10,000 to cash balance

**5. WITHDRAWAL - Remove Cash**
```java
Transaction withdrawalTransaction = Transaction.builder()
    .portfolioId("PORT001")
    .type(TransactionType.WITHDRAWAL)
    .amount(new BigDecimal("5000.00"))
    .commission(BigDecimal.ZERO)
    .totalAmount(new BigDecimal("5000.00"))
    .status(TransactionStatus.COMPLETED)
    .build();
```

**Effect on Portfolio:**
- Deduct $5,000 from cash balance

### Transaction Status Enum

```java
public enum TransactionStatus {
    PENDING,      // Created, waiting to be processed
    PROCESSING,   // Currently being executed
    COMPLETED,    // Successfully executed
    FAILED,       // Execution failed
    CANCELLED     // Manually cancelled
}
```

**Status Meanings:**
- **PENDING:** Transaction created but not yet processed
- **PROCESSING:** Transaction is being executed (communicating with trading system)
- **COMPLETED:** Transaction successfully executed, portfolio updated
- **FAILED:** Transaction failed (insufficient funds, invalid symbol, etc.)
- **CANCELLED:** User or system cancelled the transaction

### Amount Calculations

**Method: calculateTotalAmount()**

```java
public void calculateTotalAmount() {
    if (amount != null && commission != null) {
        this.totalAmount = amount.add(commission);
    } else if (amount != null) {
        this.totalAmount = amount;
    }
}
```

**For BUY transactions:**
```
Amount = Quantity × Price
TotalAmount = Amount + Commission

Example:
BUY 100 AAPL @ $150
Amount = 100 × $150 = $15,000
Commission = $10
TotalAmount = $15,000 + $10 = $15,010
```

**For SELL transactions:**
```
Amount = Quantity × Price
TotalAmount = Amount + Commission (commission is subtracted when updating portfolio)

Example:
SELL 50 GOOGL @ $2,800
Amount = 50 × $2,800 = $140,000
Commission = $10
TotalAmount = $140,000 + $10 = $140,010 (for tracking)
Net proceeds to portfolio = $140,000 - $10 = $139,990
```

### MongoDB Document Structure

**Stored Document:**
```json
{
  "_id": "67890abcdef12345",
  "portfolioId": "PORT001",
  "accountNumber": "ACC001",
  "type": "BUY",
  "symbol": "AAPL",
  "assetName": "Apple Inc.",
  "quantity": 100,
  "price": 150.00,
  "amount": 15000.00,
  "commission": 10.00,
  "totalAmount": 15010.00,
  "currency": "USD",
  "status": "COMPLETED",
  "notes": "Market order",
  "transactionDate": ISODate("2025-12-30T10:00:00Z"),
  "processedDate": ISODate("2025-12-30T10:00:05Z")
}
```

### Validation Constraints

**Applied via DTOs and service layer:**

```java
@NotNull(message = "Portfolio ID is required")
private String portfolioId;

@NotNull(message = "Transaction type is required")
private TransactionType type;

@NotNull(message = "Symbol is required for BUY/SELL transactions")
private String symbol;  // Only for BUY/SELL

@Positive(message = "Quantity must be positive")
private BigDecimal quantity;

@Positive(message = "Price must be positive")
private BigDecimal price;

@PositiveOrZero(message = "Commission cannot be negative")
private BigDecimal commission;
```

### Immutability Considerations

**Transaction records should be immutable** (audit trail):
- Once created, core fields (portfolioId, type, symbol, quantity, price) should not change
- Only status can be updated (PENDING → PROCESSING → COMPLETED/FAILED)
- Never delete transactions (soft delete with CANCELLED status if needed)
- Corrections made via new offsetting transactions

**Why?**
- Regulatory compliance (audit trail)
- Financial reconciliation
- Historical accuracy

## State Machine

### Transaction Lifecycle

```
    ┌─────────┐
    │ PENDING │  ← Transaction created
    └────┬────┘
         │ processTransaction()
         ▼
  ┌─────────────┐
  │ PROCESSING  │  ← Executing transaction
  └──────┬──────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌──────────┐ ┌────────┐
│COMPLETED │ │ FAILED │
└──────────┘ └────────┘
     │           │
     └─────┬─────┘
           ▼
    (Terminal States)
```

### State Descriptions

**1. PENDING (Initial State)**
- Transaction just created
- Validation passed
- Waiting to be processed
- Can transition to: PROCESSING, CANCELLED

**2. PROCESSING (Active State)**
- Transaction is being executed
- Communicating with trading system (simulated)
- Cannot be cancelled at this point
- Can transition to: COMPLETED, FAILED

**3. COMPLETED (Success Terminal State)**
- Transaction successfully executed
- Portfolio updated
- Events published
- **Cannot transition to any other state** (final)

**4. FAILED (Error Terminal State)**
- Transaction execution failed
- Portfolio NOT updated
- Error logged and event published
- **Cannot transition to any other state** (final)

**5. CANCELLED (Manual Terminal State)**
- User or system cancelled transaction
- Only possible from PENDING state
- Portfolio NOT updated
- **Cannot transition to any other state** (final)

### State Transitions

**Valid Transitions:**
```java
PENDING → PROCESSING    ✅ Start processing
PENDING → CANCELLED     ✅ Cancel before processing
PROCESSING → COMPLETED  ✅ Success
PROCESSING → FAILED     ✅ Error occurred
```

**Invalid Transitions:**
```java
COMPLETED → PROCESSING  ❌ Cannot reprocess completed transaction
FAILED → PROCESSING     ❌ Cannot retry failed transaction
CANCELLED → PROCESSING  ❌ Cannot process cancelled transaction
PROCESSING → PENDING    ❌ Cannot go back to pending
```

### State Transition Code

**Location:** `TransactionService.java`

```java
@Transactional
public TransactionDTO.Response processTransaction(String transactionId) {
    log.info("Processing transaction: {}", transactionId);

    Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));

    // Guard: Only process PENDING transactions
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        log.warn("Transaction {} is not in PENDING status", transactionId);
        return transactionMapper.toResponse(transaction);
    }

    // Transition: PENDING → PROCESSING
    transaction.setStatus(TransactionStatus.PROCESSING);
    transactionRepository.save(transaction);
    publishEvent(TransactionEvent.EventType.TRANSACTION_PROCESSING, transaction);

    try {
        // Execute transaction (simulated)
        Thread.sleep(100);

        // Transition: PROCESSING → COMPLETED
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedDate(LocalDateTime.now());
        Transaction completed = transactionRepository.save(transaction);
        
        log.info("Transaction processed successfully: {}", transactionId);
        publishEvent(TransactionEvent.EventType.TRANSACTION_COMPLETED, completed);
        
        return transactionMapper.toResponse(completed);

    } catch (Exception e) {
        // Transition: PROCESSING → FAILED
        log.error("Failed to process transaction: {}", transactionId, e);
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        
        publishEvent(TransactionEvent.EventType.TRANSACTION_FAILED, transaction);
        throw new RuntimeException("Transaction processing failed", e);
    }
}
```

### State Guard Pattern

**Prevents invalid state transitions:**

```java
public void updateStatus(TransactionStatus newStatus) {
    // Terminal states cannot be changed
    if (this.status == TransactionStatus.COMPLETED || 
        this.status == TransactionStatus.FAILED || 
        this.status == TransactionStatus.CANCELLED) {
        throw new IllegalStateException(
            "Cannot change status of transaction in terminal state: " + this.status);
    }

    // Only PENDING can be cancelled
    if (newStatus == TransactionStatus.CANCELLED && 
        this.status != TransactionStatus.PENDING) {
        throw new IllegalStateException(
            "Can only cancel PENDING transactions. Current status: " + this.status);
    }

    // Only PENDING can go to PROCESSING
    if (newStatus == TransactionStatus.PROCESSING && 
        this.status != TransactionStatus.PENDING) {
        throw new IllegalStateException(
            "Can only process PENDING transactions. Current status: " + this.status);
    }

    this.status = newStatus;
}
```

### Events Published at Each State

**State transition triggers events:**

| Transition | Event Type | Consumer Action |
|------------|-----------|-----------------|
| Created → PENDING | `TRANSACTION_CREATED` | Log creation, track metrics |
| PENDING → PROCESSING | `TRANSACTION_PROCESSING` | Update UI (show "Processing...") |
| PROCESSING → COMPLETED | `TRANSACTION_COMPLETED` | Update portfolio, send notification |
| PROCESSING → FAILED | `TRANSACTION_FAILED` | Alert user, log error |

### State Transition Timeline

**Example: Successful BUY Transaction**

```
T+0ms:  Create transaction
        Status: PENDING
        Event: TRANSACTION_CREATED
        ↓
T+10ms: Start processing
        Status: PROCESSING
        Event: TRANSACTION_PROCESSING
        ↓
T+110ms: Processing complete
        Status: COMPLETED
        ProcessedDate: 2025-12-30T10:00:00.110Z
        Event: TRANSACTION_COMPLETED
        ↓
T+120ms: Portfolio Service receives event
        Updates holdings (adds 100 AAPL)
        Deducts cash ($15,010)
        ↓
T+130ms: Notification Service receives event
        Sends email: "Your BUY order is complete"
```

**Example: Failed Transaction**

```
T+0ms:  Create transaction (insufficient funds)
        Status: PENDING
        Event: TRANSACTION_CREATED
        ↓
T+10ms: Start processing
        Status: PROCESSING
        Event: TRANSACTION_PROCESSING
        ↓
T+50ms: Validation fails (insufficient cash)
        Status: FAILED
        Event: TRANSACTION_FAILED
        ↓
T+60ms: Notification Service receives event
        Sends email: "Your BUY order failed: Insufficient funds"
```

### Idempotency

**Prevent double processing:**

```java
@Transactional
public TransactionDTO.Response processTransaction(String transactionId) {
    Transaction transaction = getTransaction(transactionId);
    
    // Already processed - return current state (idempotent)
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        log.info("Transaction {} already processed. Status: {}", 
            transactionId, transaction.getStatus());
        return transactionMapper.toResponse(transaction);
    }
    
    // Continue processing...
}
```

**Why?**
- Client may retry if timeout occurs
- Kafka may deliver event multiple times
- Ensures same result regardless of retry count

### Compensation Transactions

**For failed transactions, create compensating transaction:**

```java
public TransactionDTO.Response compensateFailedTransaction(String failedTransactionId) {
    Transaction failed = getTransaction(failedTransactionId);
    
    if (failed.getStatus() != TransactionStatus.FAILED) {
        throw new IllegalStateException("Can only compensate FAILED transactions");
    }
    
    // Create opposite transaction
    TransactionDTO.CreateRequest compensation = TransactionDTO.CreateRequest.builder()
        .portfolioId(failed.getPortfolioId())
        .type(failed.getType() == TransactionType.BUY ? 
              TransactionType.SELL : TransactionType.BUY)
        .symbol(failed.getSymbol())
        .quantity(failed.getQuantity())
        .price(failed.getPrice())
        .notes("Compensation for failed transaction: " + failedTransactionId)
        .build();
    
    return createTransaction(compensation);
}
```

### State Persistence

**MongoDB stores current state:**

```json
{
  "_id": "TXN001",
  "status": "PROCESSING",  ← Current state
  "transactionDate": ISODate("2025-12-30T10:00:00Z"),
  "processedDate": null  ← Set when COMPLETED
}
```

**State history tracking (optional enhancement):**

```java
@Document(collection = "transactions")
public class Transaction {
    // ... existing fields ...
    
    @Builder.Default
    private List<StateTransition> stateHistory = new ArrayList<>();
    
    public void transitionTo(TransactionStatus newStatus) {
        StateTransition transition = StateTransition.builder()
            .fromStatus(this.status)
            .toStatus(newStatus)
            .timestamp(LocalDateTime.now())
            .build();
        
        this.stateHistory.add(transition);
        this.status = newStatus;
    }
}

@Data
@Builder
class StateTransition {
    private TransactionStatus fromStatus;
    private TransactionStatus toStatus;
    private LocalDateTime timestamp;
}
```

**Stored with history:**
```json
{
  "_id": "TXN001",
  "status": "COMPLETED",
  "stateHistory": [
    {
      "fromStatus": null,
      "toStatus": "PENDING",
      "timestamp": ISODate("2025-12-30T10:00:00.000Z")
    },
    {
      "fromStatus": "PENDING",
      "toStatus": "PROCESSING",
      "timestamp": ISODate("2025-12-30T10:00:00.010Z")
    },
    {
      "fromStatus": "PROCESSING",
      "toStatus": "COMPLETED",
      "timestamp": ISODate("2025-12-30T10:00:00.110Z")
    }
  ]
}
```

### Best Practices

✅ **DO:**
- Use guard clauses to prevent invalid transitions
- Log every state change
- Publish events at each transition
- Make transitions atomic (@Transactional)
- Handle failures gracefully

❌ **DON'T:**
- Allow transitions from terminal states
- Skip intermediate states (PENDING → COMPLETED directly)
- Process same transaction twice
- Forget to publish events
- Modify completed transactions

## Transaction Processing

### Service Layer Overview

**Location:** `TransactionService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    private static final String TRANSACTION_EVENTS_TOPIC = "transaction-events";
}
```

**Dependencies:**
- **TransactionRepository:** MongoDB CRUD operations
- **TransactionMapper:** DTO ↔ Entity conversion
- **KafkaTemplate:** Event publishing

### Create Transaction

```java
@Transactional
public TransactionDTO.Response createTransaction(TransactionDTO.CreateRequest request) {
    log.info("Creating transaction for portfolio: {}", request.getPortfolioId());

    // 1. Map DTO to entity
    Transaction transaction = transactionMapper.toEntity(request);

    // 2. Calculate amounts
    BigDecimal amount = request.getQuantity().multiply(request.getPrice());
    transaction.setAmount(amount);

    if (request.getCommission() != null) {
        transaction.setCommission(request.getCommission());
    } else {
        transaction.setCommission(BigDecimal.ZERO);
    }

    transaction.calculateTotalAmount();  // amount + commission
    
    // 3. Set initial status
    transaction.setStatus(TransactionStatus.PENDING);

    // 4. Save to database
    Transaction saved = transactionRepository.save(transaction);
    log.info("Transaction created with ID: {}", saved.getId());

    // 5. Publish creation event
    publishEvent(TransactionEvent.EventType.TRANSACTION_CREATED, saved);

    // 6. Automatically process the transaction
    return processTransaction(saved.getId());
}
```

**Request Example:**
```json
POST /api/transaction/transactions
{
  "portfolioId": "PORT001",
  "accountNumber": "ACC001",
  "type": "BUY",
  "symbol": "AAPL",
  "assetName": "Apple Inc.",
  "quantity": 100,
  "price": 150.00,
  "commission": 10.00,
  "currency": "USD"
}
```

**Response:**
```json
{
  "id": "TXN001",
  "portfolioId": "PORT001",
  "type": "BUY",
  "symbol": "AAPL",
  "quantity": 100,
  "price": 150.00,
  "amount": 15000.00,
  "commission": 10.00,
  "totalAmount": 15010.00,
  "status": "COMPLETED",
  "transactionDate": "2025-12-30T10:00:00",
  "processedDate": "2025-12-30T10:00:00.110"
}
```

### Process Transaction

**Core processing logic:**

```java
@Transactional
public TransactionDTO.Response processTransaction(String transactionId) {
    log.info("Processing transaction: {}", transactionId);

    // 1. Fetch transaction
    Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));

    // 2. Guard: Only process PENDING transactions (idempotency)
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        log.warn("Transaction {} is not in PENDING status", transactionId);
        return transactionMapper.toResponse(transaction);  // Return current state
    }

    // 3. Update status to PROCESSING
    transaction.setStatus(TransactionStatus.PROCESSING);
    transactionRepository.save(transaction);
    publishEvent(TransactionEvent.EventType.TRANSACTION_PROCESSING, transaction);

    // 4. Execute transaction
    try {
        // Simulate trade execution (in real system: call broker API)
        Thread.sleep(100);

        // 5. Success: Update to COMPLETED
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedDate(LocalDateTime.now());
        Transaction completed = transactionRepository.save(transaction);
        
        log.info("Transaction processed successfully: {}", transactionId);
        publishEvent(TransactionEvent.EventType.TRANSACTION_COMPLETED, completed);
        
        return transactionMapper.toResponse(completed);

    } catch (Exception e) {
        // 6. Failure: Update to FAILED
        log.error("Failed to process transaction: {}", transactionId, e);
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        
        publishEvent(TransactionEvent.EventType.TRANSACTION_FAILED, transaction);
        throw new RuntimeException("Transaction processing failed", e);
    }
}
```

**Processing Flow:**
```
1. Validate transaction exists
2. Check status is PENDING (prevent double-processing)
3. Update status → PROCESSING
4. Execute trade (simulated with Thread.sleep)
5a. Success → status = COMPLETED, publish event
5b. Failure → status = FAILED, publish event
6. Return result
```

### Query Operations

**Get Single Transaction:**
```java
public TransactionDTO.Response getTransactionById(String id) {
    log.debug("Fetching transaction: {}", id);
    Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    return transactionMapper.toResponse(transaction);
}
```

**Get All Transactions for Portfolio:**
```java
public List<TransactionDTO.Response> getTransactionsByPortfolioId(String portfolioId) {
    log.debug("Fetching transactions for portfolio: {}", portfolioId);
    List<Transaction> transactions = transactionRepository.findByPortfolioId(portfolioId);
    return transactionMapper.toResponseList(transactions);
}
```

**Get All Transactions for Account:**
```java
public List<TransactionDTO.Response> getTransactionsByAccountNumber(String accountNumber) {
    log.debug("Fetching transactions for account: {}", accountNumber);
    List<Transaction> transactions = transactionRepository.findByAccountNumber(accountNumber);
    return transactionMapper.toResponseList(transactions);
}
```

**Get All Transactions:**
```java
public List<TransactionDTO.Response> getAllTransactions() {
    log.debug("Fetching all transactions");
    List<Transaction> transactions = transactionRepository.findAll();
    return transactionMapper.toResponseList(transactions);
}
```

### Cancel Transaction

```java
@Transactional
public TransactionDTO.Response cancelTransaction(String transactionId) {
    log.info("Cancelling transaction: {}", transactionId);

    Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));

    // Guard: Cannot cancel completed transactions
    if (transaction.getStatus() == TransactionStatus.COMPLETED) {
        throw new IllegalStateException("Cannot cancel completed transaction");
    }

    // Guard: Can only cancel PENDING transactions
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        throw new IllegalStateException("Can only cancel PENDING transactions");
    }

    transaction.setStatus(TransactionStatus.CANCELLED);
    Transaction cancelled = transactionRepository.save(transaction);

    publishEvent(TransactionEvent.EventType.TRANSACTION_CANCELLED, cancelled);

    return transactionMapper.toResponse(cancelled);
}
```

### Amount Calculation Logic

**For BUY transactions:**
```java
// Input from client
BigDecimal quantity = new BigDecimal("100");
BigDecimal price = new BigDecimal("150.00");
BigDecimal commission = new BigDecimal("10.00");

// Calculations
BigDecimal amount = quantity.multiply(price);  // 100 × 150 = 15,000
BigDecimal totalAmount = amount.add(commission);  // 15,000 + 10 = 15,010

// What portfolio pays
Portfolio cash balance -= totalAmount;  // Deduct $15,010
```

**For SELL transactions:**
```java
// Input from client
BigDecimal quantity = new BigDecimal("50");
BigDecimal price = new BigDecimal("2800.00");
BigDecimal commission = new BigDecimal("10.00");

// Calculations
BigDecimal amount = quantity.multiply(price);  // 50 × 2,800 = 140,000
BigDecimal totalAmount = amount.add(commission);  // 140,000 + 10 = 140,010

// What portfolio receives
Portfolio cash balance += amount.subtract(commission);  // Add $139,990
```

### Real-World Integration (Production)

**In production, replace simulation with actual broker API:**

```java
@Transactional
public TransactionDTO.Response processTransaction(String transactionId) {
    Transaction transaction = getTransaction(transactionId);
    
    // Update to PROCESSING
    transaction.setStatus(TransactionStatus.PROCESSING);
    transactionRepository.save(transaction);
    publishEvent(TransactionEvent.EventType.TRANSACTION_PROCESSING, transaction);

    try {
        // REAL IMPLEMENTATION:
        // Call broker/trading system API
        BrokerResponse response = brokerClient.executeTrade(
            transaction.getSymbol(),
            transaction.getQuantity(),
            transaction.getType() == TransactionType.BUY ? "BUY" : "SELL"
        );
        
        // Update with actual execution price
        transaction.setPrice(response.getExecutionPrice());
        transaction.setAmount(response.getTotalAmount());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedDate(LocalDateTime.now());
        
        Transaction completed = transactionRepository.save(transaction);
        publishEvent(TransactionEvent.EventType.TRANSACTION_COMPLETED, completed);
        
        return transactionMapper.toResponse(completed);
        
    } catch (BrokerException e) {
        // Trade rejected or failed
        log.error("Broker rejected transaction: {}", transactionId, e);
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setNotes("Broker error: " + e.getMessage());
        transactionRepository.save(transaction);
        
        publishEvent(TransactionEvent.EventType.TRANSACTION_FAILED, transaction);
        throw new RuntimeException("Transaction processing failed", e);
    }
}
```

### Transaction Atomicity

**@Transactional ensures:**
- All database operations succeed or rollback together
- No partial updates (status changed but event not published)
- Consistent state even if crash occurs

**Example - What @Transactional prevents:**

**WITHOUT @Transactional:**
```java
transaction.setStatus(PROCESSING);
transactionRepository.save(transaction);  // ✅ Saved
// CRASH HERE - status updated but event never published ❌
publishEvent(TRANSACTION_PROCESSING, transaction);
```

**WITH @Transactional:**
```java
@Transactional
public void processTransaction() {
    transaction.setStatus(PROCESSING);
    transactionRepository.save(transaction);
    // CRASH HERE - entire transaction rolled back ✅
    publishEvent(TRANSACTION_PROCESSING, transaction);
}
```

### Error Scenarios

**1. Transaction Not Found:**
```java
TransactionNotFoundException: "Transaction not found: TXN999"
→ HTTP 404 Not Found
```

**2. Double Processing:**
```java
GET /api/transaction/transactions/TXN001/process
// First call: Success
// Second call: No-op (already COMPLETED)
→ Returns current state (idempotent)
```

**3. Cancel Completed Transaction:**
```java
POST /api/transaction/transactions/TXN001/cancel
// Transaction is COMPLETED
IllegalStateException: "Cannot cancel completed transaction"
→ HTTP 400 Bad Request
```

**4. Processing Failure:**
```java
// Broker API timeout
RuntimeException: "Transaction processing failed"
→ Status = FAILED
→ Event published: TRANSACTION_FAILED
→ HTTP 500 Internal Server Error
```

### Performance Considerations

**Asynchronous Processing (Future Enhancement):**

```java
@Async
@Transactional
public CompletableFuture<TransactionDTO.Response> processTransactionAsync(String id) {
    // Process transaction asynchronously
    return CompletableFuture.completedFuture(processTransaction(id));
}
```

**Batch Processing:**
```java
public List<TransactionDTO.Response> processPendingTransactions() {
    List<Transaction> pending = transactionRepository
        .findByStatus(TransactionStatus.PENDING);
    
    return pending.stream()
        .map(t -> processTransaction(t.getId()))
        .collect(Collectors.toList());
}
```

## Event Publishing

### Overview

Transaction Service publishes events to Kafka at every state transition, enabling **event-driven architecture**.

**Why Publish Events?**
- **Decoupling:** Portfolio Service doesn't call Transaction Service directly
- **Asynchronous Processing:** Portfolio updates happen independently
- **Scalability:** Multiple consumers can process events
- **Audit Trail:** Events provide complete transaction history
- **Reliability:** Kafka guarantees message delivery

**Event Flow:**
```
Transaction Service               Kafka Topic              Consumers
      │                         transaction-events            │
      ├──[TRANSACTION_CREATED]──────►│◄──────┐               │
      ├──[TRANSACTION_PROCESSING]────►│       │               │
      ├──[TRANSACTION_COMPLETED]─────►│       ├──► Portfolio Service
      ├──[TRANSACTION_FAILED]────────►│       ├──► Notification Service
      └──[TRANSACTION_CANCELLED]─────►│       └──► Analytics Service
```

### TransactionEvent Model

**Location:** `TransactionEvent.java`

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
    private Transaction.TransactionType transactionType;
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private Transaction.TransactionStatus status;
    private LocalDateTime timestamp;

    public enum EventType {
        TRANSACTION_CREATED,
        TRANSACTION_PROCESSING,
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        TRANSACTION_CANCELLED
    }
}
```

**Field Descriptions:**

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| **eventType** | Enum | Type of event | `TRANSACTION_COMPLETED` |
| **transactionId** | String | Transaction ID | `"TXN001"` |
| **portfolioId** | String | Associated portfolio | `"PORT001"` |
| **accountNumber** | String | Account number | `"ACC001"` |
| **transactionType** | Enum | BUY, SELL, etc. | `BUY` |
| **symbol** | String | Security symbol | `"AAPL"` |
| **quantity** | BigDecimal | Number of shares | `100` |
| **price** | BigDecimal | Price per share | `150.00` |
| **totalAmount** | BigDecimal | Total transaction amount | `15010.00` |
| **status** | Enum | Current status | `COMPLETED` |
| **timestamp** | LocalDateTime | When event occurred | `2025-12-30T10:00:00` |

### Publishing Implementation

**KafkaTemplate Configuration:**

```java
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final TransactionRepository transactionRepository;
    
    @Value("${kafka.topic.transaction-events}")
    private String transactionEventsTopic;
    
    // Service methods...
}
```

**Publishing Events:**

```java
private void publishEvent(Transaction transaction, TransactionEvent.EventType eventType) {
    TransactionEvent event = TransactionEvent.builder()
        .eventType(eventType)
        .transactionId(transaction.getId())
        .portfolioId(transaction.getPortfolioId())
        .accountNumber(transaction.getAccountNumber())
        .transactionType(transaction.getType())
        .symbol(transaction.getSymbol())
        .quantity(transaction.getQuantity())
        .price(transaction.getPrice())
        .totalAmount(transaction.getTotalAmount())
        .status(transaction.getStatus())
        .timestamp(LocalDateTime.now())
        .build();
    
    kafkaTemplate.send(transactionEventsTopic, transaction.getId(), event);
    log.info("Published {} event for transaction {}", eventType, transaction.getId());
}
```

**Event Publishing at Different Stages:**

```java
// 1. On transaction creation
public Transaction createTransaction(TransactionRequest request) {
    // Validation...
    Transaction transaction = buildTransaction(request);
    Transaction saved = transactionRepository.save(transaction);
    
    publishEvent(saved, TransactionEvent.EventType.TRANSACTION_CREATED);
    return saved;
}

// 2. When processing starts
public void processTransaction(String transactionId) {
    Transaction transaction = getTransactionById(transactionId);
    transaction.setStatus(Transaction.TransactionStatus.PROCESSING);
    transactionRepository.save(transaction);
    
    publishEvent(transaction, TransactionEvent.EventType.TRANSACTION_PROCESSING);
    // Processing logic...
}

// 3. On successful completion
private void completeTransaction(Transaction transaction) {
    transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
    transaction.setCompletedAt(LocalDateTime.now());
    transactionRepository.save(transaction);
    
    publishEvent(transaction, TransactionEvent.EventType.TRANSACTION_COMPLETED);
}

// 4. On failure
private void failTransaction(Transaction transaction, String errorMessage) {
    transaction.setStatus(Transaction.TransactionStatus.FAILED);
    transaction.setErrorMessage(errorMessage);
    transactionRepository.save(transaction);
    
    publishEvent(transaction, TransactionEvent.EventType.TRANSACTION_FAILED);
}

// 5. On cancellation
public void cancelTransaction(String transactionId) {
    Transaction transaction = getTransactionById(transactionId);
    transaction.setStatus(Transaction.TransactionStatus.CANCELLED);
    transactionRepository.save(transaction);
    
    publishEvent(transaction, TransactionEvent.EventType.TRANSACTION_CANCELLED);
}
```

### Event Consumers

**1. Portfolio Service Consumer:**

```java
@Component
@Slf4j
public class TransactionEventListener {

    @Autowired
    private PortfolioService portfolioService;

    @KafkaListener(
        topics = "${kafka.topic.transaction-events}",
        groupId = "portfolio-service-group"
    )
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("Received transaction event: {}", event.getEventType());
        
        switch (event.getEventType()) {
            case TRANSACTION_COMPLETED:
                updatePortfolioHoldings(event);
                break;
            case TRANSACTION_FAILED:
                log.warn("Transaction {} failed: processing rollback if needed", 
                    event.getTransactionId());
                break;
            case TRANSACTION_CANCELLED:
                log.info("Transaction {} cancelled", event.getTransactionId());
                break;
            default:
                log.debug("Ignoring event type: {}", event.getEventType());
        }
    }
    
    private void updatePortfolioHoldings(TransactionEvent event) {
        try {
            if (event.getTransactionType() == Transaction.TransactionType.BUY) {
                portfolioService.addHolding(
                    event.getPortfolioId(),
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getPrice()
                );
            } else if (event.getTransactionType() == Transaction.TransactionType.SELL) {
                portfolioService.reduceHolding(
                    event.getPortfolioId(),
                    event.getSymbol(),
                    event.getQuantity()
                );
            }
            log.info("Portfolio {} updated for transaction {}", 
                event.getPortfolioId(), event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to update portfolio for transaction {}: {}", 
                event.getTransactionId(), e.getMessage());
            // Could publish compensating event or dead-letter queue
        }
    }
}
```

**2. Notification Service Consumer:**

```java
@Component
@Slf4j
public class EventListener {

    @KafkaListener(
        topics = "${kafka.topic.transaction-events}",
        groupId = "notification-service-group"
    )
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("Received transaction event: {} for transaction {}", 
            event.getEventType(), event.getTransactionId());
        
        String message = buildNotificationMessage(event);
        sendNotification(event.getAccountNumber(), message);
    }
    
    private String buildNotificationMessage(TransactionEvent event) {
        return switch (event.getEventType()) {
            case TRANSACTION_CREATED -> 
                String.format("Transaction %s created: %s %s shares of %s",
                    event.getTransactionId(),
                    event.getTransactionType(),
                    event.getQuantity(),
                    event.getSymbol());
                    
            case TRANSACTION_COMPLETED -> 
                String.format("Transaction %s completed successfully. Total: $%s",
                    event.getTransactionId(),
                    event.getTotalAmount());
                    
            case TRANSACTION_FAILED -> 
                String.format("Transaction %s failed. Status: %s",
                    event.getTransactionId(),
                    event.getStatus());
                    
            case TRANSACTION_CANCELLED -> 
                String.format("Transaction %s has been cancelled",
                    event.getTransactionId());
                    
            default -> 
                String.format("Transaction %s update: %s",
                    event.getTransactionId(),
                    event.getEventType());
        };
    }
    
    private void sendNotification(String accountNumber, String message) {
        // Implementation: email, SMS, push notification, etc.
        log.info("Sending notification to {}: {}", accountNumber, message);
    }
}
```

### Event Handling Best Practices

**1. Idempotency:**

```java
@KafkaListener(topics = "transaction-events", groupId = "portfolio-service-group")
public void handleTransactionEvent(TransactionEvent event) {
    // Check if already processed
    if (processedEventRepository.existsByEventId(event.getTransactionId())) {
        log.info("Event {} already processed, skipping", event.getTransactionId());
        return;
    }
    
    try {
        // Process event
        processEvent(event);
        
        // Mark as processed
        processedEventRepository.save(new ProcessedEvent(event.getTransactionId()));
    } catch (Exception e) {
        log.error("Failed to process event: {}", e.getMessage());
        throw e; // Kafka will retry
    }
}
```

**2. Event Versioning:**

```java
@Data
@Builder
public class TransactionEvent {
    private String eventVersion = "1.0";  // Add version field
    private EventType eventType;
    // ... other fields
}

// Consumer handles different versions
@KafkaListener(topics = "transaction-events")
public void handleEvent(TransactionEvent event) {
    if ("1.0".equals(event.getEventVersion())) {
        handleV1Event(event);
    } else if ("2.0".equals(event.getEventVersion())) {
        handleV2Event(event);
    } else {
        log.warn("Unknown event version: {}", event.getEventVersion());
    }
}
```

**3. Error Handling and Dead Letter Queue:**

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Retry configuration
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate()),
            new FixedBackOff(1000L, 3L) // 3 retries with 1 second between
        ));
        
        return factory;
    }
}
```

**4. Ordering Guarantees:**

```java
// Use transactionId as partition key to ensure ordering per transaction
private void publishEvent(Transaction transaction, EventType eventType) {
    TransactionEvent event = buildEvent(transaction, eventType);
    
    // Send with key = transactionId
    // All events for same transaction go to same partition (ordered)
    kafkaTemplate.send(
        transactionEventsTopic,
        transaction.getId(),  // Key for partitioning
        event
    );
}
```

**5. Event Monitoring:**

```java
@Aspect
@Component
@Slf4j
public class EventPublishingAspect {

    @Around("execution(* com.wealthmanagement.transaction.service..publishEvent(..))")
    public Object logEventPublishing(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        
        try {
            result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Event published successfully in {}ms", duration);
            return result;
        } catch (Exception e) {
            log.error("Event publishing failed: {}", e.getMessage());
            // Could send alert, increment metric, etc.
            throw e;
        }
    }
}
```

**Key Takeaways:**

- ✅ **Decouple services** - Transaction service doesn't need to know about Portfolio/Notification services
- ✅ **Async processing** - Consumers process events independently without blocking transaction service
- ✅ **Event sourcing** - Every state change produces an event (audit trail)
- ✅ **Idempotency** - Consumers can safely process same event multiple times
- ✅ **Ordering** - Partition by transactionId ensures events processed in order
- ✅ **Reliability** - Kafka persists events, retries on failure, dead-letter queue for poison pills
- ✅ **Scalability** - Multiple consumer instances can parallelize processing

## Validation Rules

### Overview

Transaction Service implements **multiple layers of validation** to ensure data integrity and business rule compliance.

**Validation Layers:**
```
1. DTO Validation (Bean Validation)
   ↓
2. Business Rule Validation (Service Layer)
   ↓
3. State Validation (State Machine Guards)
   ↓
4. Data Integrity (Database Constraints)
```

### 1. DTO Validation (Bean Validation)

**Request DTO with annotations:**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRequest {

    @NotBlank(message = "Portfolio ID is required")
    private String portfolioId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Transaction type is required")
    private Transaction.TransactionType type;

    @NotBlank(message = "Symbol is required for BUY/SELL transactions")
    private String symbol;

    private String assetName;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @PositiveOrZero(message = "Commission cannot be negative")
    private BigDecimal commission;

    @Pattern(regexp = "USD|EUR|GBP", message = "Currency must be USD, EUR, or GBP")
    private String currency;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
```

**Controller validates DTOs:**

```java
@RestController
@RequestMapping("/api/transaction/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionDTO.Response> createTransaction(
            @Valid @RequestBody TransactionDTO.CreateRequest request) {
        // @Valid triggers Bean Validation
        TransactionDTO.Response response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

**Validation Error Response:**

```json
POST /api/transaction/transactions
{
  "portfolioId": "",
  "quantity": -10,
  "price": 0
}

Response: 400 Bad Request
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "portfolioId",
      "message": "Portfolio ID is required"
    },
    {
      "field": "quantity",
      "message": "Quantity must be positive"
    },
    {
      "field": "price",
      "message": "Price must be positive"
    }
  ]
}
```

### 2. Business Rule Validation

**Custom validation in service layer:**

```java
@Service
@RequiredArgsConstructor
public class TransactionService {

    @Transactional
    public TransactionDTO.Response createTransaction(TransactionDTO.CreateRequest request) {
        // 1. Validate portfolio exists
        validatePortfolioExists(request.getPortfolioId());
        
        // 2. Validate symbol for BUY/SELL transactions
        validateSymbolRequired(request);
        
        // 3. Validate transaction type-specific rules
        validateTransactionType(request);
        
        // 4. Validate sufficient funds (for BUY)
        if (request.getType() == TransactionType.BUY) {
            validateSufficientFunds(request);
        }
        
        // 5. Validate sufficient holdings (for SELL)
        if (request.getType() == TransactionType.SELL) {
            validateSufficientHoldings(request);
        }
        
        // Create transaction...
    }
    
    private void validatePortfolioExists(String portfolioId) {
        // Call Portfolio Service to verify portfolio exists
        // In real implementation:
        // Portfolio portfolio = portfolioClient.getPortfolio(portfolioId);
        // if (portfolio == null) throw new PortfolioNotFoundException(...);
        
        if (portfolioId == null || portfolioId.isBlank()) {
            throw new ValidationException("Portfolio ID cannot be blank");
        }
    }
    
    private void validateSymbolRequired(TransactionDTO.CreateRequest request) {
        if ((request.getType() == TransactionType.BUY || 
             request.getType() == TransactionType.SELL) &&
            (request.getSymbol() == null || request.getSymbol().isBlank())) {
            throw new ValidationException("Symbol is required for " + request.getType() + " transactions");
        }
    }
    
    private void validateTransactionType(TransactionDTO.CreateRequest request) {
        switch (request.getType()) {
            case BUY, SELL:
                // Must have symbol, quantity, price
                if (request.getSymbol() == null || 
                    request.getQuantity() == null || 
                    request.getPrice() == null) {
                    throw new ValidationException(
                        "BUY/SELL transactions require symbol, quantity, and price");
                }
                break;
                
            case DEPOSIT, WITHDRAWAL:
                // Must have amount, no symbol/quantity
                if (request.getSymbol() != null) {
                    throw new ValidationException(
                        "DEPOSIT/WITHDRAWAL transactions cannot have symbol");
                }
                break;
                
            case DIVIDEND:
                // Must have symbol and amount
                if (request.getSymbol() == null) {
                    throw new ValidationException("DIVIDEND transactions require symbol");
                }
                break;
        }
    }
    
    private void validateSufficientFunds(TransactionDTO.CreateRequest request) {
        BigDecimal totalCost = request.getQuantity()
            .multiply(request.getPrice())
            .add(request.getCommission() != null ? request.getCommission() : BigDecimal.ZERO);
        
        // In real implementation: fetch portfolio cash balance
        // BigDecimal cashBalance = portfolioClient.getCashBalance(request.getPortfolioId());
        // if (cashBalance.compareTo(totalCost) < 0) {
        //     throw new InsufficientFundsException(...);
        // }
        
        log.debug("Total cost for transaction: {}", totalCost);
    }
    
    private void validateSufficientHoldings(TransactionDTO.CreateRequest request) {
        // In real implementation: verify portfolio has enough shares to sell
        // BigDecimal holdings = portfolioClient.getHoldings(
        //     request.getPortfolioId(), 
        //     request.getSymbol()
        // );
        // if (holdings.compareTo(request.getQuantity()) < 0) {
        //     throw new InsufficientHoldingsException(...);
        // }
        
        log.debug("Validating holdings for SELL transaction");
    }
}
```

### 3. State Validation

**Guard clauses prevent invalid state transitions:**

```java
@Transactional
public TransactionDTO.Response processTransaction(String transactionId) {
    Transaction transaction = getTransactionById(transactionId);
    
    // Guard: Only PENDING transactions can be processed
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        throw new IllegalStateException(
            String.format("Cannot process transaction in %s status. Only PENDING transactions can be processed.",
                transaction.getStatus()));
    }
    
    // Continue processing...
}

@Transactional
public TransactionDTO.Response cancelTransaction(String transactionId) {
    Transaction transaction = getTransactionById(transactionId);
    
    // Guard: Cannot cancel completed transactions
    if (transaction.getStatus() == TransactionStatus.COMPLETED) {
        throw new IllegalStateException("Cannot cancel completed transaction");
    }
    
    // Guard: Cannot cancel failed transactions
    if (transaction.getStatus() == TransactionStatus.FAILED) {
        throw new IllegalStateException("Cannot cancel failed transaction");
    }
    
    // Guard: Only PENDING can be cancelled
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        throw new IllegalStateException(
            String.format("Can only cancel PENDING transactions. Current status: %s",
                transaction.getStatus()));
    }
    
    // Cancel transaction...
}
```

### 4. Custom Validators

**Reusable validation components:**

```java
@Component
public class TransactionValidator {

    private static final Set<String> VALID_SYMBOLS = Set.of(
        "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "META", "NVDA"
    );
    
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("10000");
    private static final BigDecimal MAX_PRICE = new BigDecimal("100000");
    
    public void validateBuyTransaction(TransactionDTO.CreateRequest request) {
        validateSymbol(request.getSymbol());
        validateQuantity(request.getQuantity());
        validatePrice(request.getPrice());
        validateCommission(request.getCommission());
    }
    
    public void validateSellTransaction(TransactionDTO.CreateRequest request) {
        validateSymbol(request.getSymbol());
        validateQuantity(request.getQuantity());
        validatePrice(request.getPrice());
    }
    
    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new ValidationException("Symbol cannot be blank");
        }
        
        if (!VALID_SYMBOLS.contains(symbol.toUpperCase())) {
            throw new ValidationException(
                String.format("Invalid symbol: %s. Valid symbols: %s", 
                    symbol, VALID_SYMBOLS));
        }
    }
    
    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new ValidationException("Quantity is required");
        }
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Quantity must be positive");
        }
        
        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new ValidationException(
                String.format("Quantity cannot exceed %s", MAX_QUANTITY));
        }
        
        // Ensure quantity is a whole number (no fractional shares)
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new ValidationException("Quantity must be a whole number");
        }
    }
    
    private void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new ValidationException("Price is required");
        }
        
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Price must be positive");
        }
        
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new ValidationException(
                String.format("Price cannot exceed %s", MAX_PRICE));
        }
    }
    
    private void validateCommission(BigDecimal commission) {
        if (commission != null && commission.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Commission cannot be negative");
        }
    }
}
```

### 5. Validation Exception Hierarchy

```java
// Base exception
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}

// Specific exceptions
public class InsufficientFundsException extends ValidationException {
    public InsufficientFundsException(String portfolioId, BigDecimal required, BigDecimal available) {
        super(String.format(
            "Insufficient funds in portfolio %s. Required: $%s, Available: $%s",
            portfolioId, required, available));
    }
}

public class InsufficientHoldingsException extends ValidationException {
    public InsufficientHoldingsException(String portfolioId, String symbol, 
                                          BigDecimal required, BigDecimal available) {
        super(String.format(
            "Insufficient holdings in portfolio %s for %s. Required: %s, Available: %s",
            portfolioId, symbol, required, available));
    }
}

public class InvalidSymbolException extends ValidationException {
    public InvalidSymbolException(String symbol) {
        super(String.format("Invalid symbol: %s", symbol));
    }
}

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String transactionId) {
        super(String.format("Transaction not found: %s", transactionId));
    }
}
```

### 6. Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        List<ErrorDetail> errors = fieldErrors.stream()
            .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage()))
            .collect(Collectors.toList());
        
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Input validation error")
            .errors(errors)
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Business Rule Violation")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Invalid State Transition")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(TransactionNotFoundException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}

@Data
@Builder
class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private List<ErrorDetail> errors;
}

@Data
@AllArgsConstructor
class ErrorDetail {
    private String field;
    private String message;
}
```

### 7. Validation Examples

**Example 1: Missing Required Fields**

```java
POST /api/transaction/transactions
{
  "type": "BUY"
}

Response: 400 Bad Request
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Input validation error",
  "errors": [
    {"field": "portfolioId", "message": "Portfolio ID is required"},
    {"field": "accountNumber", "message": "Account number is required"},
    {"field": "symbol", "message": "Symbol is required for BUY/SELL transactions"},
    {"field": "quantity", "message": "Quantity is required"},
    {"field": "price", "message": "Price is required"}
  ]
}
```

**Example 2: Invalid Values**

```java
POST /api/transaction/transactions
{
  "portfolioId": "PORT001",
  "accountNumber": "ACC001",
  "type": "BUY",
  "symbol": "INVALID",
  "quantity": -10,
  "price": 0,
  "commission": -5
}

Response: 400 Bad Request
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 400,
  "error": "Validation Failed",
  "errors": [
    {"field": "symbol", "message": "Invalid symbol: INVALID. Valid symbols: [AAPL, GOOGL, MSFT, ...]"},
    {"field": "quantity", "message": "Quantity must be positive"},
    {"field": "price", "message": "Price must be positive"},
    {"field": "commission", "message": "Commission cannot be negative"}
  ]
}
```

**Example 3: Insufficient Funds**

```java
POST /api/transaction/transactions
{
  "portfolioId": "PORT001",
  "type": "BUY",
  "symbol": "AAPL",
  "quantity": 1000,
  "price": 150.00
}

// Portfolio has only $10,000 cash
// Required: $150,000

Response: 400 Bad Request
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 400,
  "error": "Business Rule Violation",
  "message": "Insufficient funds in portfolio PORT001. Required: $150000, Available: $10000"
}
```

**Example 4: Invalid State Transition**

```java
POST /api/transaction/transactions/TXN001/process
// Transaction is already COMPLETED

Response: 409 Conflict
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 409,
  "error": "Invalid State Transition",
  "message": "Cannot process transaction in COMPLETED status. Only PENDING transactions can be processed."
}
```

**Example 5: Transaction Not Found**

```java
GET /api/transaction/transactions/INVALID_ID

Response: 404 Not Found
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Transaction not found: INVALID_ID"
}
```

### 8. Validation Best Practices

**✅ DO:**
- Validate at multiple layers (DTO, service, state)
- Return clear, actionable error messages
- Use specific exception types
- Log validation failures for monitoring
- Fail fast (validate early, return immediately)
- Use Bean Validation annotations for simple rules
- Implement custom validators for complex business rules

**❌ DON'T:**
- Skip validation assuming client will validate
- Return generic error messages like "Invalid request"
- Throw exceptions with stack traces to clients
- Allow invalid state transitions
- Process transactions without validating funds/holdings
- Mix validation logic with business logic
- Ignore edge cases (null, negative, zero values)

**Key Validation Rules Summary:**

| Rule | Validation | Exception |
|------|-----------|-----------|
| Required fields | `@NotNull`, `@NotBlank` | `MethodArgumentNotValidException` |
| Positive values | `@Positive`, `@PositiveOrZero` | `MethodArgumentNotValidException` |
| Valid symbol | Custom validator | `InvalidSymbolException` |
| Sufficient funds | Service layer check | `InsufficientFundsException` |
| Sufficient holdings | Service layer check | `InsufficientHoldingsException` |
| Valid state | Guard clauses | `IllegalStateException` |
| Transaction exists | Repository check | `TransactionNotFoundException` |

## Error Handling

### Overview

Transaction Service implements **comprehensive error handling** to ensure reliability and provide clear feedback.

**Error Handling Strategies:**
```
1. Validation Errors → 400 Bad Request
2. Business Logic Errors → 400 Bad Request  
3. State Errors → 409 Conflict
4. Not Found Errors → 404 Not Found
5. Processing Errors → 500 Internal Server Error
6. Compensation for failures
7. Logging and monitoring
```

### 1. Transaction Processing Errors

**Handling failures during processing:**

```java
@Transactional
public TransactionDTO.Response processTransaction(String transactionId) {
    Transaction transaction = getTransactionById(transactionId);
    
    // Guard clause
    if (transaction.getStatus() != TransactionStatus.PENDING) {
        return transactionMapper.toResponse(transaction);
    }
    
    // Update to PROCESSING
    transaction.setStatus(TransactionStatus.PROCESSING);
    transactionRepository.save(transaction);
    publishEvent(TransactionEvent.EventType.TRANSACTION_PROCESSING, transaction);
    
    try {
        // Execute transaction (may throw exceptions)
        executeTransaction(transaction);
        
        // Success path
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedDate(LocalDateTime.now());
        Transaction completed = transactionRepository.save(transaction);
        
        publishEvent(TransactionEvent.EventType.TRANSACTION_COMPLETED, completed);
        return transactionMapper.toResponse(completed);
        
    } catch (InsufficientFundsException e) {
        // Business rule violation
        log.error("Transaction {} failed: {}", transactionId, e.getMessage());
        return handleTransactionFailure(transaction, "Insufficient funds", e);
        
    } catch (BrokerException e) {
        // External system error
        log.error("Broker rejected transaction {}: {}", transactionId, e.getMessage());
        return handleTransactionFailure(transaction, "Broker error: " + e.getMessage(), e);
        
    } catch (Exception e) {
        // Unexpected error
        log.error("Unexpected error processing transaction {}", transactionId, e);
        return handleTransactionFailure(transaction, "Processing error", e);
    }
}

private TransactionDTO.Response handleTransactionFailure(
        Transaction transaction, String errorMessage, Exception cause) {
    
    transaction.setStatus(TransactionStatus.FAILED);
    transaction.setNotes(errorMessage);
    Transaction failed = transactionRepository.save(transaction);
    
    publishEvent(TransactionEvent.EventType.TRANSACTION_FAILED, failed);
    
    // Don't throw exception - return failed transaction
    return transactionMapper.toResponse(failed);
}
```

### 2. Retry Logic

**Automatic retry for transient failures:**

```java
@Service
public class TransactionService {

    @Retryable(
        value = {BrokerConnectionException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void executeTransaction(Transaction transaction) throws BrokerException {
        log.info("Executing transaction {} (attempt)", transaction.getId());
        
        // Call broker API
        brokerClient.executeTrade(transaction);
        
        // If BrokerConnectionException thrown, retry up to 3 times
        // Delays: 1s, 2s, 4s
    }
    
    @Recover
    public void recoverFromBrokerFailure(BrokerConnectionException e, Transaction transaction) {
        // Called after all retries exhausted
        log.error("All retry attempts failed for transaction {}", transaction.getId());
        handleTransactionFailure(transaction, "Broker unavailable after retries", e);
    }
}
```

### 3. Compensation Transactions

**Rollback completed transactions:**

```java
@Service
public class TransactionCompensationService {

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * Create compensating transaction to reverse a completed transaction
     */
    @Transactional
    public TransactionDTO.Response compensate(String originalTransactionId) {
        Transaction original = transactionRepository.findById(originalTransactionId)
            .orElseThrow(() -> new TransactionNotFoundException(
                "Original transaction not found: " + originalTransactionId));
        
        // Only compensate COMPLETED transactions
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalStateException(
                "Can only compensate COMPLETED transactions. Current status: " + 
                original.getStatus());
        }
        
        // Create opposite transaction
        TransactionDTO.CreateRequest compensation = buildCompensation(original);
        
        TransactionDTO.Response result = transactionService.createTransaction(compensation);
        
        log.info("Created compensation transaction {} for original transaction {}", 
            result.getId(), originalTransactionId);
        
        return result;
    }
    
    private TransactionDTO.CreateRequest buildCompensation(Transaction original) {
        Transaction.TransactionType compensationType = switch (original.getType()) {
            case BUY -> Transaction.TransactionType.SELL;
            case SELL -> Transaction.TransactionType.BUY;
            case DEPOSIT -> Transaction.TransactionType.WITHDRAWAL;
            case WITHDRAWAL -> Transaction.TransactionType.DEPOSIT;
            case DIVIDEND -> throw new IllegalStateException("Cannot compensate DIVIDEND");
        };
        
        return TransactionDTO.CreateRequest.builder()
            .portfolioId(original.getPortfolioId())
            .accountNumber(original.getAccountNumber())
            .type(compensationType)
            .symbol(original.getSymbol())
            .assetName(original.getAssetName())
            .quantity(original.getQuantity())
            .price(original.getPrice())
            .commission(original.getCommission())
            .currency(original.getCurrency())
            .notes("Compensation for transaction: " + original.getId())
            .build();
    }
}
```

**Example:**
```
Original Transaction:
  BUY 100 AAPL @ $150 (Total: $15,010)
  
Compensation Transaction:
  SELL 100 AAPL @ $150 (Total: $14,990)
  
Net Effect: Reverses the BUY
```

### 4. Dead Letter Queue (DLQ)

**Handle poison messages in Kafka consumers:**

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> 
            kafkaListenerContainerFactory() {
        
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Error handler with DLQ
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate(),
            (record, ex) -> new TopicPartition(
                record.topic() + ".DLT",  // Dead Letter Topic
                record.partition()
            )
        );
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(1000L, 3L)  // 3 retries, 1 second apart
        );
        
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
}
```

**DLQ Consumer (for manual intervention):**

```java
@Component
@Slf4j
public class DeadLetterQueueConsumer {

    @KafkaListener(topics = "transaction-events.DLT")
    public void handleDeadLetter(ConsumerRecord<String, TransactionEvent> record) {
        log.error("Message sent to DLQ: key={}, value={}, reason={}",
            record.key(),
            record.value(),
            record.headers());
        
        // Store in database for manual review
        // Send alert to operations team
        // Trigger monitoring dashboard
    }
}
```

### 5. Circuit Breaker Pattern

**Prevent cascading failures:**

```java
@Service
public class TransactionService {

    @Autowired
    private BrokerClient brokerClient;
    
    @CircuitBreaker(
        name = "brokerService",
        fallbackMethod = "executeFallback"
    )
    public void executeTransaction(Transaction transaction) throws BrokerException {
        // Call external broker
        brokerClient.executeTrade(transaction);
    }
    
    // Fallback method when circuit is open
    public void executeFallback(Transaction transaction, Exception e) {
        log.warn("Circuit breaker open for broker service. Queueing transaction {}", 
            transaction.getId());
        
        // Queue for later processing
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setNotes("Queued - broker service unavailable");
        transactionRepository.save(transaction);
        
        // Could publish to retry queue
        publishEvent(TransactionEvent.EventType.TRANSACTION_PROCESSING, transaction);
    }
}
```

**application.yml configuration:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      brokerService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

### 6. Error Logging and Monitoring

**Structured logging for errors:**

```java
@Aspect
@Component
@Slf4j
public class TransactionErrorLoggingAspect {

    @AfterThrowing(
        pointcut = "execution(* com.wealthmanagement.transaction.service..*(..))",
        throwing = "exception"
    )
    public void logError(JoinPoint joinPoint, Exception exception) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        log.error("Error in {}: {}. Args: {}", 
            methodName, 
            exception.getMessage(), 
            Arrays.toString(args),
            exception);
        
        // Could send to monitoring system (Prometheus, Grafana, etc.)
        // metricsService.incrementErrorCounter(methodName, exception.getClass().getSimpleName());
    }
}
```

**Metrics collection:**

```java
@Service
public class TransactionMetricsService {

    private final Counter transactionCreatedCounter;
    private final Counter transactionCompletedCounter;
    private final Counter transactionFailedCounter;
    private final Timer transactionProcessingTimer;

    public TransactionMetricsService(MeterRegistry meterRegistry) {
        this.transactionCreatedCounter = meterRegistry.counter("transactions.created");
        this.transactionCompletedCounter = meterRegistry.counter("transactions.completed");
        this.transactionFailedCounter = meterRegistry.counter("transactions.failed");
        this.transactionProcessingTimer = meterRegistry.timer("transactions.processing.time");
    }
    
    public void recordTransactionCreated() {
        transactionCreatedCounter.increment();
    }
    
    public void recordTransactionCompleted() {
        transactionCompletedCounter.increment();
    }
    
    public void recordTransactionFailed() {
        transactionFailedCounter.increment();
    }
    
    public void recordProcessingTime(Runnable task) {
        transactionProcessingTimer.record(task);
    }
}
```

### 7. Error Response Format

**Consistent error responses:**

```java
@Data
@Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String transactionId;  // Optional: for transaction-specific errors
    private List<ErrorDetail> errors;  // For validation errors
}
```

**Examples:**

**Validation Error:**
```json
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/transaction/transactions",
  "errors": [
    {"field": "quantity", "message": "Quantity must be positive"}
  ]
}
```

**Business Logic Error:**
```json
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 400,
  "error": "Business Rule Violation",
  "message": "Insufficient funds in portfolio PORT001. Required: $150000, Available: $10000",
  "path": "/api/transaction/transactions",
  "transactionId": null
}
```

**Processing Error:**
```json
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Transaction processing failed",
  "path": "/api/transaction/transactions/TXN001/process",
  "transactionId": "TXN001"
}
```

**Not Found Error:**
```json
{
  "timestamp": "2025-12-30T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Transaction not found: TXN999",
  "path": "/api/transaction/transactions/TXN999",
  "transactionId": "TXN999"
}
```

### 8. Error Handling Best Practices

**✅ DO:**
- Use specific exception types
- Return appropriate HTTP status codes
- Provide clear, actionable error messages
- Log all errors with context
- Implement retry logic for transient failures
- Use circuit breakers for external dependencies
- Handle errors at appropriate layers
- Publish failed transaction events
- Update transaction status to FAILED
- Use @Transactional to ensure atomicity
- Monitor error rates and patterns
- Implement dead letter queues for poison messages

**❌ DON'T:**
- Return stack traces to clients (security risk)
- Use generic error messages ("Error occurred")
- Throw exceptions without context
- Ignore errors silently
- Retry indefinitely without backoff
- Process failed transactions again
- Delete failed transactions (keep for audit)
- Mix error handling with business logic
- Hardcode error messages (use constants/enums)
- Return 200 OK for errors

**Error Handling Summary:**

| Error Type | HTTP Status | Action | Example |
|-----------|-------------|--------|---------|
| Validation | 400 | Return validation errors | Missing required field |
| Business Rule | 400 | Return error message | Insufficient funds |
| State Error | 409 | Return current state | Already processed |
| Not Found | 404 | Return not found | Transaction doesn't exist |
| Processing | 500 | Set status=FAILED, log error | Broker timeout |
| External API | 503 | Retry, circuit breaker | Broker unavailable |

**Transaction Lifecycle Error States:**

```
PENDING → PROCESSING → FAILED
                 ↓
            (Compensation)
                 ↓
            New Transaction (opposite type)
```

**Key Principles:**

1. **Fail Fast:** Validate early, return errors immediately
2. **Fail Safe:** Never leave transaction in inconsistent state
3. **Fail Visible:** Log all errors, publish events, update status
4. **Fail Recoverable:** Provide compensation mechanisms
5. **Fail Informative:** Clear error messages for debugging

---

## Summary

The **Transaction Service** is a critical component demonstrating:

✅ **State Machine Pattern:** PENDING → PROCESSING → COMPLETED/FAILED  
✅ **Event-Driven Architecture:** Kafka events at each transition  
✅ **Multi-Layer Validation:** DTO, business rules, state guards  
✅ **Comprehensive Error Handling:** Retry, circuit breaker, compensation  
✅ **Transaction Processing:** Buy/sell with automatic calculations  
✅ **Asynchronous Communication:** Decoupled from Portfolio/Notification services  
✅ **Data Integrity:** @Transactional, MongoDB persistence, audit trail

**For Interviews:**
- Explain state machine and valid transitions
- Describe event publishing and consumer patterns
- Walk through validation layers
- Discuss error handling strategies (retry, compensation, DLQ)
- Demonstrate understanding of @Transactional and atomicity
- Explain why separate Transaction Service (bounded context)
