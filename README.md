# Fraud Detection Service - Development Guide

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Design Patterns & Principles](#design-patterns--principles)
- [Business Logic](#business-logic)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Authentication & Authorization](#authentication--authorization)
- [API Reference](#api-reference)
- [Testing with cURL](#testing-with-curl)
- [Testing with Postman](#testing-with-postman)
- [CI/CD Pipeline](#cicd-pipeline)
- [Infrastructure Management](#infrastructure-management)

---

## Overview

The Fraud Detection Service is an enterprise-grade, real-time fraud detection system designed to process 100,000+ transactions per second with sub-100ms latency. It combines machine learning predictions, rule-based evaluation, velocity checks, and geographic validation to assess transaction risk and make automated decisions.

### Key Features

- **Real-time Risk Assessment**: Sub-100ms P99 latency for transaction evaluation
- **Hybrid Scoring**: Combines ML predictions (60%) with business rules (40%)
- **Velocity Tracking**: Redis-backed counters for transaction frequency analysis
- **Geographic Validation**: Impossible travel detection using Haversine distance calculations
- **Event-Driven Architecture**: Kafka-based event streaming with Avro serialization
- **Idempotency**: Redis-backed deduplication for exactly-once processing
- **Multi-Architecture Support**: Docker images for AMD64 and ARM64
- **OAuth2 Security**: Keycloak-based authentication and authorization
- **Observability**: OpenTelemetry tracing, Prometheus metrics, health checks

---

## Architecture

### Architecture Styles

#### 1. **Hexagonal Architecture (Ports and Adapters)**
The application is structured with a clear separation between business logic and external concerns:

```
┌────────────────────────────────────────────────────────────┐
│                     Infrastructure Layer                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Inbound Adapters (Driving)              │  │
│  │  - REST Controller (FraudDetectionController)        │  │
│  │  - Kafka Consumer (TransactionEventConsumer)         │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ↓↑                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                 Application Layer                    │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │           Input Ports (Use Cases)              │  │  │
│  │  │  - AssessTransactionRiskUseCase                │  │  │
│  │  │  - GetRiskAssessmentUseCase                    │  │  │
│  │  │  - FindRiskLeveledAssessmentsUseCase           │  │  │
│  │  │  - ProcessTransactionUseCase                   │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │                       ↓↑                             │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │        Application Services (Orchestration)    │  │  │
│  │  │  - FraudDetectionApplicationService            │  │  │
│  │  │  - ProcessTransactionApplicationService        │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ↓↑                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   Domain Layer                       │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │  Aggregates:                                   │  │  │
│  │  │  - RiskAssessment (root)                       │  │  │
│  │  │                                                │  │  │
│  │  │  Value Objects:                                │  │  │
│  │  │  - Transaction, RiskScore, Decision, etc.      │  │  │
│  │  │                                                │  │  │
│  │  │  Domain Services:                              │  │  │
│  │  │  - RiskScoringService                          │  │  │
│  │  │  - DecisionService                             │  │  │
│  │  │  - RuleEngineService                           │  │  │
│  │  │  - GeographicValidator                         │  │  │
│  │  │                                                │  │  │
│  │  │  Domain Events:                                │  │  │
│  │  │  - RiskAssessmentCompleted                     │  │  │
│  │  │  - HighRiskDetected                            │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ↓↑                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             Output Ports (Interfaces)                │  │
│  │  - RiskAssessmentRepository                          │  │
│  │  - TransactionRepository                             │  │
│  │  - EventPublisherPort                                │  │
│  │  - MLServicePort                                     │  │
│  │  - VelocityServicePort                               │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ↓↑                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             Outbound Adapters (Driven)               │  │
│  │  - JDBC Repositories (PostgreSQL)                    │  │
│  │  - Kafka Producer (Event Publishing)                 │  │
│  │  - Redis Cache (Velocity Counters)                   │  │
│  │  - SageMaker Client (ML Predictions)                 │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

**Locations:**
- **Ports**: `application.port.in.*` (input ports), `application.port.out.*` (output ports)
- **Adapters**: `infrastructure.adapter.*` (REST, Kafka, Persistence, ML, Cache)
- **Domain**: `domain.*` (pure business logic, no framework dependencies)

#### 2. **Domain-Driven Design (DDD)**

The application implements tactical and strategic DDD patterns:

**Tactical Patterns:**
- **Aggregate Root**: `RiskAssessment` - consistency boundary for risk assessment operations
- **Value Objects**: `Transaction`, `RiskScore`, `Location`, `Money`, `Decision`, etc. - immutable domain concepts
- **Domain Services**: Complex business logic that doesn't belong to a single entity
- **Domain Events**: `RiskAssessmentCompleted`, `HighRiskDetected` - capture significant business occurrences
- **Repositories**: Persistence abstractions (`RiskAssessmentRepository`, `TransactionRepository`)

**Strategic Patterns:**
- **Bounded Context**: Fraud Detection context with clear boundaries
- **Anti-Corruption Layer**: Adapters translate between domain and external systems
- **Context Mapping**: Integration with other contexts (Transaction Processing, Notification, etc.)

**Locations:**
- **Aggregates**: `domain.aggregate.RiskAssessment`
- **Value Objects**: `domain.valueobject.*`
- **Domain Services**: `domain.service.*`
- **Domain Events**: `domain.event.*`
- **Repositories**: `application.port.out.*Repository`

#### 3. **Event-Driven Architecture**

Asynchronous communication via domain events and Kafka:

```
Transaction Event → Kafka Consumer → Process Transaction Use Case
                                    ↓
                          Assess Risk → Publish Events
                                    ↓
                    ┌───────────────┴───────────────┐
                    ↓                               ↓
        RiskAssessmentCompleted           HighRiskDetected
                    ↓                               ↓
  fraud-detection.risk-assessments   fraud-detection.high-risk-alerts
```

**Locations:**
- **Event Publishing**: `infrastructure.adapter.kafka.EventPublisherAdapter`
- **Event Consumption**: `infrastructure.adapter.kafka.TransactionEventConsumer`
- **Domain Events**: `domain.event.*`

#### 4. **Layered Architecture**

```
Presentation Layer  → REST Controllers, Kafka Consumers
Application Layer   → Use Cases, Application Services, DTOs
Domain Layer        → Aggregates, Value Objects, Domain Services
Infrastructure Layer → Adapters, Repositories, External Integrations
```

#### 5. **CQRS (Command Query Responsibility Segregation)**

Separation of command and query operations:

**Commands:**
- `AssessTransactionRiskCommand` → Creates/modifies risk assessments
- `ProcessTransactionCommand` → Processes incoming transactions

**Queries:**
- `GetRiskAssessmentQuery` → Retrieves single assessment
- `FindRiskLeveledAssessmentsQuery` → Searches assessments by criteria

**Locations:**
- **Commands**: `application.port.in.command.*`
- **Queries**: `application.port.in.query.*`
- **Command Handlers**: `application.service.*ApplicationService`

---

## Design Patterns & Principles

### Design Patterns

#### 1. **Strategy Pattern**
Decision-making logic varies by risk level using interchangeable strategies.

**Location**: `domain.service.*Strategy`
```java
public interface DecisionStrategy {
    TransactionRiskLevel getRiskLevel();
    Decision decide(RiskAssessment assessment);
}

// Implementations:
- CriticalRiskStrategy → BLOCK
- HighRiskStrategy → REVIEW
- MediumRiskStrategy → CHALLENGE
- LowRiskStrategy → ALLOW
```

#### 2. **Repository Pattern**
Abstracts data persistence from business logic.

**Locations:**
- **Interfaces**: `application.port.out.*Repository`
- **Implementations**: `infrastructure.adapter.persistence.*RepositoryAdapter`

```java
// Port (Interface)
public interface RiskAssessmentRepository {
    RiskAssessment save(RiskAssessment assessment);
    Optional<RiskAssessment> findByTransactionId(TransactionId id);
}

// Adapter (Implementation)
@Component
public class RiskAssessmentRepositoryAdapter implements RiskAssessmentRepository {
    // JDBC implementation
}
```

#### 3. **Adapter Pattern**
Translates between domain and external systems.

**Locations**: `infrastructure.adapter.*`
- **Kafka Adapter**: `EventPublisherAdapter`, `TransactionEventConsumer`
- **ML Adapter**: `SageMakerMLAdapter`
- **Cache Adapter**: `VelocityCounterAdapter`
- **Persistence Adapter**: `*RepositoryAdapter`

#### 4. **Mapper Pattern**
Converts between domain objects and DTOs/entities.

**Locations**: `infrastructure.adapter.persistence.mapper.*`
```java
@Mapper(componentModel = "spring")
public interface RiskAssessmentMapper {
    RiskAssessmentEntity toEntity(RiskAssessment domain);
    RiskAssessment toDomain(RiskAssessmentEntity entity);
}
```

#### 5. **Builder Pattern**
Constructs complex objects step-by-step.

**Locations**: Used throughout value objects and commands
```java
Transaction transaction = Transaction.builder()
    .id(TransactionId.of(uuid))
    .amount(Money.of(amount, currency))
    .type(TransactionType.PURCHASE)
    .build();
```

#### 6. **Factory Pattern**
Creates domain objects with specific logic.

**Locations**: `domain.valueobject.*`
```java
public static TransactionId generate() {
    return new TransactionId(Generators.timeBasedEpochGenerator().generate());
}
```

#### 7. **Circuit Breaker Pattern**
Protects against cascading failures in external service calls.

**Location**: `infrastructure.adapter.ml.SageMakerMLAdapter`
```java
@ConditionalOnProperty(name = "aws.sagemaker.enabled")
public class SageMakerMLAdapter implements MLServicePort {
    private final CircuitBreaker circuitBreaker;
    
    public MLPrediction predict(Transaction transaction) {
        return circuitBreaker.executeSupplier(() -> {
            // SageMaker invocation
        });
    }
}
```

**Configuration**: `application.yml` - `resilience4j.circuitbreaker`

#### 8. **Template Method Pattern**
Defines algorithm skeleton with customizable steps.

**Location**: Used in domain services for risk assessment flow

#### 9. **Observer Pattern**
Domain events notify interested parties of state changes.

**Locations**:
- **Events**: `domain.event.*`
- **Publishing**: `application.port.out.EventPublisherPort`

### SOLID Principles

#### 1. **Single Responsibility Principle (SRP)**
Each class has one reason to change.

**Examples:**
- `RiskScoringService` - Only calculates risk scores
- `DecisionService` - Only makes decisions based on risk levels
- `GeographicValidator` - Only validates geographic impossibility
- `VelocityCounterAdapter` - Only manages velocity counters

#### 2. **Open/Closed Principle (OCP)**
Open for extension, closed for modification.

**Examples:**
- `DecisionStrategy` interface - New risk strategies can be added without modifying existing code
- Hexagonal architecture ports - New adapters can be added without changing business logic

#### 3. **Liskov Substitution Principle (LSP)**
Subtypes must be substitutable for their base types.

**Examples:**
- All `DecisionStrategy` implementations are interchangeable
- Repository implementations can be swapped without affecting business logic

#### 4. **Interface Segregation Principle (ISP)**
Clients shouldn't depend on interfaces they don't use.

**Examples:**
- Separate use case interfaces (`AssessTransactionRiskUseCase`, `GetRiskAssessmentUseCase`)
- Focused repository interfaces with minimal methods

#### 5. **Dependency Inversion Principle (DIP)**
High-level modules don't depend on low-level modules; both depend on abstractions.

**Examples:**
- Application services depend on port interfaces, not concrete adapters
- Domain services depend on repository interfaces, not JDBC implementations

### Additional Principles

#### Domain-Driven Design Principles
- **Ubiquitous Language**: Domain terminology used throughout codebase
- **Bounded Context**: Clear boundaries for the Fraud Detection context
- **Aggregate Consistency**: `RiskAssessment` maintains invariants
- **Domain Events**: Capture business-significant occurrences

#### Clean Architecture Principles
- **Dependency Rule**: Dependencies point inward toward domain
- **Framework Independence**: Domain layer has no framework dependencies
- **Testability**: Business logic isolated and easily testable

---

## Business Logic

### Business Rules

#### 1. **Risk Scoring Rules**

**Composite Score Calculation:**
```
Final Score = (ML Score × 0.6) + (Rule Score × 0.4)
```

**Risk Level Thresholds:**
```java
Score 0-40    → LOW
Score 41-70   → MEDIUM
Score 71-90   → HIGH
Score 91-100  → CRITICAL
```

**Location**: `domain.service.RiskScoringService.calculateCompositeScore()`

#### 2. **Amount-Based Rules**

| Rule ID | Threshold | Severity | Points |
|---------|-----------|----------|--------|
| LARGE_AMOUNT | > $10,000 | MEDIUM | 25 |
| VERY_LARGE_AMOUNT | > $50,000 | HIGH | 40 |
| EXCESSIVELY_LARGE_AMOUNT | > $100,000 | CRITICAL | 60 |

**Location**: `resources/rules/amount-rules.drl`

#### 3. **Velocity Rules**

| Rule ID | Window | Threshold | Severity | Points |
|---------|--------|-----------|----------|--------|
| VELOCITY_5MIN | 5 minutes | > 5 transactions | MEDIUM | 25 |
| VELOCITY_1HOUR | 1 hour | > 20 transactions | HIGH | 40 |
| VELOCITY_24HOURS | 24 hours | > 80 transactions | CRITICAL | 60 |

**Location**: `resources/rules/velocity-rules.drl`

#### 4. **Geographic Rules**

**Impossible Travel Detection:**
```java
Required Speed = Distance (km) / Time (hours)
If Required Speed > 965 km/h (jet cruising speed) → CRITICAL (60 points)
```

**Calculation Method**: Haversine formula for great-circle distance

**Location**:
- Rule: `resources/rules/geographic-rules.drl`
- Logic: `domain.service.GeographicValidator`
- Distance: `domain.valueobject.Location.distanceFrom()`

#### 5. **Decision Rules**

| Risk Level | Decision | Description |
|------------|----------|-------------|
| LOW | ALLOW | Proceed with transaction |
| MEDIUM | CHALLENGE | Request additional authentication (2FA, OTP) |
| HIGH | REVIEW | Manual review by fraud analyst |
| CRITICAL | BLOCK | Immediate transaction rejection |

**Location**: `domain.service.*Strategy` classes

### Use Cases

#### 1. **Assess Transaction Risk**
**Interface**: `AssessTransactionRiskUseCase`  
**Implementation**: `FraudDetectionApplicationService.assess()`

**Flow:**
```
1. Receive AssessTransactionRiskCommand
2. Convert to Transaction domain object
3. Fetch velocity metrics from Redis
4. Validate geographic context
5. Invoke ML service (SageMaker)
6. Execute Drools rule engine
7. Calculate composite risk score
8. Determine risk level
9. Make decision based on strategy
10. Save risk assessment
11. Publish domain events
12. Increment velocity counters
13. Return RiskAssessmentDto
```

**Invariants Enforced:**
- CRITICAL risk must result in BLOCK decision
- LOW risk cannot result in BLOCK decision
- Risk score must be 0-100

**Location**: `application.service.FraudDetectionApplicationService.assess()`

#### 2. **Get Risk Assessment**
**Interface**: `GetRiskAssessmentUseCase`  
**Implementation**: `FraudDetectionApplicationService.get()`

**Flow:**
```
1. Receive GetRiskAssessmentQuery with transactionId
2. Query repository by transactionId
3. Convert to DTO
4. Return RiskAssessmentDto or throw RiskAssessmentNotFoundException
```

**Location**: `application.service.FraudDetectionApplicationService.get()`

#### 3. **Find Risk-Leveled Assessments**
**Interface**: `FindRiskLeveledAssessmentsUseCase`  
**Implementation**: `FraudDetectionApplicationService.find()`

**Flow:**
```
1. Receive FindRiskLeveledAssessmentsQuery with:
   - transactionRiskLevels (optional)
   - fromDate (optional)
   - PageRequestQuery (pageNumber, pageSize, sortBy, sortDirection)
2. Query repository with filters and pagination
3. Convert to PagedResultDto
4. Return paginated results
```

**Query Behavior:**
- Empty risk levels → Returns all levels
- Null fromDate → No time filtering
- Default sort: assessmentTime DESC

**Location**: `application.service.FraudDetectionApplicationService.find()`

#### 4. **Process Transaction**
**Interface**: `ProcessTransactionUseCase`  
**Implementation**: `ProcessTransactionApplicationService.process()`

**Flow:**
```
1. Receive ProcessTransactionCommand from Kafka
2. Check idempotency via SeenMessageCache (Redis)
3. If duplicate → Skip and acknowledge
4. Save transaction to repository
5. Invoke AssessTransactionRiskUseCase
6. Mark as processed in cache (TTL: 10 minutes)
7. Acknowledge Kafka message
```

**Idempotency:**
- Redis key: `seen:transaction:{transactionId}`
- TTL: 10 minutes (configurable)
- Prevents duplicate processing

**Location**: `application.service.ProcessTransactionApplicationService.process()`

### Invariants

#### Aggregate Invariants

**RiskAssessment Invariants:**
```java
// 1. CRITICAL risk must be BLOCKED
if (hasCriticalRisk() && decision.isProceed())
    throw new InvariantViolationException("Critical risk must result in BLOCK decision");

// 2. LOW risk cannot be BLOCKED
if (hasLowRisk() && decision.isBlocked())
    throw new InvariantViolationException("Low risk cannot result in BLOCK decision");

// 3. Risk score must be 0-100
public record RiskScore(@Min(0) @Max(100) int value) {}
```

**Location**: `domain.aggregate.RiskAssessment.validateDecisionAlignment()`

#### Value Object Invariants

**Money Invariants:**
```java
// Value and currency cannot be null
public record Money(
    @NotNull BigDecimal value,
    @NotNull Currency currency
) {}
```

**Location**: `domain.valueobject.Money`

**MLPrediction Invariants:**
```java
// Probabilities must be 0.0-1.0
public record MLPrediction(
    @DecimalMin("0.0") @DecimalMax("1.0") double fraudProbability,
    @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
    ...
) {}
```

**Location**: `domain.valueobject.MLPrediction`

### Domain Events

#### 1. **RiskAssessmentCompleted**
Published when risk assessment finishes successfully.

**Payload:**
```java
{
  "id": "transaction-id",
  "assessmentId": "assessment-id",
  "finalScore": 75,
  "riskLevel": "HIGH",
  "decision": "REVIEW",
  "occurredAt": 1703001234567
}
```

**Topic**: `fraud-detection.risk-assessments`

**Location**: `domain.event.RiskAssessmentCompleted`

#### 2. **HighRiskDetected**
Published when HIGH or CRITICAL risk is detected.

**Payload:**
```java
{
  "id": "transaction-id",
  "assessmentId": "assessment-id",
  "riskLevel": "CRITICAL",
  "occurredAt": 1703001234567
}
```

**Topic**: `fraud-detection.high-risk-alerts`

**Location**: `domain.event.HighRiskDetected`

---

## Technology Stack

### Core Technologies

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Java** | 25 | Programming Language | Latest LTS features, Virtual Threads for high concurrency |
| **Spring Boot** | 3.5.8 | Application Framework | Mature ecosystem, auto-configuration, production-ready features |
| **PostgreSQL** | Latest | Relational Database | ACID compliance, JSONB support, excellent performance |
| **Redis** | Latest | In-Memory Cache | Millisecond latency, atomic operations for velocity counters |
| **Apache Kafka** | Latest | Event Streaming | High-throughput, durable message queue, exactly-once semantics |
| **Drools** | 10.1.0 | Rule Engine | Externalized business rules, declarative DSL, dynamic rule updates |

### Infrastructure & DevOps

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Docker** | Latest | Containerization | Consistent environments, easy deployment |
| **Docker Compose** | Latest | Local Development | Multi-container orchestration for dev/test |
| **GitHub Actions** | N/A | CI/CD Pipeline | Native GitHub integration, workflow automation |
| **Testcontainers** | Latest | Integration Testing | Real dependencies in tests, reproducible test environments |

### Observability & Monitoring

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Micrometer** | Latest | Metrics | Vendor-neutral metrics facade |
| **Prometheus** | Latest | Metrics Storage | Time-series database, powerful queries, alerting |
| **OpenTelemetry** | Latest | Distributed Tracing | Trace requests across services, performance analysis |
| **Zipkin** | Latest | Trace Visualization | UI for viewing distributed traces |

### Security

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Keycloak** | 26.0.7 | Identity Provider | OAuth2/OIDC, fine-grained authorization, user management |
| **Spring Security** | 3.5.x | Application Security | OAuth2 Resource Server, method security |

### Machine Learning

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **AWS SageMaker** | Latest | ML Inference | Managed ML endpoints, scalable, auto-scaling |
| **Resilience4j** | 2.2.0 | Fault Tolerance | Circuit breaker for ML calls, fallback handling |

### Serialization & Schema

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Apache Avro** | Latest | Event Serialization | Schema evolution, compact binary format, type safety |
| **Apicurio Registry** | 2.6.13 | Schema Registry | Schema versioning, compatibility checks, centralized management |

### Testing

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **JUnit 5** | 5.10.x | Unit Testing | Modern testing framework, parameterized tests |
| **Testcontainers** | Latest | Integration Testing | Real PostgreSQL, Kafka, Redis, Keycloak in tests |
| **Awaitility** | 4.2.0 | Async Testing | Test async operations with timeout and polling |

### Build & Development

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Gradle** | 8.x | Build Tool | Fast incremental builds, dependency management |
| **MapStruct** | 1.6.3 | Object Mapping | Compile-time mapping generation, type-safe |
| **Lombok** | Latest | Boilerplate Reduction | @Builder, @Data, reduces code verbosity |
| **Flyway** | Latest | Database Migration | Version-controlled schema changes, repeatable migrations |

---

## Getting Started

### Prerequisites

- **JDK 25** (GraalVM or Eclipse Temurin)
- **Docker** & **Docker Compose**
- **Git**
- **Gradle** (or use `./gradlew` wrapper)

### Environment Setup

1. **Clone the repository:**
```bash
git clone https://github.com/itumelengManota/fraud-detection-service.git
cd fraud-detection-service
```

2. **Start infrastructure services:**
```bash
docker-compose -f docker-compose/compose.yml up -d
```

This starts:
- PostgreSQL (port 5432)
- PostgreSQL for Keycloak (port 5433)
- Redis (port 6379)
- Kafka (port 9092)
- Apicurio Registry (port 8081)
- Apicurio Registry UI (port 8082)
- Keycloak (port 8180)

3. **Verify services are healthy:**
```bash
docker-compose -f docker-compose/compose.yml ps
```

All services should show "healthy" status.

4. **Build the application:**
```bash
./gradlew clean build
```

5. **Run the application:**
```bash
./gradlew bootRun
```

The application starts on **http://localhost:9001**

### Development Mode Features

- **Hot Reload**: DevTools enabled for automatic restarts
- **H2 Console**: Available at `/h2-console` (if enabled)
- **Actuator Endpoints**: Health checks at `/actuator/health`
- **Swagger UI**: API documentation at `/swagger-ui.html`

### Verifying the Setup

1. **Check application health:**
```bash
curl http://localhost:9001/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

2. **Check Keycloak:**
```bash
curl http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration
```

3. **Check Kafka:**
```bash
docker exec -it fraud-detection-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

---

## Authentication & Authorization

### OAuth2 Configuration

The service uses **Keycloak** as the OAuth2 provider with the following configuration:

**Realm**: `fraud-detection`  
**Authorization Server**: `http://localhost:8180/realms/fraud-detection`  
**Token Endpoint**: `http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token`

### Pre-configured Clients

#### 1. **fraud-detection-service** (Resource Server)
- **Type**: Bearer-only client
- **Purpose**: Validates JWT tokens
- **Secret**: `fraud-detection-secret`

#### 2. **fraud-detection-client** (Test Client)
- **Type**: Confidential client
- **Grant Types**: Authorization Code, Client Credentials, Password
- **Secret**: `fraud-detection-client-secret`
- **Redirect URIs**: `http://localhost:9001/*`, `http://localhost:3000/*`

### Pre-configured Users

| Username | Password | Role | Scopes |
|----------|----------|------|--------|
| `analyst` | `analyst123` | `fraud_analyst` | `fraud:read` |
| `detector` | `detector123` | `fraud_detector` | `fraud:detect`, `fraud:read` |
| `admin` | `admin123` | `fraud_admin` | `fraud:detect`, `fraud:read` |

### Scopes & Authorities

| Scope | Authority | Required For |
|-------|-----------|--------------|
| `fraud:detect` | `SCOPE_fraud:detect` | POST `/fraud/assessments` |
| `fraud:read` | `SCOPE_fraud:read` | GET `/fraud/assessments*` |

### Obtaining Access Tokens

#### Method 1: Password Grant (for testing)

```bash
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fraud-detection-client" \
  -d "client_secret=fraud-detection-client-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "scope=fraud:detect fraud:read"
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "token_type": "Bearer",
  "scope": "fraud:detect fraud:read"
}
```

#### Method 2: Client Credentials Grant (for service-to-service)

```bash
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=fraud-detection-client" \
  -d "client_secret=fraud-detection-client-secret" \
  -d "scope=fraud:detect fraud:read"
```

#### Method 3: Authorization Code Flow (for web applications)

1. Redirect user to authorization endpoint:
```
http://localhost:8180/realms/fraud-detection/protocol/openid-connect/auth?
  response_type=code&
  client_id=fraud-detection-client&
  redirect_uri=http://localhost:9001/callback&
  scope=fraud:detect fraud:read&
  state=random-state-value
```

2. After user login, exchange code for token:
```bash
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "client_id=fraud-detection-client" \
  -d "client_secret=fraud-detection-client-secret" \
  -d "code=<authorization_code>" \
  -d "redirect_uri=http://localhost:9001/callback"
```

### Token Validation

Tokens are validated by the application using:
- **Signature verification**: RSA256 using JWKS from Keycloak
- **Audience validation**: Must contain `fraud-detection-service`
- **Issuer validation**: Must be from `fraud-detection` realm
- **Expiration check**: Tokens expire after 5 minutes (300s)

### Security Configuration

**Endpoint Protection:**
```java
POST /fraud/assessments → Requires SCOPE_fraud:detect
GET /fraud/assessments/** → Requires SCOPE_fraud:read
/actuator/health, /actuator/info → Public
/swagger-ui/**, /v3/api-docs/** → Public
```

**Location**: `infrastructure.config.SecurityConfig`

---

## API Reference

### Base URL
```
http://localhost:9001
```

### Endpoints

#### 1. **Assess Transaction Risk**

**Endpoint**: `POST /fraud/assessments`  
**Description**: Performs real-time fraud analysis on a transaction  
**Authorization**: Requires `SCOPE_fraud:detect`

**Request Body:**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "accountId": "ACC-12345",
  "amount": 15000.00,
  "currency": "USD",
  "type": "PURCHASE",
  "channel": "ONLINE",
  "merchantId": "MERCHANT-001",
  "merchantName": "Amazon",
  "merchantCategory": "E-COMMERCE",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "country": "US",
    "city": "New York",
    "timestamp": "2024-12-17T10:00:00Z"
  },
  "deviceId": "DEVICE-12345",
  "transactionTimestamp": "2024-12-17T10:00:00Z"
}
```

**Response** (200 OK):
```json
{
  "assessmentId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "riskScore": 75,
  "transactionRiskLevel": "HIGH",
  "decision": "REVIEW",
  "assessmentTime": "2024-12-17T10:00:01.234Z"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid request data (validation errors)
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Insufficient permissions
- `422 Unprocessable Entity`: Business rule violation
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Unexpected error

#### 2. **Get Risk Assessment**

**Endpoint**: `GET /fraud/assessments/{transactionId}`  
**Description**: Retrieves a previously completed risk assessment  
**Authorization**: Requires `SCOPE_fraud:read`

**Path Parameters:**
- `transactionId` (UUID): Transaction identifier

**Response** (200 OK):
```json
{
  "assessmentId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "riskScore": 75,
  "transactionRiskLevel": "HIGH",
  "decision": "REVIEW",
  "assessmentTime": "2024-12-17T10:00:01.234Z"
}
```

**Error Responses:**
- `404 Not Found`: Assessment not found for transaction ID
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Insufficient permissions
- `500 Internal Server Error`: Unexpected error

#### 3. **Search Risk Assessments**

**Endpoint**: `GET /fraud/assessments`  
**Description**: Search risk assessments with filters and pagination  
**Authorization**: Requires `SCOPE_fraud:read`

**Query Parameters:**
- `transactionRiskLevels` (String[], optional): Filter by risk levels (LOW, MEDIUM, HIGH, CRITICAL)
- `fromDate` (ISO-8601, optional): Filter assessments after this timestamp
- `page` (int, default=0): Page number (0-indexed)
- `size` (int, default=20): Page size
- `sort` (String, default="assessmentTime,desc"): Sort specification

**Examples:**

1. **Get all assessments (paginated):**
```
GET /fraud/assessments?page=0&size=20
```

2. **Filter by risk level:**
```
GET /fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&page=0&size=10
```

3. **Filter by date:**
```
GET /fraud/assessments?fromDate=2024-12-01T00:00:00Z&page=0&size=20
```

4. **Combined filters:**
```
GET /fraud/assessments?transactionRiskLevels=HIGH&fromDate=2024-12-01T00:00:00Z&page=0&size=10&sort=assessmentTime,asc
```

**Response** (200 OK):
```json
{
  "content": [
    {
      "assessmentId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "transactionId": "550e8400-e29b-41d4-a716-446655440000",
      "riskScore": 75,
      "transactionRiskLevel": "HIGH",
      "decision": "REVIEW",
      "assessmentTime": "2024-12-17T10:00:01.234Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "orders": [
        {
          "property": "assessmentTime",
          "direction": "DESC"
        }
      ]
    }
  },
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 20
}
```

**Error Responses:**
- `400 Bad Request`: Invalid query parameters (e.g., invalid risk level)
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Insufficient permissions
- `500 Internal Server Error`: Unexpected error

---

## Testing with cURL

### Setup: Get Access Token

Before running tests, obtain an access token:

```bash
export TOKEN=$(curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fraud-detection-client" \
  -d "client_secret=fraud-detection-client-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "scope=fraud:detect fraud:read" | jq -r '.access_token')
```

### Test Scenario 1: Low-Risk Transaction

**Scenario**: Small online purchase from known merchant

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "11111111-1111-1111-1111-111111111111",
    "accountId": "ACC-LOW-RISK",
    "amount": 49.99,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "merchantId": "MERCHANT-AMAZON",
    "merchantName": "Amazon",
    "merchantCategory": "E-COMMERCE",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "country": "US",
      "city": "New York",
      "timestamp": "2024-12-17T10:00:00Z"
    },
    "deviceId": "DEVICE-REGULAR",
    "transactionTimestamp": "2024-12-17T10:00:00Z"
  }'
```

**Expected Result**:
- Risk Score: ~20-30
- Risk Level: LOW
- Decision: ALLOW

### Test Scenario 2: Medium-Risk Transaction

**Scenario**: Large purchase triggering amount rule

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "22222222-2222-2222-2222-222222222222",
    "accountId": "ACC-MEDIUM-RISK",
    "amount": 12500.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "merchantId": "MERCHANT-ELECTRONICS",
    "merchantName": "Best Buy",
    "merchantCategory": "ELECTRONICS",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "country": "US",
      "city": "New York",
      "timestamp": "2024-12-17T10:00:00Z"
    },
    "deviceId": "DEVICE-REGULAR",
    "transactionTimestamp": "2024-12-17T10:00:00Z"
  }'
```

**Expected Result**:
- Risk Score: ~50-60
- Risk Level: MEDIUM
- Decision: CHALLENGE
- Triggered Rules: LARGE_AMOUNT

### Test Scenario 3: High-Risk Transaction (Velocity)

**Scenario**: Multiple transactions in short time window

First, create 6 transactions rapidly:

```bash
for i in {1..6}; do
  curl -X POST http://localhost:9001/fraud/assessments \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"transactionId\": \"33333333-3333-3333-3333-33333333333$i\",
      \"accountId\": \"ACC-HIGH-VELOCITY\",
      \"amount\": 100.00,
      \"currency\": \"USD\",
      \"type\": \"PURCHASE\",
      \"channel\": \"ONLINE\",
      \"merchantId\": \"MERCHANT-$i\",
      \"merchantName\": \"Merchant $i\",
      \"merchantCategory\": \"RETAIL\",
      \"location\": {
        \"latitude\": 40.7128,
        \"longitude\": -74.0060,
        \"country\": \"US\",
        \"city\": \"New York\",
        \"timestamp\": \"2024-12-17T10:00:00Z\"
      },
      \"deviceId\": \"DEVICE-REGULAR\",
      \"transactionTimestamp\": \"2024-12-17T10:00:00Z\"
    }"
  sleep 1
done
```

**Expected Result** (6th transaction):
- Risk Score: ~75-85
- Risk Level: HIGH
- Decision: REVIEW
- Triggered Rules: VELOCITY_5MIN

### Test Scenario 4: Critical Risk (Impossible Travel)

**Scenario**: Transaction from geographically impossible location

```bash
# First transaction in New York
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "44444444-4444-4444-4444-444444444441",
    "accountId": "ACC-IMPOSSIBLE-TRAVEL",
    "amount": 500.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "POS",
    "merchantId": "MERCHANT-NYC",
    "merchantName": "NYC Store",
    "merchantCategory": "RETAIL",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "country": "US",
      "city": "New York",
      "timestamp": "2024-12-17T10:00:00Z"
    },
    "deviceId": "DEVICE-US",
    "transactionTimestamp": "2024-12-17T10:00:00Z"
  }'

# Wait 1 minute, then transaction in Tokyo
sleep 60

curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "44444444-4444-4444-4444-444444444442",
    "accountId": "ACC-IMPOSSIBLE-TRAVEL",
    "amount": 500.00,
    "currency": "JPY",
    "type": "PURCHASE",
    "channel": "POS",
    "merchantId": "MERCHANT-TOKYO",
    "merchantName": "Tokyo Store",
    "merchantCategory": "RETAIL",
    "location": {
      "latitude": 35.6762,
      "longitude": 139.6503,
      "country": "JP",
      "city": "Tokyo",
      "timestamp": "2024-12-17T10:01:00Z"
    },
    "deviceId": "DEVICE-JP",
    "transactionTimestamp": "2024-12-17T10:01:00Z"
  }'
```

**Expected Result** (2nd transaction):
- Risk Score: ~95+
- Risk Level: CRITICAL
- Decision: BLOCK
- Triggered Rules: IMPOSSIBLE_TRAVEL

### Test Scenario 5: Very Large Amount (Critical)

**Scenario**: Transaction exceeding $100,000

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "55555555-5555-5555-5555-555555555555",
    "accountId": "ACC-LARGE-AMOUNT",
    "amount": 150000.00,
    "currency": "USD",
    "type": "WIRE",
    "channel": "ONLINE",
    "merchantId": "MERCHANT-WIRE",
    "merchantName": "Wire Transfer Service",
    "merchantCategory": "FINANCIAL",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "country": "US",
      "city": "New York",
      "timestamp": "2024-12-17T10:00:00Z"
    },
    "deviceId": "DEVICE-REGULAR",
    "transactionTimestamp": "2024-12-17T10:00:00Z"
  }'
```

**Expected Result**:
- Risk Score: ~92+
- Risk Level: CRITICAL
- Decision: BLOCK
- Triggered Rules: EXCESSIVELY_LARGE_AMOUNT

### Test Scenario 6: Get Risk Assessment

**Scenario**: Retrieve a previously assessed transaction

```bash
curl -X GET http://localhost:9001/fraud/assessments/11111111-1111-1111-1111-111111111111 \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**: Returns the assessment for the low-risk transaction

### Test Scenario 7: Search Assessments - High Risk Only

**Scenario**: Find all high and critical risk assessments

```bash
curl -X GET "http://localhost:9001/fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**: Paginated list of HIGH and CRITICAL assessments

### Test Scenario 8: Search Assessments - Recent Only

**Scenario**: Find assessments from the last hour

```bash
ONE_HOUR_AGO=$(date -u -d '1 hour ago' '+%Y-%m-%dT%H:%M:%SZ')
curl -X GET "http://localhost:9001/fraud/assessments?fromDate=$ONE_HOUR_AGO&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**: Paginated list of recent assessments

### Test Scenario 9: Unauthorized Access

**Scenario**: Attempt to access endpoint without token

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "99999999-9999-9999-9999-999999999999",
    "accountId": "ACC-UNAUTHORIZED",
    "amount": 100.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "transactionTimestamp": "2024-12-17T10:00:00Z"
  }'
```

**Expected Result**:
- Status: 401 Unauthorized
- Error: Missing or invalid authentication

### Test Scenario 10: Invalid Risk Level

**Scenario**: Search with invalid risk level

```bash
curl -X GET "http://localhost:9001/fraud/assessments?transactionRiskLevels=INVALID&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected Result**:
- Status: 400 Bad Request
- Error: Invalid transaction risk level

---

## Testing with Postman

### Postman Collection Setup

#### Step 1: Create Collection

1. Open Postman
2. Click "New" → "Collection"
3. Name: `Fraud Detection Service`
4. Description: `API tests for fraud detection service`

#### Step 2: Configure Collection Variables

Add these variables (Collection → Variables tab):

| Variable | Initial Value | Current Value |
|----------|---------------|---------------|
| `baseUrl` | `http://localhost:9001` | `http://localhost:9001` |
| `keycloakUrl` | `http://localhost:8180` | `http://localhost:8180` |
| `realm` | `fraud-detection` | `fraud-detection` |
| `clientId` | `fraud-detection-client` | `fraud-detection-client` |
| `clientSecret` | `fraud-detection-client-secret` | `fraud-detection-client-secret` |
| `username` | `detector` | `detector` |
| `password` | `detector123` | `detector123` |
| `accessToken` | (empty) | (will be populated) |

#### Step 3: Configure Collection Authorization

1. Go to Collection → Authorization tab
2. Type: `OAuth 2.0`
3. Configure:

```
Grant Type: Password Credentials
Access Token URL: {{keycloakUrl}}/realms/{{realm}}/protocol/openid-connect/token
Client ID: {{clientId}}
Client Secret: {{clientSecret}}
Username: {{username}}
Password: {{password}}
Scope: fraud:detect fraud:read
```

4. Click "Get New Access Token"
5. Click "Use Token"

#### Step 4: Create Pre-request Script (Collection Level)

Add this to Collection → Pre-request Scripts:

```javascript
// Auto-refresh token if expired
const tokenExpiry = pm.collectionVariables.get("tokenExpiry");
const now = Date.now();

if (!tokenExpiry || now > tokenExpiry) {
    pm.sendRequest({
        url: pm.variables.get("keycloakUrl") + "/realms/" + pm.variables.get("realm") + "/protocol/openid-connect/token",
        method: 'POST',
        header: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: {
            mode: 'urlencoded',
            urlencoded: [
                { key: 'grant_type', value: 'password' },
                { key: 'client_id', value: pm.variables.get("clientId") },
                { key: 'client_secret', value: pm.variables.get("clientSecret") },
                { key: 'username', value: pm.variables.get("username") },
                { key: 'password', value: pm.variables.get("password") },
                { key: 'scope', value: 'fraud:detect fraud:read' }
            ]
        }
    }, function (err, response) {
        if (!err) {
            const jsonData = response.json();
            pm.collectionVariables.set("accessToken", jsonData.access_token);
            pm.collectionVariables.set("tokenExpiry", Date.now() + (jsonData.expires_in * 1000));
        }
    });
}
```

### Request Examples

#### Request 1: Assess Low-Risk Transaction

**Method**: `POST`  
**URL**: `{{baseUrl}}/fraud/assessments`  
**Headers**:
```
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**Body** (raw JSON):
```json
{
  "transactionId": "{{$randomUUID}}",
  "accountId": "ACC-{{$randomInt}}",
  "amount": 49.99,
  "currency": "USD",
  "type": "PURCHASE",
  "channel": "ONLINE",
  "merchantId": "MERCHANT-AMAZON",
  "merchantName": "Amazon",
  "merchantCategory": "E-COMMERCE",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "country": "US",
    "city": "New York",
    "timestamp": "{{$isoTimestamp}}"
  },
  "deviceId": "DEVICE-{{$randomInt}}",
  "transactionTimestamp": "{{$isoTimestamp}}"
}
```

**Tests** (Tests tab):
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has required fields", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property("assessmentId");
    pm.expect(jsonData).to.have.property("transactionId");
    pm.expect(jsonData).to.have.property("riskScore");
    pm.expect(jsonData).to.have.property("transactionRiskLevel");
    pm.expect(jsonData).to.have.property("decision");
});

pm.test("Low risk assessment", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.transactionRiskLevel).to.equal("LOW");
    pm.expect(jsonData.decision).to.equal("ALLOW");
    pm.expect(jsonData.riskScore).to.be.below(41);
});

// Save transaction ID for later tests
pm.collectionVariables.set("lastTransactionId", pm.response.json().transactionId);
```

