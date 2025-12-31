# Getting Started Guide - Portfolio Management System

## Overview
This guide walks you through using your Portfolio Management System to demonstrate microservices architecture, event-driven design, and modern Java development.

---

## Step 1: Verify All Services Are Running

Open PowerShell and run:

```powershell
docker-compose ps
```

You should see all services with status "Up" or "Up (healthy)":
- ✅ **mongodb-portfolio** (port 27017)
- ✅ **mongodb-transaction** (port 27018)
- ✅ **redis-cache** (port 6379)
- ✅ **kafka** (port 9092)
- ✅ **zookeeper** (port 2181)
- ✅ **portfolio-service** (port 8081)
- ✅ **transaction-service** (port 8082)
- ✅ **notification-service** (port 8083)
- ✅ **api-gateway** (port 8080)

---

## Step 2: Access the Dashboard

First, start the frontend development server:

```powershell
cd .\frontend\
npx vite
```

Then open your browser and navigate to:

**Frontend Dashboard:** http://localhost:3000

You should see:
- Total portfolio value across all clients
- Total cash balance
- Number of holdings
- Client portfolios table

---

## Step 3: Explore API Documentation

The system uses **OpenAPI/Swagger** for interactive API documentation.

**Portfolio Service Swagger UI:** http://localhost:8081/api/portfolio/swagger-ui.html

Here you can:
- See all available endpoints
- Try out API calls directly from the browser
- View request/response schemas

---

## Step 4: Create Your First Portfolio

### Option A: Using PowerShell (Recommended for Windows)

```powershell
# Create a portfolio for a new client
$portfolio = @{
    clientId = "CLIENT002"
    clientName = "Jane Smith"
    accountNumber = "ACC-87654321"
    currency = "USD"
    cashBalance = 250000.00
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8081/api/portfolio/portfolios `
    -Method Post `
    -Body $portfolio `
    -ContentType "application/json"
```

### Option B: Using Swagger UI

1. Go to http://localhost:8081/api/portfolio/swagger-ui.html
2. Find **POST /portfolios** endpoint
3. Click "Try it out"
4. Use this JSON:

```json
{
  "clientId": "CLIENT002",
  "clientName": "Jane Smith",
  "accountNumber": "ACC-87654321",
  "currency": "USD",
  "cashBalance": 250000.00
}
```

5. Click "Execute"

**Refresh the dashboard** at http://localhost:3000 - you should now see 2 portfolios!

---

## Step 5: Add Holdings to a Portfolio

First, get the portfolio ID from the dashboard or API response. Then add some stock holdings:

```powershell
# Replace PORTFOLIO_ID with the actual ID from your portfolio
$portfolioId = "YOUR_PORTFOLIO_ID_HERE"

