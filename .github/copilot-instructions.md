# Portfolio Management System - Development Guide

## Project Overview
Microservices-based wealth management platform demonstrating enterprise Java development skills.

## Architecture
- **portfolio-service**: Manages client portfolios and holdings (MongoDB + Redis)
- **transaction-service**: Processes transactions with Kafka events
- **notification-service**: Consumes Kafka events for notifications
- **api-gateway**: Spring Cloud Gateway for routing

## Tech Stack
- Java 17, Spring Boot 3.x, Spring Cloud
- Kafka, MongoDB, Redis
- OpenAPI/Swagger, Docker, React

## Development Workflow
1. Use TDD approach with JUnit 5 and Mockito
2. Follow Spring best practices
3. Document APIs with OpenAPI
4. Containerize services with Docker

## Coding Standards
- Use constructor injection for dependencies
- Implement proper exception handling
- Add comprehensive logging
- Write unit and integration tests
- Follow RESTful conventions