#### Request 2: Assess Large Amount Transaction

**Method**: `POST`  
**URL**: `{{baseUrl}}/fraud/assessments`  
**Headers**: (same as Request 1)

**Body**:
```json
{
  "transactionId": "{{$randomUUID}}",
  "accountId": "ACC-{{$randomInt}}",
  "amount": 15000.00,
  "currency": "USD",
  "type": "PURCHASE",
  "channel": "ONLINE",
  "merchantId": "MERCHANT-BESTBUY",
  "merchantName": "Best Buy",
  "merchantCategory": "ELECTRONICS",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "country": "US",
    "city": "New York",
    "timestamp": "{{$isoTimestamp}}"
  },
  "deviceId": "DEVICE-{{$randomInt}}",
  "transactionTimestamp": "{{$isoTimestamp}}"
}
```

**Tests**:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Medium or High risk", function () {
    const jsonData = pm.response.json();
    pm.expect(["MEDIUM", "HIGH", "CRITICAL"]).to.include(jsonData.transactionRiskLevel);
});

pm.test("Risk score appropriate", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.riskScore).to.be.at.least(40);
});
```

#### Request 3: Assess Very Large Amount (Critical)

**Method**: `POST`  
**URL**: `{{baseUrl}}/fraud/assessments`  
**Headers**: (same as Request 1)

**Body**:
```json
{
  "transactionId": "{{$randomUUID}}",
  "accountId": "ACC-{{$randomInt}}",
  "amount": 150000.00,
  "currency": "USD",
  "type": "WIRE",
  "channel": "ONLINE",
  "merchantId": "MERCHANT-WIRE",
  "merchantName": "Wire Transfer Service",
  "merchantCategory": "FINANCIAL",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "country": "US",
    "city": "New York",
    "timestamp": "{{$isoTimestamp}}"
  },
  "deviceId": "DEVICE-{{$randomInt}}",
  "transactionTimestamp": "{{$isoTimestamp}}"
}
```

**Tests**:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Critical risk assessment", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.transactionRiskLevel).to.equal("CRITICAL");
    pm.expect(jsonData.decision).to.equal("BLOCK");
    pm.expect(jsonData.riskScore).to.be.at.least(90);
});
```

