# API Examples

This document provides sample API requests for testing the Portfolio Management System.

## Prerequisites

Ensure all services are running:
```bash
docker-compose up -d
```

## Portfolio Service Examples

### 1. Create a Portfolio

```bash
curl -X POST http://localhost:8080/api/portfolio/portfolios \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "CLIENT001",
    "clientName": "John Doe",
    "accountNumber": "ACC12345",
    "currency": "USD",
    "cashBalance": 100000.00
  }'
```

**Response:**
```json
{
  "id": "65a1b2c3d4e5f6g7h8i9j0k1",
  "clientId": "CLIENT001",
  "clientName": "John Doe",
  "accountNumber": "ACC12345",
  "currency": "USD",
  "totalValue": 100000.00,
  "cashBalance": 100000.00,
  "holdings": [],
  "status": "ACTIVE",
  "createdAt": "2025-12-30T10:00:00",
  "updatedAt": "2025-12-30T10:00:00"
}
```

### 2. Add a Holding to Portfolio

Replace `{portfolioId}` with the ID from the previous response.

```bash
curl -X POST http://localhost:8080/api/portfolio/portfolios/{portfolioId}/holdings \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "assetType": "STOCK",
    "quantity": 100,
    "averageCost": 150.00,
    "currentPrice": 155.00
  }'
```

### 3. Get Portfolio by ID

```bash
curl http://localhost:8080/api/portfolio/portfolios/{portfolioId}
```

### 4. Get All Portfolios Summary

```bash
curl http://localhost:8080/api/portfolio/portfolios
```

### 5. Update Portfolio

```bash
curl -X PUT http://localhost:8080/api/portfolio/portfolios/{portfolioId} \
  -H "Content-Type: application/json" \
  -d '{
    "cashBalance": 120000.00,
    "status": "ACTIVE"
  }'
```

## Transaction Service Examples

### 1. Create a Buy Transaction

```bash
curl -X POST http://localhost:8080/api/transaction/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "portfolioId": "{portfolioId}",
    "accountNumber": "ACC12345",
    "type": "BUY",
    "symbol": "MSFT",
    "assetName": "Microsoft Corporation",
    "quantity": 50,
    "price": 380.00,
    "currency": "USD",
    "commission": 9.99,
    "notes": "Initial purchase"
  }'
```

### 2. Create a Sell Transaction

```bash
curl -X POST http://localhost:8080/api/transaction/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "portfolioId": "{portfolioId}",
    "accountNumber": "ACC12345",
    "type": "SELL",
    "symbol": "AAPL",
    "assetName": "Apple Inc.",
    "quantity": 25,
    "price": 160.00,
    "currency": "USD",
    "commission": 9.99,
    "notes": "Partial sell"
  }'
```

### 3. Get Transaction by ID

```bash
curl http://localhost:8080/api/transaction/transactions/{transactionId}
```

### 4. Get Transactions by Portfolio

```bash
curl http://localhost:8080/api/transaction/transactions/portfolio/{portfolioId}
```

### 5. Get All Transactions

```bash
curl http://localhost:8080/api/transaction/transactions
```

### 6. Cancel a Transaction

```bash
curl -X PUT http://localhost:8080/api/transaction/transactions/{transactionId}/cancel
```

## Complete Workflow Example

Here's a complete workflow to demonstrate the system:

```bash
# 1. Create a portfolio
PORTFOLIO_RESPONSE=$(curl -s -X POST http://localhost:8080/api/portfolio/portfolios \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "CLIENT001",
    "clientName": "Jane Smith",
    "accountNumber": "ACC99999",
    "currency": "USD",
    "cashBalance": 500000.00
  }')

PORTFOLIO_ID=$(echo $PORTFOLIO_RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "Created Portfolio ID: $PORTFOLIO_ID"

# 2. Add multiple holdings
curl -X POST http://localhost:8080/api/portfolio/portfolios/$PORTFOLIO_ID/holdings \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "assetType": "STOCK",
    "quantity": 200,
    "averageCost": 150.00,
    "currentPrice": 155.00
  }'

curl -X POST http://localhost:8080/api/portfolio/portfolios/$PORTFOLIO_ID/holdings \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "MSFT",
    "name": "Microsoft Corporation",
    "assetType": "STOCK",
    "quantity": 150,
    "averageCost": 380.00,
    "currentPrice": 385.00
  }'

curl -X POST http://localhost:8080/api/portfolio/portfolios/$PORTFOLIO_ID/holdings \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "GOOGL",
    "name": "Alphabet Inc.",
    "assetType": "STOCK",
    "quantity": 100,
    "averageCost": 140.00,
    "currentPrice": 142.50
  }'

# 3. Create a buy transaction
curl -X POST http://localhost:8080/api/transaction/transactions \
  -H "Content-Type: application/json" \
  -d "{
    \"portfolioId\": \"$PORTFOLIO_ID\",
    \"accountNumber\": \"ACC99999\",
    \"type\": \"BUY\",
    \"symbol\": \"TSLA\",
    \"assetName\": \"Tesla Inc.\",
    \"quantity\": 50,
    \"price\": 250.00,
    \"currency\": \"USD\",
    \"commission\": 15.00
  }"

# 4. View the updated portfolio
curl http://localhost:8080/api/portfolio/portfolios/$PORTFOLIO_ID

# 5. Check notifications (view logs)
docker-compose logs notification-service | tail -20
```

## Health Checks

Check service health:

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# Portfolio Service
curl http://localhost:8081/api/portfolio/actuator/health

# Transaction Service
curl http://localhost:8082/api/transaction/actuator/health

# Notification Service
curl http://localhost:8083/api/notification/actuator/health
```

## Kafka Events

Monitor Kafka topics:

```bash
# List topics
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consume portfolio events
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic portfolio-events \
  --from-beginning

# Consume transaction events
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic transaction-events \
  --from-beginning
```

## Troubleshooting

### Clear all data and restart

```bash
docker-compose down -v
docker-compose up -d
```

### View service logs

```bash
docker-compose logs -f portfolio-service
docker-compose logs -f transaction-service
docker-compose logs -f notification-service
```