# Add Apple stock
$holding = @{
    symbol = "AAPL"
    name = "Apple Inc."
    quantity = 100
    purchasePrice = 150.00
    currentPrice = 180.00
    assetType = "STOCK"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId/holdings" `
    -Method Post `
    -Body $holding `
    -ContentType "application/json"
```

Add more holdings:

```powershell
# Microsoft
$msft = @{
    symbol = "MSFT"
    name = "Microsoft Corporation"
    quantity = 50
    purchasePrice = 300.00
    currentPrice = 350.00
    assetType = "STOCK"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId/holdings" `
    -Method Post `
    -Body $msft `
    -ContentType "application/json"

# Tesla
$tsla = @{
    symbol = "TSLA"
    name = "Tesla Inc."
    quantity = 25
    purchasePrice = 200.00
    currentPrice = 250.00
    assetType = "STOCK"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId/holdings" `
    -Method Post `
    -Body $tsla `
    -ContentType "application/json"
```

**Refresh the dashboard** - the holdings count should now update!

---

## Step 6: Create Transactions

Create a buy transaction:

```powershell
$transaction = @{
    portfolioId = "$portfolioId"
    symbol = "GOOGL"
    transactionType = "BUY"
    quantity = 10
    price = 140.00
    currency = "USD"
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8082/api/transaction/transactions `
    -Method Post `
    -Body $transaction `
    -ContentType "application/json"
```

Create a sell transaction:

```powershell
$sell = @{
    portfolioId = "$portfolioId"
    symbol = "AAPL"
    transactionType = "SELL"
    quantity = 20
    price = 185.00
    currency = "USD"
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8082/api/transaction/transactions `
    -Method Post `
    -Body $sell `
    -ContentType "application/json"
```

---

## Step 7: See Event-Driven Architecture in Action

The system uses **Apache Kafka** for event-driven communication between services. Let's watch the events flow!

### Watch Notification Service Logs

When you create portfolios or transactions, events are published to Kafka and consumed by the notification service:

```powershell
# Watch notification service logs in real-time
docker logs notification-service --tail=50 -f
```

Now create a portfolio or transaction (from Step 4 or 6). You'll see log entries like:

```
Received portfolio created event: PortfolioCreatedEvent(portfolioId=..., clientName=Jane Smith, ...)
Processing notification for portfolio creation: Jane Smith
```

Press `Ctrl+C` to stop following logs.

---

## Step 8: Explore the Microservices

### View All Endpoints Through API Gateway

The API Gateway routes requests to the appropriate microservice:

- **Portfolio Service:** http://localhost:8080/api/portfolio/portfolios
- **Transaction Service:** http://localhost:8080/api/transaction/transactions
- **Notification Service:** http://localhost:8080/api/notification/notifications

### Check Service Health

```powershell
# API Gateway health
Invoke-RestMethod http://localhost:8080/actuator/health

# Portfolio Service health
Invoke-RestMethod http://localhost:8081/api/portfolio/actuator/health

# Transaction Service health
Invoke-RestMethod http://localhost:8082/api/transaction/actuator/health
```

### View Metrics

```powershell
# Portfolio service metrics
Invoke-RestMethod http://localhost:8081/api/portfolio/actuator/metrics
```

---

## Step 9: Check Redis Caching

The portfolio service uses **Redis** for caching. Let's verify it's working:

```powershell
# Connect to Redis container
docker exec -it redis-cache redis-cli

# Inside Redis, check cached portfolios
KEYS *
GET "portfolio:YOUR_PORTFOLIO_ID"

# Exit Redis
exit
```

---

## Step 10: Query MongoDB Directly

View the data stored in MongoDB:

```powershell
# Connect to Portfolio MongoDB
docker exec -it mongodb-portfolio mongosh portfolio_db

# Inside MongoDB, run queries
db.portfolios.find().pretty()
db.portfolios.countDocuments()

# Exit MongoDB
exit

# Connect to Transaction MongoDB
docker exec -it mongodb-transaction mongosh transaction_db

# Query transactions
db.transactions.find().pretty()

# Exit
exit
```

---

## Common Queries to Try

### Get All Portfolios

```powershell
Invoke-RestMethod http://localhost:8081/api/portfolio/portfolios
```

### Get Portfolio by ID

```powershell
Invoke-RestMethod "http://localhost:8081/api/portfolio/portfolios/$portfolioId"
```

### Get Portfolios by Client

```powershell
Invoke-RestMethod "http://localhost:8081/api/portfolio/portfolios/client/CLIENT002"
```

### Get All Transactions

```powershell
Invoke-RestMethod http://localhost:8082/api/transaction/transactions
```

### Get Transactions by Portfolio

```powershell
Invoke-RestMethod "http://localhost:8082/api/transaction/transactions/portfolio/$portfolioId"
```

### Update Portfolio Cash Balance

```powershell
$update = @{
    cashBalance = 300000.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8081/api/portfolio/portfolios/$portfolioId" `
    -Method Put `
    -Body $update `
    -ContentType "application/json"
```

---

## Architecture Highlights to Demonstrate

### 1. **Microservices Pattern**
- Each service (Portfolio, Transaction, Notification) is independent
- Services communicate via REST APIs and Kafka events
- Each service has its own database (MongoDB instances)

### 2. **API Gateway Pattern**
- Single entry point at port 8080
- Routes requests to appropriate services
- Includes circuit breaker for resilience
- CORS configuration for frontend

### 3. **Event-Driven Architecture**
- Portfolio events → Kafka → Notification Service
- Transaction events → Kafka → Portfolio updates
- Asynchronous, decoupled communication

### 4. **Caching Strategy**
- Redis caches frequently accessed portfolios
- Cache invalidation on updates
- Improves performance

### 5. **Database per Service**
- portfolio_db (MongoDB) for portfolios
- transaction_db (MongoDB) for transactions
- Each service owns its data

### 6. **Observability**
- Spring Boot Actuator endpoints
- Health checks, metrics, info
- Comprehensive logging

---

## Stopping the System

When you're done:

```powershell
# Stop all services
docker-compose down

# Stop and remove volumes (clears all data)
docker-compose down -v

# Stop frontend (in the terminal running npm)
Ctrl+C
```

---

## Restarting the System

```powershell
# Start all services
docker-compose up -d

# Start frontend (in a separate terminal)
cd c:\workspace\pms-2025-12-30\frontend
npx vite
```

---

## Troubleshooting

### Services won't start
```powershell
# Check logs
docker-compose logs portfolio-service
docker-compose logs api-gateway

# Restart specific service
docker-compose restart portfolio-service
```

### Frontend can't connect
- Verify Vite is running on port 3000
- Check API Gateway is healthy: http://localhost:8080/actuator/health
- Check browser console for errors

### Clear all data and restart fresh
```powershell
docker-compose down -v
docker-compose up -d --build
```

---

## What Makes This Demo Impressive

✅ **Microservices Architecture** - 4 independent services with clear boundaries  
✅ **Event-Driven Design** - Kafka for async communication  
✅ **Spring Boot 3.x** - Latest Spring framework with Java 17  
✅ **Spring Cloud Gateway** - Modern API gateway with circuit breakers  
✅ **Database per Service** - Proper data isolation  
✅ **Caching Strategy** - Redis for performance  
✅ **Docker Orchestration** - Complete containerized deployment  
✅ **OpenAPI Documentation** - Interactive API docs  
✅ **React Frontend** - Modern TypeScript UI  
✅ **TDD Approach** - Unit and integration tests  
✅ **CI/CD Ready** - GitHub Actions pipeline included  

---

## Next Steps for Interview Preparation

1. **Explain the Architecture** - Use the README.md diagram
2. **Demonstrate Service Communication** - Show Kafka events in logs
3. **Discuss Trade-offs** - Why Kafka vs REST, MongoDB vs SQL, etc.
4. **Show Testing** - Run `mvn test` in any service
5. **Explain Resilience** - Circuit breakers, retry logic, health checks
6. **Talk About Scalability** - Horizontal scaling, stateless services, caching

Good luck with your wealth management job application! 🚀