#### Request 4: Get Risk Assessment

**Method**: `GET`  
**URL**: `{{baseUrl}}/fraud/assessments/{{lastTransactionId}}`  
**Headers**:
```
Authorization: Bearer {{accessToken}}
```

**Tests**:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Returns assessment for correct transaction", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.transactionId).to.equal(pm.collectionVariables.get("lastTransactionId"));
});
```

#### Request 5: Search High-Risk Assessments

**Method**: `GET`  
**URL**: `{{baseUrl}}/fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&page=0&size=10`  
**Headers**:
```
Authorization: Bearer {{accessToken}}
```

**Tests**:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Returns paginated results", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property("content");
    pm.expect(jsonData).to.have.property("totalElements");
    pm.expect(jsonData).to.have.property("totalPages");
});

pm.test("All results are HIGH or CRITICAL", function () {
    const jsonData = pm.response.json();
    jsonData.content.forEach(assessment => {
        pm.expect(["HIGH", "CRITICAL"]).to.include(assessment.transactionRiskLevel);
    });
});
```

#### Request 6: Search Recent Assessments

**Method**: `GET`  
**URL**: `{{baseUrl}}/fraud/assessments?fromDate={{$isoTimestamp}}&page=0&size=20`  
**Headers**:
```
Authorization: Bearer {{accessToken}}
```

