# Portfolio Management System

> **A microservices-based wealth management platform demonstrating enterprise Java development expertise**

[![CI/CD Pipeline](https://github.com/yourusername/pms/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/yourusername/pms/actions/workflows/ci-cd.yml)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Testing](#testing)
- [Deployment](#deployment)
- [Project Structure](#project-structure)

## 🎯 Overview

This Portfolio Management System is a production-ready demonstration of modern microservices architecture designed for wealth management. It showcases enterprise-level Java development skills including:

- **Event-Driven Architecture** with Kafka
- **RESTful API Design** with comprehensive OpenAPI documentation
- **Distributed Caching** using Redis
- **Document Database** integration with MongoDB
- **Cloud-Native Patterns** (Circuit Breaker, API Gateway, Service Discovery)
- **Modern Frontend** with React and TypeScript
- **CI/CD Pipeline** with GitHub Actions
- **Containerization** with Docker

## 🏗️ Architecture

```
┌──────────────────────┐
│   React Frontend     │
│   (TypeScript)       │
│   Port: 3000         │
└──────────┬───────────┘
           │ HTTP/REST
           ▼
┌──────────────────────┐
│   API Gateway        │
│ (Spring Cloud)       │
│   Port: 8080         │
│                      │
│ - Route requests     │
│ - Circuit breaker    │
│ - CORS handling      │
└──────────┬───────────┘
           │
    ┌──────┴──────┬──────────────┐
    │             │              │
    ▼             ▼              ▼
┌─────────┐  ┌─────────┐  ┌─────────┐
│Portfolio│  │Transaction│ │Notification│
│ Service │  │ Service  │  │ Service │
│  :8081  │  │  :8082   │  │  :8083  │
└────┬────┘  └────┬─────┘  └────┬────┘
     │            │              │
     │            │              │
     ▼            ▼              │
┌─────────┐  ┌──────────┐       │
│ MongoDB │  │ MongoDB  │       │
│Portfolio│  │Transaction│      │
│   DB    │  │    DB    │       │
└─────────┘  └──────────┘       │
     │                           │
     ▼                           │
┌─────────┐                      │
│  Redis  │                      │
│ (Cache) │                      │
└─────────┘                      │
                                 │
EVENT-DRIVEN COMMUNICATION       │
                                 │
     │            │              │
     └─────┬──────┘              │
           │ Publish Events      │
           ▼                     │
     ┌─────────────────┐         │
     │  Apache Kafka   │         │
     │   + Zookeeper   │         │
     │                 │         │
     │  Topics:        │         │
     │  - portfolio-   │         │
     │    events       │         │
     │  - transaction- │         │
     │    events       │         │
     └────────┬────────┘         │
              │ Consume Events   │
              └──────────────────┘

Key Data Flow:
1. Frontend → API Gateway → Microservices (REST)
2. Portfolio/Transaction Services → MongoDB (Data persistence)
3. Portfolio Service → Redis (Caching for performance)
4. Portfolio/Transaction Services → Kafka (Publish events)
5. Notification Service ← Kafka (Consume events)
```

## 🛠️ Tech Stack

### Backend
- **Java 17** - Modern LTS version with latest language features
- **Spring Boot 3.2** - Application framework
- **Spring Cloud Gateway** - API Gateway and routing
- **Spring Data MongoDB** - Document database integration
- **Spring Data Redis** - Distributed caching
- **Spring Kafka** - Event streaming platform
- **MapStruct** - Bean mapping
- **Lombok** - Boilerplate reduction

### Data & Messaging
- **MongoDB** - Document database for flexible schema
- **Redis** - In-memory cache for high performance
- **Apache Kafka** - Event streaming and async communication

### Frontend
- **React 18** - Modern UI library
- **TypeScript** - Type-safe JavaScript
- **Vite** - Fast build tool
- **Axios** - HTTP client
- **Recharts** - Data visualization

### Testing
- **JUnit 5** - Unit testing framework
- **Mockito** - Mocking framework
- **MockServer** - API mocking
- **Playwright** - E2E testing

### DevOps
- **Docker** - Containerization
- **Docker Compose** - Multi-container orchestration
- **GitHub Actions** - CI/CD pipeline
- **Maven** - Build automation

## ✨ Features

### Portfolio Management
- ✅ Create and manage client portfolios
- ✅ Track holdings with real-time valuations
- ✅ Calculate unrealized gains/losses
- ✅ Multi-currency support
- ✅ Portfolio status management

### Transaction Processing
- ✅ Buy/Sell transaction execution
- ✅ Automatic transaction processing
- ✅ Commission calculation
- ✅ Transaction history and audit trail
- ✅ Event-driven updates

### Notifications
- ✅ Real-time event consumption
- ✅ Portfolio change notifications
- ✅ Transaction status updates
- ✅ Extensible notification channels

### Technical Features
- ✅ RESTful APIs with OpenAPI 3.0 documentation
- ✅ Redis caching for improved performance
- ✅ Kafka event streaming for async communication
- ✅ Circuit breaker pattern for resilience
- ✅ Comprehensive error handling
- ✅ Structured logging
- ✅ Health checks and monitoring

## 🚀 Getting Started

### Prerequisites

- **Java 17+** - [Download](https://adoptium.net/)
- **Maven 3.8+** - [Download](https://maven.apache.org/download.cgi)
- **Docker & Docker Compose** - [Download](https://www.docker.com/products/docker-desktop)
- **Node.js 20+** (for frontend) - [Download](https://nodejs.org/)

### Option 1: Run with Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/yourusername/portfolio-management-system.git
cd portfolio-management-system

# Build all services
mvn clean install -DskipTests

# Start all services with Docker Compose
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f
```

Services will be available at:
- **API Gateway**: http://localhost:8080
- **Portfolio Service**: http://localhost:8081/api/portfolio/swagger-ui.html
- **Transaction Service**: http://localhost:8082/api/transaction/swagger-ui.html
- **Notification Service**: http://localhost:8083
- **Frontend Dashboard**: http://localhost:3000

### Option 2: Run Services Individually

#### 1. Start Infrastructure Services

```bash
# Start MongoDB, Redis, Kafka
docker-compose up -d mongodb-portfolio mongodb-transaction redis zookeeper kafka
```

#### 2. Start Backend Services

```bash
# Portfolio Service
cd portfolio-service
mvn spring-boot:run

# Transaction Service (in new terminal)
cd transaction-service
mvn spring-boot:run

# Notification Service (in new terminal)
cd notification-service
mvn spring-boot:run

# API Gateway (in new terminal)
cd api-gateway
mvn spring-boot:run
```

#### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

### Quick Test

Create a sample portfolio:

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

Add a holding:

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

Create a transaction:

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
    "commission": 9.99
  }'
```

## 📚 API Documentation

Interactive API documentation is available via Swagger UI:

- **Portfolio Service**: http://localhost:8081/api/portfolio/swagger-ui.html
- **Transaction Service**: http://localhost:8082/api/transaction/swagger-ui.html

### Key Endpoints

#### Portfolio Service
- `POST /api/portfolio/portfolios` - Create portfolio
- `GET /api/portfolio/portfolios/{id}` - Get portfolio by ID
- `GET /api/portfolio/portfolios/client/{clientId}` - Get portfolios by client
- `POST /api/portfolio/portfolios/{id}/holdings` - Add holding
- `PUT /api/portfolio/portfolios/{id}/holdings/{symbol}` - Update holding
- `DELETE /api/portfolio/portfolios/{id}/holdings/{symbol}` - Remove holding

#### Transaction Service
- `POST /api/transaction/transactions` - Create transaction
- `GET /api/transaction/transactions/{id}` - Get transaction by ID
- `GET /api/transaction/transactions/portfolio/{portfolioId}` - Get portfolio transactions
- `PUT /api/transaction/transactions/{id}/cancel` - Cancel transaction

## 🧪 Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

### Generate Test Coverage Report

```bash
mvn jacoco:report
```

Coverage reports will be available in `target/site/jacoco/index.html`

### Run Frontend Tests

```bash
cd frontend
npm run test:e2e
```

### Test Coverage Goals
- Unit test coverage: 80%+
- Integration test coverage: 70%+
- Critical path coverage: 95%+

## 🐳 Deployment

### Build Docker Images

```bash
# Build all images
docker-compose build

# Or build individually
docker build -t portfolio-service:latest ./portfolio-service
docker build -t transaction-service:latest ./transaction-service
docker build -t notification-service:latest ./notification-service
docker build -t api-gateway:latest ./api-gateway
```

### Deploy to Kubernetes (Example)

```bash
# Apply Kubernetes manifests (not included in this demo)
kubectl apply -f k8s/
```

### Environment Variables

Key environment variables for configuration:

```bash
# MongoDB
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/portfolio_db

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Application
SERVER_PORT=8081
```

## 📁 Project Structure

```
portfolio-management-system/
├── .github/
│   ├── copilot-instructions.md
│   └── workflows/
│       └── ci-cd.yml
├── api-gateway/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── portfolio-service/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/.../portfolio/
│   │   │   │   ├── config/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── event/
│   │   │   │   ├── exception/
│   │   │   │   ├── mapper/
│   │   │   │   ├── model/
│   │   │   │   ├── repository/
│   │   │   │   └── service/
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   ├── Dockerfile
│   └── pom.xml
├── transaction-service/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── notification-service/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── services/
│   │   ├── App.tsx
│   │   └── index.tsx
│   ├── tests/
│   ├── package.json
│   └── vite.config.ts
├── docker-compose.yml
├── pom.xml
└── README.md
```

## 🎓 Learning Highlights

This project demonstrates proficiency in:

1. **Microservices Architecture**
   - Service decomposition and bounded contexts
   - Inter-service communication patterns
   - Data consistency in distributed systems

2. **Event-Driven Design**
   - Kafka topic design and partitioning
   - Event sourcing patterns
   - Asynchronous processing

3. **Spring Framework Expertise**
   - Spring Boot auto-configuration
   - Spring Data repositories
   - Spring Cloud Gateway routing

4. **Testing Best Practices**
   - Unit testing with Mockito
   - Integration testing strategies
   - E2E testing with Playwright

5. **DevOps & Cloud-Native**
   - Docker containerization
   - CI/CD pipeline automation
   - Infrastructure as Code

6. **API Design**
   - RESTful conventions
   - OpenAPI specification
   - Versioning strategies

## 🤝 Contributing

This is a demonstration project. For suggestions or improvements:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Your Name**
- LinkedIn: [Your Profile](https://linkedin.com/in/yourprofile)
- Email: your.email@example.com
- Portfolio: [yourportfolio.com](https://yourportfolio.com)

## 🙏 Acknowledgments

Built as a technical demonstration for wealth management industry applications, showcasing production-ready code and enterprise development best practices.

---

**⭐ If you find this project useful, please consider giving it a star!**
