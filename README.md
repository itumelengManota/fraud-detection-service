# Fraud Detection Service
Domain-Driven Design implementation of the Fraud Detection bounded context using Hexagonal Architecture.
## Architecture
This service implements the Fraud Detection bounded context following DDD principles with Hexagonal Architecture (Ports and Adapters).
### Domain Layer

- Pure business logic without framework dependencies
- Aggregates: RiskAssessment
- Value Objects: RiskScore, Location, Money, etc.
- Domain Services: RiskScoringService, RuleEngineService, GeographicValidator
- Domain Events: RiskAssessmentCompleted, HighRiskDetected

### Application Layer

- Application Services orchestrating domain operations
- DTOs for cross-boundary communication

### Infrastructure Layer

- Adapters implementing domain ports
- Kafka consumer/producer
- REST client for ML service
- JDBC persistence
- Redis caching

## Technology Stack

- Java 25
- Spring Boot 4.0.0
- Spring Data JDBC
- Spring Kafka
- Drools Rule Engine
- Redis + Redisson
- PostgreSQL
- Resilience4j
- Micrometer + OpenTelemetry

## Building and Running

```bash
  ./gradlew clean build
  ./gradlew bootRun
```

## Running Tests

```bash
  ./gradlew test
```
## API Documentation
Swagger UI available at: http://localhost:8080/swagger-ui.html

## Performance Requirements

- Throughput: 100,000 TPS
- Latency: P99 < 100ms
- Availability: 99.99%