**Pre-request Script**:
```javascript
// Set fromDate to 1 hour ago
const oneHourAgo = new Date(Date.now() - 3600000).toISOString();
pm.collectionVariables.set("oneHourAgo", oneHourAgo);
```

Update URL to: `{{baseUrl}}/fraud/assessments?fromDate={{oneHourAgo}}&page=0&size=20`

**Tests**:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Returns only recent assessments", function () {
    const jsonData = pm.response.json();
    const oneHourAgo = pm.collectionVariables.get("oneHourAgo");
    
    jsonData.content.forEach(assessment => {
        pm.expect(new Date(assessment.assessmentTime).getTime())
            .to.be.at.least(new Date(oneHourAgo).getTime());
    });
});
```

#### Request 7: Invalid Transaction (Validation Error)

**Method**: `POST`  
**URL**: `{{baseUrl}}/fraud/assessments`  
**Headers**: (same as Request 1)

**Body** (missing required fields):
```json
{
  "transactionId": "{{$randomUUID}}",
  "accountId": "ACC-{{$randomInt}}",
  "currency": "USD"
}
```

**Tests**:
```javascript
pm.test("Status code is 400", function () {
    pm.response.to.have.status(400);
});

pm.test("Returns validation errors", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property("code", "VALIDATION_ERROR");
    pm.expect(jsonData).to.have.property("details");
    pm.expect(jsonData.details).to.be.an("array");
});
```

#### Request 8: Unauthorized Access

**Method**: `POST`  
**URL**: `{{baseUrl}}/fraud/assessments`  
**Headers**:
```
Content-Type: application/json
```
(No Authorization header)

**Body**: (any valid transaction body)

**Tests**:
```javascript
pm.test("Status code is 401", function () {
    pm.response.to.have.status(401);
});
```

#### Request 9: Invalid Risk Level Search

**Method**: `GET`  
**URL**: `{{baseUrl}}/fraud/assessments?transactionRiskLevels=INVALID&page=0&size=10`  
**Headers**:
```
Authorization: Bearer {{accessToken}}
```

**Tests**:
```javascript
pm.test("Status code is 400", function () {
    pm.response.to.have.status(400);
});

