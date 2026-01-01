# Fly.io Deployment Guide

## Overview
Deploy the Portfolio Management System to Fly.io for **~$0-10/month** 24/7 hosting.

## Architecture
- **Fly.io** (Free tier): 3 microservices + frontend + API gateway
- **MongoDB Atlas** (Free tier): 512MB for portfolio & transaction DBs
- **Upstash Redis** (Free tier): 256MB for caching
- **Upstash Kafka** (Free tier): 10K messages/day for events

## Prerequisites
1. [Fly.io account](https://fly.io/app/sign-up) (no credit card required for free tier)
2. [MongoDB Atlas account](https://www.mongodb.com/cloud/atlas/register) (free)
3. [Upstash account](https://upstash.com/) (free for Redis + Kafka)
4. Install Fly CLI: `powershell -c "iwr https://fly.io/install.ps1 -useb | iex"`

## Step 1: Set Up External Services

### MongoDB Atlas (Free 512MB)
```bash
1. Go to https://cloud.mongodb.com/
2. Create new cluster → M0 Free tier → Hong Kong (ap-east-1)
3. Create database user: pmsuser / <password>
4. Network Access → Add IP: 0.0.0.0/0 (allow from anywhere)
5. Get connection string:
   mongodb+srv://pmsuser:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

**Create two databases:**
- `portfolio_db` (for portfolio-service)
- `transaction_db` (for transaction-service)

### Upstash Redis (Free 256MB)
```bash
1. Go to https://console.upstash.com/
2. Create Redis database → Global → Hong Kong region
3. Copy connection details:
   - REDIS_HOST: xxxxx.upstash.io
   - REDIS_PORT: 6379
   - REDIS_PASSWORD: <password>
```

### Upstash Kafka (Free 10K msgs/day)
```bash
1. Go to https://console.upstash.com/kafka
2. Create Kafka cluster → Global → ap-southeast-1
3. Create topic: transaction-events (partitions: 1, retention: 7 days)
4. Copy connection details:
   - KAFKA_BOOTSTRAP_SERVERS: xxxxx.upstash.io:9092
   - KAFKA_USERNAME: <username>
   - KAFKA_PASSWORD: <password>
   - KAFKA_SASL_MECHANISM: SCRAM-SHA-256
```

## Step 2: Deploy Services

### Login to Fly.io
```powershell
flyctl auth login
```

### Deploy API Gateway
```powershell
cd api-gateway
flyctl launch --no-deploy
flyctl deploy
cd ..
```

### Deploy Portfolio Service
```powershell
cd portfolio-service
flyctl launch --no-deploy

# Set secrets
flyctl secrets set `
  MONGODB_URI="mongodb+srv://pmsuser:<password>@cluster0.xxxxx.mongodb.net/portfolio_db?retryWrites=true&w=majority" `
  REDIS_HOST="xxxxx.upstash.io" `
  REDIS_PORT="6379" `
  REDIS_PASSWORD="<password>" `
  KAFKA_BOOTSTRAP_SERVERS="xxxxx.upstash.io:9092" `
  KAFKA_USERNAME="<username>" `
  KAFKA_PASSWORD="<password>"

flyctl deploy
cd ..
```

### Deploy Transaction Service
```powershell
cd transaction-service
flyctl launch --no-deploy

# Set secrets
flyctl secrets set `
  MONGODB_URI="mongodb+srv://pmsuser:<password>@cluster0.xxxxx.mongodb.net/transaction_db?retryWrites=true&w=majority" `
  KAFKA_BOOTSTRAP_SERVERS="xxxxx.upstash.io:9092" `
  KAFKA_USERNAME="<username>" `
  KAFKA_PASSWORD="<password>" `
  PORTFOLIO_SERVICE_URL="https://pms-portfolio-service.fly.dev"

flyctl deploy
cd ..
```

### Deploy Notification Service
```powershell
cd notification-service
flyctl launch --no-deploy

# Set secrets
flyctl secrets set `
  KAFKA_BOOTSTRAP_SERVERS="xxxxx.upstash.io:9092" `
  KAFKA_USERNAME="<username>" `
  KAFKA_PASSWORD="<password>"

flyctl deploy
cd ..
```

### Deploy Frontend
```powershell
cd frontend
flyctl launch --no-deploy

# Set build-time environment variable
flyctl secrets set VITE_API_BASE_URL="https://pms-api-gateway.fly.dev"

flyctl deploy
cd ..
```

## Step 3: Update Spring Boot Application Properties

Create production profiles for each service to use environment variables.

### portfolio-service/src/main/resources/application-prod.yml
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
    password: ${REDIS_PASSWORD}
    ssl: true
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: org.apache.kafka.common.security.scram.ScramLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";
```

### transaction-service/src/main/resources/application-prod.yml
```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: org.apache.kafka.common.security.scram.ScramLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";

portfolio:
  service:
    url: ${PORTFOLIO_SERVICE_URL}
```

### notification-service/src/main/resources/application-prod.yml
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: org.apache.kafka.common.security.scram.ScramLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";
```

## Step 4: Configure API Gateway Routes

Update `api-gateway/src/main/resources/application.yml` to use Fly.io internal URLs:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: portfolio-service
          uri: https://pms-portfolio-service.fly.dev
          predicates:
            - Path=/api/portfolio/**
          filters:
            - StripPrefix=2

        - id: transaction-service
          uri: https://pms-transaction-service.fly.dev
          predicates:
            - Path=/api/transaction/**
          filters:
            - StripPrefix=2
```

## Step 5: Update Frontend API URL

Update `frontend/src/services/api.ts`:

```typescript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
```

## Step 6: Verify Deployment

```powershell
# Check all apps
flyctl apps list

# Check app status
flyctl status -a pms-api-gateway
flyctl status -a pms-portfolio-service
flyctl status -a pms-transaction-service
flyctl status -a pms-notification-service
flyctl status -a pms-frontend

# View logs
flyctl logs -a pms-portfolio-service

# Open frontend
flyctl open -a pms-frontend
```

## Step 7: Test the System

1. **Frontend**: https://pms-frontend.fly.dev
2. **API Gateway**: https://pms-api-gateway.fly.dev
3. **Create portfolio** via demo button
4. **Verify Kafka events** in Upstash console
5. **Check MongoDB** data in Atlas console
6. **Monitor Redis** cache in Upstash console

## Cost Breakdown (Monthly)

| Service | Free Tier | Expected Cost |
|---------|-----------|---------------|
| Fly.io (5 apps × 256-512MB) | 3 apps free | $0-5 |
| MongoDB Atlas M0 | 512MB free | $0 |
| Upstash Redis | 256MB free | $0 |
| Upstash Kafka | 10K msgs/day | $0 |
| **Total** | | **$0-5/month** |

## Scaling Commands

```powershell
# Scale portfolio service to 2 instances
flyctl scale count 2 -a pms-portfolio-service

# Scale memory
flyctl scale memory 1024 -a pms-portfolio-service

# Auto-scale based on load
flyctl autoscale set min=1 max=3 -a pms-portfolio-service
```

## Monitoring

```powershell
# Live logs
flyctl logs -a pms-portfolio-service --follow

# Metrics dashboard
flyctl dashboard -a pms-portfolio-service

# SSH into machine
flyctl ssh console -a pms-portfolio-service
```

## Troubleshooting

### Services can't connect to MongoDB
- Check IP whitelist in Atlas (should be 0.0.0.0/0)
- Verify connection string includes database name
- Check secrets: `flyctl secrets list -a pms-portfolio-service`

### Kafka consumer not receiving events
- Verify topic exists in Upstash console
- Check SASL credentials match
- Increase consumer timeout in application.yml

### Frontend can't reach backend
- Verify CORS configuration in Spring Boot
- Check API Gateway routes
- Test direct service URL: `curl https://pms-portfolio-service.fly.dev/actuator/health`

### Out of memory errors
- Scale memory: `flyctl scale memory 512 -a pms-portfolio-service`
- Check Java heap settings in Dockerfile

## Update Deployment

```powershell
# After code changes
cd portfolio-service
mvn clean package -DskipTests
flyctl deploy
cd ..

# Frontend updates
cd frontend
npm run build
flyctl deploy
cd ..
```

## Pause/Resume Services (Save costs)

```powershell
# Suspend all apps
flyctl apps list | ForEach-Object { flyctl suspend -a $_.Name }

# Resume before interview
flyctl resume -a pms-api-gateway
flyctl resume -a pms-portfolio-service
flyctl resume -a pms-transaction-service
flyctl resume -a pms-notification-service
flyctl resume -a pms-frontend
```

## Production Checklist

- [ ] MongoDB indexes created for performance
- [ ] Redis cache TTL configured
- [ ] Kafka retention policy set
- [ ] Health checks passing
- [ ] CORS configured for frontend domain
- [ ] Secrets rotation documented
- [ ] Backup strategy for MongoDB
- [ ] Monitoring alerts configured
- [ ] SSL certificates auto-renewed (Fly.io handles this)

## Resume Links

Add to your resume/portfolio:
- **Live Demo**: https://pms-frontend.fly.dev
- **Source Code**: https://github.com/cstsangac/pms-2025-12-30
- **Architecture**: Event-driven microservices (Spring Boot, Kafka, MongoDB, Redis)
