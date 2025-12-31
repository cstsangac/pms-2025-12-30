# Interview Prep Checklist - 7-Day Plan

## Day 1: Architecture & Design Patterns
- ✅ Review [05-API-GATEWAY-GUIDE.md](guides/05-API-GATEWAY-GUIDE.md)
- ✅ Study microservices architecture diagram
- ✅ Practice explaining: "Walk me through your system architecture"
- ✅ Review design decisions and trade-offs

## Day 2: Core Java & Spring Framework
- ✅ Review [09-SPRING-BOOT-ESSENTIALS.md](guides/09-SPRING-BOOT-ESSENTIALS.md)
- ✅ Study dependency injection examples in your code
- ✅ Review `@Transactional`, `@Cacheable`, `@Component` usage
- ✅ Practice: "Explain how Spring Boot auto-configuration works"

## Day 3: Event-Driven Architecture
- ✅ Deep dive [02-KAFKA-EVENT-DRIVEN-GUIDE.md](guides/02-KAFKA-EVENT-DRIVEN-GUIDE.md)
- ✅ Understand producer/consumer patterns in your code
- ✅ Review event schemas and topic strategies
- ✅ Practice: "How do you ensure message ordering in Kafka?"

## Day 4: Data Layer & Caching
- ✅ Study [06-MONGODB-INTEGRATION-GUIDE.md](guides/06-MONGODB-INTEGRATION-GUIDE.md)
- ✅ Review [03-REDIS-CACHING-GUIDE.md](guides/03-REDIS-CACHING-GUIDE.md)
- ✅ Understand cache eviction strategy
- ✅ Practice: "MongoDB vs PostgreSQL - when to use each?"

## Day 5: Testing & Quality
- ✅ Review [04-TESTING-STRATEGY-GUIDE.md](guides/04-TESTING-STRATEGY-GUIDE.md)
- ✅ Study unit tests with Mockito
- ✅ Review integration tests with Testcontainers
- ✅ Practice: "Explain your testing pyramid"

## Day 6: Demo Preparation
- ✅ Test complete demo flow 3 times
- ✅ Review [07-INTERVIEW-SCENARIOS-GUIDE.md](guides/07-INTERVIEW-SCENARIOS-GUIDE.md) Demo Script
- ✅ Prepare backup plan (screenshots, code walkthrough)
- ✅ Practice 10-minute presentation out loud

## Day 7: Final Review
- ✅ Review QUICK-REFERENCE.md for key concepts
- ✅ Practice common pitfalls to avoid
- ✅ Prepare questions to ask interviewer
- ✅ Test all Docker services one final time

---

## Pre-Interview Checklist (Day of Interview)

### Technical Setup (30 min before)
- [ ] Start Docker services: `docker-compose up -d`
- [ ] Verify all services running: `docker-compose ps`
- [ ] Test API endpoints with curl/Postman
- [ ] Clear old test data from MongoDB
- [ ] Have QUICK-REFERENCE.md open in VS Code

### Materials Ready
- [ ] Resume with this project highlighted
- [ ] Architecture diagram printed/ready to share screen
- [ ] Code repository open in VS Code
- [ ] Notepad for taking notes during interview

### Mental Prep
- [ ] Review talking points for 2-year gap
- [ ] Prepare 3 questions about the role/team
- [ ] Remember: Honesty > Pretending to know everything

---

## Common Interview Questions to Practice

### Technical
1. "Walk me through your system architecture"
2. "Why did you choose microservices over a monolith?"
3. "How do you handle data consistency across services?"
4. "Explain your caching strategy"
5. "How would you scale this to 1 million users?"

### Behavioral
1. "Tell me about a complex technical problem you solved"
2. "How did you approach learning these new technologies after your break?"
3. "Describe a time you had to make a difficult trade-off"

### Coding
1. Be ready to explain any code file on the spot
2. Walk through a transaction state machine
3. Explain Spring dependency injection with examples

---

## Post-Interview
- [ ] Send thank-you email within 24 hours
- [ ] Note questions you struggled with for improvement
- [ ] Follow up on any technical questions you couldn't answer
- [ ] Reflect on what went well

---

## Emergency Contacts
- Portfolio Service logs: `docker logs pms-portfolio-service`
- Stop all services: `docker-compose down`
- Restart everything: `docker-compose up -d --force-recreate`