pm.test("Returns invalid risk level error", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.code).to.equal("INVALID_RISK_LEVEL");
});
```

### Running the Collection

#### Option 1: Collection Runner

1. Click "Collections" → "Fraud Detection Service"
2. Click "Run"
3. Select all requests
4. Set iterations: 1
5. Click "Run Fraud Detection Service"

#### Option 2: Newman (CLI)

Export collection and environment, then:

```bash
npm install -g newman
newman run fraud-detection-collection.json -e fraud-detection-environment.json
```

### Postman Environment Variables

Create an environment called "Fraud Detection - Dev":

| Variable | Initial Value | Current Value |
|----------|---------------|---------------|
| `baseUrl` | `http://localhost:9001` | `http://localhost:9001` |
| `keycloakUrl` | `http://localhost:8180` | `http://localhost:8180` |
| `realm` | `fraud-detection` | `fraud-detection` |
| `clientId` | `fraud-detection-client` | `fraud-detection-client` |
| `clientSecret` | `fraud-detection-client-secret` | `fraud-detection-client-secret` |
| `username` | `detector` | `detector123` |
| `password` | `detector123` | `detector123` |
| `accessToken` | (empty) | (populated by pre-request script) |
| `tokenExpiry` | (empty) | (populated by pre-request script) |
| `lastTransactionId` | (empty) | (populated by tests) |
| `oneHourAgo` | (empty) | (populated by tests) |

