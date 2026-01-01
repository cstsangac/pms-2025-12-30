# Fly.io Quick Start

## 1. Install Fly CLI
```powershell
powershell -c "iwr https://fly.io/install.ps1 -useb | iex"
flyctl auth login
```

## 2. Set Up Free External Services

### MongoDB Atlas (5 minutes)
1. https://cloud.mongodb.com → Create M0 Free cluster (Hong Kong)
2. Create user: `pmsuser` / `<your-password>`
3. Network: Allow 0.0.0.0/0
4. Get URI: `mongodb+srv://pmsuser:<password>@cluster0.xxxxx.mongodb.net/`

### Upstash Redis + Kafka (3 minutes)
1. https://console.upstash.com → Create Redis (Hong Kong)
2. Kafka → Create cluster → Create topic `transaction-events`
3. Copy credentials (host, port, password, SASL)

## 3. Deploy All Services (10 minutes)

```powershell
# API Gateway
cd api-gateway
flyctl launch --no-deploy --name pms-api-gateway --region hkg
flyctl deploy
cd ..

# Portfolio Service
cd portfolio-service
flyctl launch --no-deploy --name pms-portfolio-service --region hkg
flyctl secrets set `
  MONGODB_URI="mongodb+srv://pmsuser:<password>@cluster0.xxxxx.mongodb.net/portfolio_db" `
  REDIS_HOST="xxxxx.upstash.io" `
  REDIS_PASSWORD="<redis-password>" `
  KAFKA_BOOTSTRAP_SERVERS="xxxxx.upstash.io:9092" `
  KAFKA_USERNAME="<kafka-user>" `
  KAFKA_PASSWORD="<kafka-pass>"
flyctl deploy
cd ..

# Transaction Service
cd transaction-service
flyctl launch --no-deploy --name pms-transaction-service --region hkg
flyctl secrets set `
  MONGODB_URI="mongodb+srv://pmsuser:<password>@cluster0.xxxxx.mongodb.net/transaction_db" `
  KAFKA_BOOTSTRAP_SERVERS="xxxxx.upstash.io:9092" `
  KAFKA_USERNAME="<kafka-user>" `
  KAFKA_PASSWORD="<kafka-pass>" `
  PORTFOLIO_SERVICE_URL="https://pms-portfolio-service.fly.dev"
flyctl deploy
cd ..

# Notification Service
cd notification-service
flyctl launch --no-deploy --name pms-notification-service --region hkg
flyctl secrets set `
  KAFKA_BOOTSTRAP_SERVERS="xxxxx.upstash.io:9092" `
  KAFKA_USERNAME="<kafka-user>" `
  KAFKA_PASSWORD="<kafka-pass>"
flyctl deploy
cd ..

# Frontend
cd frontend
flyctl launch --no-deploy --name pms-frontend --region hkg
flyctl secrets set VITE_API_BASE_URL="https://pms-api-gateway.fly.dev"
flyctl deploy
cd ..
```

## 4. Access Your App
- **Frontend**: https://pms-frontend.fly.dev
- **API**: https://pms-api-gateway.fly.dev

## Cost: $0-5/month
- Fly.io: 3 free apps + $0-5 for extras
- MongoDB Atlas: FREE (512MB)
- Upstash Redis: FREE (256MB)
- Upstash Kafka: FREE (10K msgs/day)

## Troubleshooting
```powershell
# View logs
flyctl logs -a pms-portfolio-service

# Check status
flyctl status -a pms-frontend

# SSH into machine
flyctl ssh console -a pms-portfolio-service
```

See [FLY-IO-DEPLOYMENT.md](FLY-IO-DEPLOYMENT.md) for complete guide.