---

## CI/CD Pipeline

### GitHub Actions Workflow

**File**: `.github/workflows/commit-stage.yml`

The CI/CD pipeline runs on every push and implements a two-stage process:

#### Stage 1: Build and Test

**Triggers**: All pushes to any branch

**Steps:**
1. **Checkout Code**: `actions/checkout@v5`
2. **Setup JDK**: GraalVM Java 25 with Gradle caching
3. **Build & Test**: `./gradlew build`
    - Compiles source code
    - Runs unit tests
    - Runs integration tests (with Testcontainers)
    - Generates test reports
4. **Vulnerability Scanning**: Anchore scan on source code
    - Scans for high-severity vulnerabilities
    - Does not fail build (informational)
5. **Upload Scan Results**: CodeQL SARIF upload for GitHub Security tab

**Environment**:
- Runner: `ubuntu-latest`
- Java: GraalVM 25
- Gradle cache enabled

#### Stage 2: Package and Publish (Main Branch Only)

**Triggers**: Pushes to `main` branch only  
**Dependency**: Requires successful "build" stage

**Steps:**
1. **Checkout Code**
2. **Setup JDK**: GraalVM Java 25
3. **Build JAR**: `./gradlew bootJar`
4. **Docker Login**: Authenticate to GitHub Container Registry (ghcr.io)
5. **Setup QEMU**: Cross-platform emulation for ARM64
6. **Setup Docker Buildx**: Multi-architecture builds
7. **Build & Push Images**:
    - Platforms: `linux/amd64`, `linux/arm64`
    - Registry: `ghcr.io`
    - Tags:
        - `ghcr.io/itumelengmanota/fraud-detection-service:${{ github.sha }}`
        - `ghcr.io/itumelengmanota/fraud-detection-service:latest`
8. **Vulnerability Scanning**: Anchore scan on Docker image
9. **Upload Scan Results**: CodeQL SARIF upload

**Environment Variables**:
```yaml
REGISTRY: ghcr.io
IMAGE_NAME: itumelengmanota/fraud-detection-service
VERSION: ${{ github.sha }}
```

**Permissions**:
```yaml
contents: read        # Read repository
packages: write       # Publish to GHCR
security-events: write # Upload SARIF
```

### Vulnerability Scanning

**Tool**: Anchore Grype  
**Configuration**:
- Severity cutoff: `high` (scans for high and critical)
- Fail build: `false` (informational only)
- Output format: SARIF
- Upload location: GitHub Security → Code Scanning Alerts

**What's Scanned**:
1. **Source Code** (build stage):
    - Java dependencies
    - Gradle plugins
    - Application code
2. **Docker Image** (package stage):
    - Base image vulnerabilities
    - Application JAR
    - System packages

### Test Execution

**Unit Tests**:
- Parallel execution enabled
- Max forks: `Runtime.availableProcessors() / 2`
- Memory: 512MB - 2GB

**Integration Tests**:
- Testcontainers for real dependencies
- Container reuse enabled
- Containers: PostgreSQL, Redis, Kafka, Keycloak

**Test Logging**:
```
Events: passed, skipped, failed
Exception format: full
Summary: Total, Passed, Failed, Skipped, Duration
```

### Build Artifacts

**Generated Artifacts**:
1. **Application JAR**: `build/libs/fraud-detection-service-0.0.1-SNAPSHOT.jar`
2. **Docker Images**:
    - AMD64: `ghcr.io/itumelengmanota/fraud-detection-service:latest`
    - ARM64: `ghcr.io/itumelengmanota/fraud-detection-service:latest`
3. **Test Reports**: `build/reports/tests/test/index.html`
4. **SARIF Reports**: Uploaded to GitHub Security

### Pulling Built Images

```bash
# Pull latest
docker pull ghcr.io/itumelengmanota/fraud-detection-service:latest

# Pull specific version
docker pull ghcr.io/itumelengmanota/fraud-detection-service:<commit-sha>

# Run
docker run -p 9001:9001 ghcr.io/itumelengmanota/fraud-detection-service:latest
```

---

## Infrastructure Management

### Docker Compose Services

**File**: `docker-compose/compose.yml`

#### Starting All Services

```bash
docker-compose -f docker-compose/compose.yml up -d
```

#### Stopping All Services

```bash
docker-compose -f docker-compose/compose.yml down
```

#### Viewing Logs

```bash
# All services
docker-compose -f docker-compose/compose.yml logs -f

# Specific service
docker-compose -f docker-compose/compose.yml logs -f postgres
```

### Individual Service Management

#### PostgreSQL (Application Database)

**Port**: 5432  
**Database**: `mydatabase`  
**User**: `myuser`  
**Password**: `secret`

```bash
# Connect via psql
docker exec -it fraud-detection-postgres psql -U myuser -d mydatabase

# View tables
\dt

# Query risk assessments
SELECT * FROM risk_assessments ORDER BY assessment_time DESC LIMIT 10;
```

#### Redis (Cache & Counters)

**Port**: 6379

```bash
# Connect via redis-cli
docker exec -it fraud-detection-redis redis-cli

# View velocity counters
KEYS velocity:*

# Get counter value
GET velocity:transaction:counter:5min:ACC-12345

# View HyperLogLog merchants
PFCOUNT velocity:merchants:5min:ACC-12345
```

#### Kafka

**Port**: 9092  
**Cluster ID**: `MkU3OEVBNTcwNTJENDM2Qk`

```bash
# List topics
docker exec -it fraud-detection-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Describe topic
docker exec -it fraud-detection-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic transactions.normalized

# Consume messages
docker exec -it fraud-detection-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic fraud-detection.risk-assessments \
  --from-beginning

# Produce test message
docker exec -it fraud-detection-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic transactions.normalized
```

#### Apicurio Registry (Schema Registry)

**API Port**: 8081  
**UI Port**: 8082

**Web UI**: http://localhost:8082

```bash
# List artifacts
curl http://localhost:8081/apis/registry/v2/search/artifacts

# Get specific schema
curl http://localhost:8081/apis/registry/v2/groups/default/artifacts/TransactionAvro
```

#### Keycloak (Identity Provider)

**Port**: 8180  
**Admin Console**: http://localhost:8180/admin  
**Admin User**: `admin`  
**Admin Password**: `admin`

```bash
# Import realm manually
docker exec -it fraud-detection-keycloak /opt/keycloak/bin/kc.sh import \
  --file /opt/keycloak/data/import/realm-export.json

# Export realm
docker exec -it fraud-detection-keycloak /opt/keycloak/bin/kc.sh export \
  --file /tmp/realm-backup.json --realm fraud-detection

# View logs
docker logs fraud-detection-keycloak -f
```

#### PostgreSQL (Keycloak Database)

**Port**: 5433  
**Database**: `keycloak`  
**User**: `keycloak`  
**Password**: `keycloak`

```bash
# Connect
docker exec -it fraud-detection-postgres-keycloak psql -U keycloak -d keycloak

# View users
SELECT username, email, enabled FROM user_entity;
```

### Database Migrations

**Tool**: Flyway  
**Location**: `src/main/resources/db/migration`

**Migrations**:
1. `V1__create_risk_assessments_table.sql` - Risk assessments table
2. `V2__create_rule_evaluations_table.sql` - Rule evaluations (child table)
3. `V3__create_transaction_tables.sql` - Transaction, merchant, location tables

**Running Migrations Manually**:
```bash
./gradlew flywayMigrate
```

**Viewing Migration History**:
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### Health Checks

All services have health checks configured:

```bash
# Check all service health
docker-compose -f docker-compose/compose.yml ps

# Check specific service
docker inspect fraud-detection-postgres | jq '.[0].State.Health'
```

### Cleaning Up

**Remove all containers and volumes:**
```bash
docker-compose -f docker-compose/compose.yml down -v
```

**Remove images:**
```bash
docker rmi $(docker images | grep fraud-detection | awk '{print $3}')
```

**Complete cleanup:**
```bash
docker-compose -f docker-compose/compose.yml down -v --rmi all
```

### Monitoring

#### Prometheus Metrics

**Endpoint**: http://localhost:9001/actuator/prometheus

```bash
# View metrics
curl http://localhost:9001/actuator/prometheus
```

**Available Metrics**:
- `sagemaker_prediction_duration_seconds` - ML prediction latency
- `sagemaker_prediction_errors_total` - ML prediction failures
- `http_server_requests_seconds` - HTTP request metrics
- `jvm_memory_used_bytes` - JVM memory usage
- `process_cpu_usage` - CPU utilization

#### Distributed Tracing

**Export Endpoint**: http://tempo:4318/v1/traces (configured for Tempo)

Traces include:
- HTTP requests
- Kafka message processing
- ML service calls
- Database queries

#### Application Health

**Liveness**: http://localhost:9001/actuator/health/liveness  
**Readiness**: http://localhost:9001/actuator/health/readiness

```bash
# Check liveness
curl http://localhost:9001/actuator/health/liveness

# Check readiness
curl http://localhost:9001/actuator/health/readiness
```

---

## Performance Tuning

### Application Configuration

**Virtual Threads** (Java 25):
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Kafka Consumer Tuning**:
```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 1000
      concurrency: 10  # Number of consumer threads
```

**Database Connection Pool**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
```

**Redis Timeout**:
```yaml
spring:
  data:
    redis:
      timeout: 3000ms
```

### Monitoring Performance

```bash
# View thread dumps
curl http://localhost:9001/actuator/threaddump

# View heap dump
curl http://localhost:9001/actuator/heapdump > heapdump.bin

# View metrics
curl http://localhost:9001/actuator/metrics
```

---

## Troubleshooting

### Common Issues

#### 1. Application Won't Start

**Error**: `Connection refused` to PostgreSQL/Redis/Kafka

**Solution**:
```bash
# Check if containers are running
docker-compose -f docker-compose/compose.yml ps

# Restart services
docker-compose -f docker-compose/compose.yml restart

# Check logs
docker-compose -f docker-compose/compose.yml logs
```

#### 2. Authentication Failures

**Error**: `401 Unauthorized` or `Invalid token`

**Solution**:
```bash
# Verify Keycloak is running
curl http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration

# Check token validity
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token/introspect \
  -u fraud-detection-client:fraud-detection-client-secret \
  -d "token=<your-token>"
```

#### 3. Kafka Connection Issues

**Error**: `TimeoutException: Failed to update metadata`

**Solution**:
```bash
# Check Kafka broker
docker exec -it fraud-detection-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Create missing topics
docker exec -it fraud-detection-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --topic transactions.normalized --partitions 10 --replication-factor 1
```

#### 4. SageMaker Integration

**Error**: `Circuit breaker open` or `Prediction timeout`

**Solution**:
```bash
# Disable SageMaker for testing
export AWS_SAGEMAKER_ENABLED=false
./gradlew bootRun
```

The system will use fallback predictions (fraud probability = 0.0).

---

## Additional Resources

- **Swagger UI**: http://localhost:9001/swagger-ui.html
- **OpenAPI Spec**: http://localhost:9001/v3/api-docs
- **Actuator**: http://localhost:9001/actuator
- **Keycloak Admin**: http://localhost:8180/admin
- **Apicurio UI**: http://localhost:8082

---

## License

[Add license information]

## Contributing

[Add contribution guidelines]

---

**Last Updated**: 18 December 2025  
**Maintained By**: Ignatius Itumeleng Manota