# Fraud Detection Service - Development Guide

## Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Ubiquitous Language Glossary](#ubiquitous-language-glossary)
- [Design Patterns & Principles](#design-patterns--principles)
- [Business Logic](#business-logic)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Machine Learning Integration](#machine-learning-integration)
- [Account Service Integration](#account-service-integration)
- [Authentication & Authorization](#authentication--authorization)
- [API Reference](#api-reference)
- [Docker Compose Services](#docker-compose-services)
- [Testing](#testing)
- [CI/CD Pipeline](#cicd-pipeline)
- [Infrastructure Management](#infrastructure-management)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting](#troubleshooting)
- [Additional Resources](#additional-resources)
- [TODO](#todo)

---

## Overview

The Fraud Detection Service is an enterprise-grade, real-time fraud detection system designed to process 100,000+ transactions per second with sub-100ms latency. It combines machine learning predictions, rule-based evaluation, velocity checks, and geographic validation to assess transaction risk and make automated decisions.

Built using **Hexagonal Architecture** and **Domain-Driven Design** principles, the service maintains clean separation between business logic and infrastructure concerns, making it highly maintainable, testable, and adaptable to changing requirements.

## Key Features

- **Real-time Risk Assessment**: Sub-100ms P99 latency for transaction evaluation
- **Hybrid Scoring**: Combines ML predictions (60%) with business rules (40%)
- **Machine Learning**: XGBoost model with configurable deployment (local Docker or AWS SageMaker)
- **Velocity Tracking**: Redis-backed counters for transaction frequency analysis
- **Geographic Validation**: Impossible travel detection using Haversine distance calculations
- **Account Service Integration**: Home location verification via Account Management bounded context
- **Privacy-by-Design**: Minimal PII storage, caching strategies, and fallback mechanisms
- **Event-Driven Architecture**: Kafka-based event streaming with Avro serialization
- **Schema Evolution**: Apicurio Registry for Avro schema management
- **Domain Events**: Captures significant business events for integration and auditing
- **High-throughput Messaging**: Kafka for low-latency, reliable event processing
- **Idempotency**: Redis-backed deduplication for exactly-once processing
- **High-performance Caching**: Redis for low-latency access to velocity metrics and idempotency data
- **OAuth2 Security**: Keycloak-based authentication and authorization with SASL/OAuth2 Bearer Tokens
- **Observability**: OpenTelemetry tracing, metrics, comprehensive health checks, and structured logging with correlation IDs for traceability
- **High-throughput Concurrency**: Virtual threads for efficient resource utilisation
- **High Availability**: Stateless design for horizontal scaling and fault tolerance
- **Resilience Patterns**: Circuit Breaker, Retry, Time Limiter, Bulkhead for external service calls
- **Configurable Rules Engine**: Drools-based rule engine for dynamic business rule management
- **CQRS Pattern**: Clear separation of command and query responsibilities
- **Ubiquitous Language**: Shared vocabulary between domain experts and developers for clarity
- **Extensible Design**: Easily adaptable to new requirements and integrations
- **Scalable Infrastructure**: Designed for cloud-native deployments with Kubernetes and Docker
- **Comprehensive Testing**: Unit, integration, and end-to-end tests with Testcontainers
- **Documentation**: OpenAPI/Swagger documentation for REST APIs
- **CI/CD Pipeline**: Automated builds, and tests via GitHub Actions
- **Multi-Architecture Support**: Docker images for AMD64 and ARM64

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
│  │  - AccountServicePort                                │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ↓↑                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             Outbound Adapters (Driven)               │  │
│  │  - JDBC Repositories (PostgreSQL)                    │  │
│  │  - Kafka Producer (Event Publishing)                 │  │
│  │  - Redis Cache (Velocity Counters)                   │  │
│  │  - SageMaker Client (ML Predictions)                 │  │
│  │  - Account Service REST Client                       │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

**Locations:**
- **Ports**: `application.port.in.*` (input ports), `application.port.out.*` (output ports)
- **Adapters**: `infrastructure.adapter.*` (REST, Kafka, Persistence, ML, Cache, Account)
- **Domain**: `domain.*` (pure business logic, no framework dependencies)

#### 2. **Source Code Structure**

The project follows **Hexagonal Architecture** (Ports and Adapters) with clear separation of concerns across three main layers:
```
fraud-detection-service/
│
├── .github/                                 # CI/CD Pipeline
│   └── workflows/
│       └── commit-stage.yml                 # GitHub Actions workflow
│
├── docker-compose/                          # Local Infrastructure
│   ├── compose.yml                          # Service definitions
│   ├── keycloak-config/
│   │   ├── fraud-detection-realm.json       # Pre-configured Keycloak realm
│   │   └── master-realm.json                # Keycloak master realm override
│   ├── mockoon/
│   │   └── data.json                       # Mock Account Service data
│   └── postgresql/
│       └── init.sql                        # Database initialization
│
├── src/
│   ├── main/
│   │   ├── avro/                           # Kafka Event Schemas
│   │   │   ├── transaction-event.avsc          # Input transaction event
│   │   │   ├── risk-assessment-completed.avsc  # Assessment result event
│   │   │   └── high-risk-detected.avsc         # High-risk alert event
│   │   │
│   │   ├── java/com/twenty9ine/frauddetection/
│   │   │   │
│   │   │   ├── FraudDetectionServiceApplication.java  # Spring Boot Entry Point
│   │   │   │
│   │   │   ├── application/               # APPLICATION LAYER (Use Cases)
│   │   │   │   ├── dto/                   # Data Transfer Objects
│   │   │   │   │   ├── LocationDto.java
│   │   │   │   │   ├── RiskAssessmentDto.java
│   │   │   │   │   └── PagedResultDto.java
│   │   │   │   │
│   │   │   │   ├── port/                  # Port Interfaces (Hexagonal Architecture)
│   │   │   │   │   ├── in/                   # Input Ports (Use Case Interfaces)
│   │   │   │   │   │   ├── AssessTransactionRiskUseCase.java
│   │   │   │   │   │   ├── GetRiskAssessmentUseCase.java
│   │   │   │   │   │   ├── FindRiskLeveledAssessmentsUseCase.java
│   │   │   │   │   │   ├── ProcessTransactionUseCase.java
│   │   │   │   │   │   ├── command/              # Command Objects (CQS Pattern)
│   │   │   │   │   │   │   ├── AssessTransactionRiskCommand.java
│   │   │   │   │   │   │   └── ProcessTransactionCommand.java
│   │   │   │   │   │   └── query/                # Query Objects (CQS Pattern)
│   │   │   │   │   │       ├── GetRiskAssessmentQuery.java
│   │   │   │   │   │       ├── FindRiskLeveledAssessmentsQuery.java
│   │   │   │   │   │       └── PageRequestQuery.java
│   │   │   │   │   │
│   │   │   │   │   └── out/                   # Output Ports (SPI)
│   │   │   │   │       ├── AccountServicePort.java
│   │   │   │   │       ├── EventPublisherPort.java
│   │   │   │   │       ├── MLServicePort.java
│   │   │   │   │       ├── RiskAssessmentRepository.java
│   │   │   │   │       ├── TransactionRepository.java
│   │   │   │   │       └── VelocityServicePort.java
│   │   │   │   │
│   │   │   │   └── service/              # Application Services (Use Case Implementations)
│   │   │   │       ├── FraudDetectionApplicationService.java
│   │   │   │       └── ProcessTransactionApplicationService.java
│   │   │   │
│   │   │   ├── domain/                   # DOMAIN LAYER (Business Logic)
│   │   │   │   ├── aggregate/                # Domain Aggregates (DDD)
│   │   │   │   │   └── RiskAssessment.java       # Aggregate Root
│   │   │   │   │
│   │   │   │   ├── event/                    # Domain Events (Event Sourcing)
│   │   │   │   │   ├── DomainEvent.java
│   │   │   │   │   ├── RiskAssessmentCompleted.java
│   │   │   │   │   └── HighRiskDetected.java
│   │   │   │   │
│   │   │   │   ├── exception/                # Domain Exceptions
│   │   │   │   │   ├── AccountNotFoundException.java
│   │   │   │   │   ├── AccountServiceException.java
│   │   │   │   │   ├── EventPublishingException.java
│   │   │   │   │   ├── InvariantViolationException.java
│   │   │   │   │   └── RiskAssessmentNotFoundException.java
│   │   │   │   │
│   │   │   │   ├── service/                  # Domain Services
│   │   │   │   │   ├── RiskScoringService.java     # Composite risk scoring
│   │   │   │   │   ├── RuleEngineService.java      # Drools integration
│   │   │   │   │   ├── GeographicValidator.java    # Impossible travel detection
│   │   │   │   │   ├── DecisionService.java        # Risk-based decisions
│   │   │   │   │   ├── DecisionStrategy.java       # Strategy pattern interface
│   │   │   │   │   ├── LowRiskStrategy.java
│   │   │   │   │   ├── MediumRiskStrategy.java
│   │   │   │   │   ├── HighRiskStrategy.java
│   │   │   │   │   └── CriticalRiskStrategy.java
│   │   │   │   │
│   │   │   │   └── valueobject/              # Value Objects (DDD)
│   │   │   │       ├── AccountProfile.java
│   │   │   │       ├── AssessmentId.java
│   │   │   │       ├── Channel.java
│   │   │   │       ├── Decision.java
│   │   │   │       ├── GeographicContext.java
│   │   │   │       ├── Location.java
│   │   │   │       ├── Merchant.java
│   │   │   │       ├── MerchantCategory.java
│   │   │   │       ├── MerchantId.java
│   │   │   │       ├── MLPrediction.java
│   │   │   │       ├── Money.java
│   │   │   │       ├── PageRequest.java
│   │   │   │       ├── PagedResult.java
│   │   │   │       ├── RiskScore.java
│   │   │   │       ├── RuleEvaluation.java
│   │   │   │       ├── RuleEvaluationResult.java
│   │   │   │       ├── RuleTrigger.java
│   │   │   │       ├── RuleType.java
│   │   │   │       ├── RuleViolationSeverity.java
│   │   │   │       ├── SortDirection.java
│   │   │   │       ├── TimeWindow.java
│   │   │   │       ├── Transaction.java
│   │   │   │       ├── TransactionId.java
│   │   │   │       ├── TransactionRiskLevel.java
│   │   │   │       ├── TransactionType.java
│   │   │   │       ├── VelocityMetrics.java
│   │   │   │       └── validation/             # Custom Validators
│   │   │   │           ├── ValidCountry.java
│   │   │   │           └── ValidCountryValidator.java
│   │   │   │
│   │   │   └── infrastructure/           # INFRASTRUCTURE LAYER (Technical Concerns)
│   │   │       ├── adapter/                  # Adapter Implementations (Hexagonal)
│   │   │       │   │
│   │   │       │   ├── account/                # Account Service Adapter (Output)
│   │   │       │   │   ├── AccountServiceRestAdapter.java
│   │   │       │   │   └── dto/
│   │   │       │   │       ├── AccountDto.java
│   │   │       │   │       └── LocationDto.java
│   │   │       │   │
│   │   │       │   ├── cache/                  # Velocity Counter Adapter (Output)
│   │   │       │   │   └── VelocityCounterAdapter.java  # Redis-based velocity tracking
│   │   │       │   │
│   │   │       │   ├── kafka/                  # Kafka Adapters (Input/Output)
│   │   │       │   │   ├── TransactionEventConsumer.java      # Input adapter
│   │   │       │   │   ├── TransactionEventMapper.java
│   │   │       │   │   ├── EventPublisherAdapter.java         # Output adapter
│   │   │       │   │   ├── DomainEventToAvroMapper.java
│   │   │       │   │   └── SeenMessageCache.java              # Idempotency
│   │   │       │   │
│   │   │       │   ├── ml/                     # Machine Learning Adapters (Output)
│   │   │       │   │   └── SageMakerMLAdapter.java           # AWS SageMaker integration
│   │   │       │   │
│   │   │       │   ├── persistence/            # Database Adapters (Output)
│   │   │       │   │   ├── RiskAssessmentJdbcRepository.java
│   │   │       │   │   ├── RiskAssessmentRepositoryAdapter.java
│   │   │       │   │   ├── TransactionJdbcRepository.java
│   │   │       │   │   ├── TransactionRepositoryAdapter.java
│   │   │       │   │   ├── converter/             # JSONB Converters
│   │   │       │   │   │   ├── JsonbReadingConverter.java
│   │   │       │   │   │   └── JsonbWritingConverter.java
│   │   │       │   │   ├── entity/                # JDBC Entities
│   │   │       │   │   │   ├── RiskAssessmentEntity.java
│   │   │       │   │   │   ├── RuleEvaluationEntity.java
│   │   │       │   │   │   ├── TransactionEntity.java
│   │   │       │   │   │   ├── MerchantEntity.java
│   │   │       │   │   │   └── LocationEntity.java
│   │   │       │   │   └── mapper/                # Entity-Domain Mappers
│   │   │       │   │       ├── RiskAssessmentMapper.java
│   │   │       │   │       ├── RuleEvaluationMapper.java
│   │   │       │   │       ├── TransactionMapper.java
│   │   │       │   │       ├── MerchantMapper.java
│   │   │       │   │       └── LocationMapper.java
│   │   │       │   │
│   │   │       │   └── rest/                   # REST API Adapter (Input)
│   │   │       │       ├── FraudDetectionController.java
│   │   │       │       ├── GlobalExceptionHandler.java
│   │   │       │       └── dto/
│   │   │       │           └── ErrorResponse.java
│   │   │       │
│   │   │       └── config/                     # Spring Configuration
│   │   │           ├── DomainServiceConfig.java          # Domain beans
│   │   │           ├── DroolsInfrastructureConfig.java   # Rule engine
│   │   │           ├── JdbcConfig.java                   # Database
│   │   │           ├── KafkaTopicProperties.java         # Kafka topics
│   │   │           ├── OpenApiConfig.java                # Swagger/OpenAPI
│   │   │           ├── RedisConfig.java                  # Cache
│   │   │           ├── SageMakerConfig.java              # ML service
│   │   │           └── SecurityConfig.java               # OAuth2
│   │   │
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── kmodule.xml                       # Drools configuration
│   │       │
│   │       ├── db/migration/                         # Database Migrations (Flyway)
│   │       │   ├── V1__create_risk_assessments_table.sql
│   │       │   ├── V2__create_rule_evaluations_table.sql
│   │       │   └── V3__create_transaction_tables.sql
│   │       │
│   │       ├── rules/                               # Business Rules (Drools DRL)
│   │       │   ├── amount-rules.drl                  # Large amount detection
│   │       │   ├── geographic-rules.drl              # Impossible travel
│   │       │   └── velocity-rules.drl                # Transaction velocity
│   │       │
│   │       ├── application.yml                      # Default configuration
│   │       ├── application-qa.yml                    # QA environment
│   │       └── application-prod.yml                  # Production environment
│   │
│   └── test/
│       ├── java/com/twenty9ine/frauddetection/
│       │   ├── TestDataFactory.java                  # Test data builders
│       │   │
│       │   ├── application/                          # Application Layer Tests
│       │   │   └── service/
│       │   │       ├── FraudDetectionApplicationServiceIntegrationTest.java
│       │   │       └── ProcessTransactionApplicationServiceIntegrationTest.java
│       │   │
│       │   ├── domain/                               # Domain Layer Tests
│       │   │   ├── aggregate/
│       │   │   │   └── RiskAssessmentTest.java
│       │   │   ├── service/
│       │   │   │   ├── RiskScoringServiceTest.java
│       │   │   │   ├── DecisionServiceTest.java
│       │   │   │   └── GeographicValidatorTest.java
│       │   │   └── valueobject/
│       │   │       └── LocationTest.java
│       │   │
│       │   └── infrastructure/                       # Infrastructure Layer Tests
│       │       ├── adapter/
│       │       │   ├── account/
│       │       │   │   └── AccountServiceRestAdapterIntegrationTest.java
│       │       │   ├── cache/
│       │       │   │   └── VelocityCounterAdapterIntegrationTest.java
│       │       │   ├── kafka/
│       │       │   │   ├── TransactionEventConsumerIntegrationTest.java
│       │       │   │   └── EventPublisherAdapterIntegrationTest.java
│       │       │   ├── ml/
│       │       │   │   └── SageMakerMLAdapterIntegrationTest.java
│       │       │   ├── persistence/
│       │       │   │   ├── RiskAssessmentRepositoryAdapterIntegrationTest.java
│       │       │   │   └── TransactionRepositoryAdapterIntegrationTest.java
│       │       │   └── rest/
│       │       │       └── FraudDetectionControllerIntegrationTest.java
│       │       │
│       │       └── config/
│       │           └── TestcontainersConfiguration.java
│       │
│       └── resources/
│           ├── application-test.yml                  # Test configuration
│           └── testcontainers.properties             # Testcontainers settings
│
├── build.gradle                                     # Gradle Build Configuration
├── settings.gradle                                  # Gradle Settings
├── Dockerfile                                       # Multi-stage Docker build
└── README.md                                        # Project Documentation (this document)
```

#### 3. **Domain-Driven Design (DDD)**

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
- **Context Mapping**: Integration with other contexts (Account Management Context, Transaction Processing Context, Case Management Context, and Machine Learning Context) via well-defined interfaces
- **Ubiquitous Language**: A shared vocabulary between domain experts and developers for the Fraud Detection bounded context.

#### 4. **Event-Driven Architecture**

Asynchronous communication via domain events and Kafka:

```
                                             ┌────────────────────────────────┐           
                                             │ Transaction Processing Context │            
                                             └────────────────────────────────┘      
                                                       Event Streaming              REST API
                                                              ↓                         ↓↑ 
                                      ┌─────────────────────────────────────────────────────────────────────────┐
                                      │                          Fraud Detection Context (this application)     │
                                      │ ┌─────────────────────────────────────────────────────────────────────┐ │
                                      │ │              Transaction Event   HTTP Assessment Request            │ │ 
                                      │ │                     ↓                       ↓                       │ │ 
                                      │ │              Kafka Consumer          REST Controller                │ │  
┌────────────────────────────┐        │ │                     ↓                       ↓                       │ │
│ Account Management Context │ REST → │ │ AccountProfile → Assess Transaction Risk Use Case                   │ │
└────────────────────────────┘        │ │       ┌─────── →               ↓                                    │ │ 
┌────────────────────────────┐      → │ │ MLPrediction              Publish Events                            │ │ 
│  Machine Learning Context  │ REST   │ │                                 ↓                                   │ │ 
└────────────────────────────┘      ← │ │                 ┌───────────────┴───────────────┐                   │ │ 
                                      │ │                 ↓                               ↓                   │ │ 
                                      │ │     RiskAssessmentCompleted           HighRiskDetected              │ │ 
                                      │ │                 ↓                               ↓                   │ │ 
                                      │ │ fraud-detection.risk-assessments   fraud-detection.high-risk-alerts │ │ 
                                      │ └─────────────────────────────────────────────────────────────────────┘ │
                                      └─────────────────────────────────────────────────────────────────────────┘
                                                                    Event Streaming                        
                                                                           ↓
                                                             ┌─────────────────────────┐
                                                             │ Case Management Context │
                                                             └─────────────────────────┘
```

**Locations:**
- **Event Publishing**: `infrastructure.adapter.kafka.EventPublisherAdapter`
- **Event Consumption**: `infrastructure.adapter.kafka.TransactionEventConsumer`
- **Domain Events**: `domain.event.*`

#### 5. **Account Service Integration**

The fraud detection service integrates with the Account Management bounded context through an anti-corruption layer:
```
Account Service (External) → AccountServicePort (Interface) → AccountServiceRestAdapter
                                      ↓
                              AccountProfile (Domain Model)
                                      ↓
                              ML Feature Extraction
```

**Privacy-by-Design**:
- Only fetches minimal data needed (home location)
- Caches profiles to minimize service calls
- Provides fallback when account service unavailable
- No PII stored in fraud detection database

**Resilience Features**:
- Circuit Breaker: Protects against cascading failures
- Retry: Automatic retry with exponential backoff
- Time Limiter: 3-second timeout
- Bulkhead: Limits concurrent calls to 25
- Redis Cache: Distributed caching for performance and scalability

#### 6. **CQRS (Command Query Responsibility Segregation)**

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

#### 7. **Layer Responsibilities**

**Application Layer** (`application/`):
- Use case orchestration
- Input/output port definitions
- DTOs for API contracts
- Application service implementations
- Commands and queries (CQS pattern)

**Domain Layer** (`domain/`):
- Core business logic
- Domain entities and aggregates
- Domain events
- Domain services
- Value objects
- Business rule enforcement

**Infrastructure Layer** (`infrastructure/`):
- Technical implementations of ports
- External system integrations
- Database persistence
- Messaging/event handling
- REST API controllers
- Configuration and wiring

#### 8. **Key Architectural Patterns**

**Hexagonal Architecture (Ports and Adapters)**:
- **Ports**: Interfaces defining contracts (`application.port.in` and `application.port.out`)
- **Adapters**: Implementations of ports (`infrastructure.adapter.*`)
- **Dependency Rule**: Dependencies point inward (Infrastructure → Application → Domain)

**Domain-Driven Design (DDD)**:
- **Aggregates**: `RiskAssessment` as aggregate root
- **Value Objects**: Immutable domain concepts (`Money`, `Location`, `RiskScore`)
- **Domain Events**: `RiskAssessmentCompleted`, `HighRiskDetected`
- **Repositories**: `RiskAssessmentRepository`, `TransactionRepository`

**Command Query Separation (CQS)**:
- **Commands**: Modify state (`AssessTransactionRiskCommand`, `ProcessTransactionCommand`)
- **Queries**: Read state (`GetRiskAssessmentQuery`, `FindRiskLeveledAssessmentsQuery`)

**Event-Driven Architecture**:
- **Event Sourcing**: Domain events published to Kafka
- **Event Handlers**: Kafka consumers process transaction events
- **Idempotency**: Duplicate detection via Redis cache

**Strategy Pattern**:
- **Decision Strategies**: Risk-level specific decision logic
- **Implementations**: `LowRiskStrategy`, `MediumRiskStrategy`, `HighRiskStrategy`, `CriticalRiskStrategy`

#### 9. **Testing Structure**

**Unit Tests**:
- Domain logic tests (pure business rules)
- Service tests with mocked dependencies
- Value object validation tests

**Integration Tests**:
- Full-stack tests with Testcontainers
- Database integration tests
- Kafka producer/consumer tests
- REST API endpoint tests
- External service integration tests (Account Service, ML Service)

**Test Utilities**:
- `TestDataFactory`: Centralized test data creation
- `TestcontainersConfiguration`: Shared container configuration
- Parallel test execution enabled in `build.gradle`

---
## Ubiquitous Language Glossary

A shared vocabulary between domain experts and developers for the Fraud Detection bounded context.

**Core Concepts**

- **Risk Assessment**: The complete evaluation of a transaction's fraud risk, combining ML predictions, rule evaluations, velocity checks, and geographic validation.  Results in a risk score, risk level, and decision.  Aggregate root in DDD terms.

- **Transaction**: A financial operation initiated by an account holder through various channels (card, online, mobile, POS, ATM).  Contains amount, merchant, location, device, and timestamp information.

- **Risk Score**: A numeric value from 0-100 representing the likelihood of fraud.  Calculated as a weighted composite of ML prediction and rule evaluation scores (default: 60% ML, 40% Rules).

- **Transaction Risk Level**: Strategic classification of risk assessment outcome into four categories:
   - **LOW** (0-40): Normal transaction behavior
   - **MEDIUM** (41-70): Unusual but potentially legitimate
   - **HIGH** (71-90): Suspicious activity requiring review
   - **CRITICAL** (91-100): Almost certainly fraudulent

- **Decision**: The final action taken based on risk assessment:
   - **ALLOW**: Permit transaction to proceed (LOW risk)
   - **CHALLENGE**: Request additional authentication (MEDIUM risk)
   - **REVIEW**: Flag for manual analyst review (HIGH risk)
   - **BLOCK**: Prevent transaction immediately (CRITICAL risk)

**Assessment Components**

- **ML Prediction**: Machine learning model output indicating fraud probability (0.0-1.0) with confidence score.  Generated by AWS SageMaker endpoint using transaction features and historical patterns.

- **Rule Evaluation**: Business rule check performed by Drools rule engine.  Each triggered rule contributes to the overall risk score based on its severity.

- **Rule Trigger**: A specific business rule that fired during evaluation, containing rule ID, name, severity, and the value that triggered it.

- **Rule Violation Severity**: The impact level of an individual rule violation:
   - **LOW** (10 points): Minor concern
   - **MEDIUM** (25 points): Moderate concern
   - **HIGH** (40 points): Serious concern
   - **CRITICAL** (60 points): Severe concern

- **Velocity Metrics**: Statistical measures of transaction frequency and patterns over time windows:
   - Transaction count per account
   - Total amount spent
   - Unique merchants visited
   - Unique locations used
   - Tracked across 5-minute, 1-hour, and 24-hour windows

- **Geographic Context**: Location-based risk indicators including:
   - **Impossible Travel**: Transaction locations that cannot be physically reached given the time between transactions (e.g., Johannesburg to New York in 2 seconds)
   - Distance from previous transaction
   - Required travel speed between locations
   - Threshold: 965 km/h (average commercial jet speed)

**Account & Identity**

- **Account Profile**: Information about the account holder from the Account Management bounded context, including:
   - Home location (for distance calculations)
   - Account creation date (for new account risk)
   - Minimal data following privacy-by-design principles

- **Device ID**: Unique identifier for the device used in the transaction.  Consistency across transactions indicates lower risk; absence or frequent changes indicate higher risk.

**Transaction Properties**

- **Money**: Financial value with currency.  Represented as decimal amount with ISO currency code (e.g., 100.00 USD, 85.50 EUR).

- **Location**: Geographic coordinates (latitude, longitude) with optional country and city.  Used for:
   - Distance calculations (Haversine formula)
   - Impossible travel detection
   - Cross-border transaction identification

- **Merchant**: Business entity accepting the transaction. Contains:
   - Merchant ID
   - Merchant name
   - Merchant category (GROCERY, RESTAURANT, ELECTRONICS, JEWELRY, CRYPTO, GIFT_CARDS, GAMBLING, etc.)
   - New merchant flag (first time account holder uses this merchant)

- **Channel**: The method through which the transaction was initiated:
   - **CARD**: Physical or virtual card transaction
   - **ONLINE**: E-commerce website
   - **MOBILE**: Mobile app
   - **POS**: Point of sale terminal
   - **ATM**: Automated teller machine

- **Transaction Type**: The nature of the financial operation:
   - **PURCHASE**: Goods or services acquisition
   - **ATM_WITHDRAWAL**: Cash withdrawal
   - **TRANSFER**: Peer-to-peer or account transfer
   - **PAYMENT**: Bill payment or recurring charge
   - **REFUND**: Return of funds

**Time Windows**

- **Time Window**: Predefined duration for velocity and pattern analysis:
   - **FIVE_MINUTES**: Short-term burst detection (5 minutes)
   - **ONE_HOUR**: Medium-term pattern detection (60 minutes)
   - **TWENTY_FOUR_HOURS**: Daily behavior baseline (24 hours)

**Events**

- **Risk Assessment Completed**: Domain event published when assessment finishes successfully.  Contains assessment ID, final risk score, risk level, and decision.  Consumed by downstream systems for transaction processing.

- **High Risk Detected**: Critical alert event published when risk level is HIGH or CRITICAL.  Triggers immediate notifications to fraud analysts and case management systems.

**Business Rules**

- **Amount Rules**: Threshold-based rules for transaction sizes:
   - **Large Amount**: > $10,000 (MEDIUM severity)
   - **Very Large Amount**: > $50,000 (HIGH severity)
   - **Excessively Large Amount**: > $100,000 (CRITICAL severity)

- **Velocity Rules**: Transaction frequency rules:
   - **Medium Velocity**: > 5 transactions in 5 minutes (MEDIUM severity)
   - **High Velocity**: > 20 transactions in 1 hour (HIGH severity)
   - **Excessive Velocity**: > 80 transactions in 24 hours (CRITICAL severity)

- **Geographic Rules**: Location-based rules:
   - **Impossible Travel**: Travel speed exceeds 965 km/h (CRITICAL severity)

**Integration Concepts**

- **Assessment ID**: Unique identifier (UUIDv7) for a risk assessment instance.  Time-ordered for efficient database indexing.

- **Transaction ID**: Unique identifier (UUIDv7) for a financial transaction.  Used to link assessments, events, and audit trails.

- **Idempotency**: Guarantee that processing the same transaction multiple times produces the same result.  Implemented via 48-hour Redis cache of seen transaction IDs.

- **Circuit Breaker**: Fault tolerance pattern that prevents cascading failures when external services (ML model, Account Service) are unavailable.  States: CLOSED (normal), OPEN (failing), HALF_OPEN (testing recovery).

**Quality Attributes**

- **Composite Score**: Final risk score calculated from multiple inputs:
```
  Final Score = (ML Probability × ML Weight) + (Rule Score × Rule Weight)
  Default: (ML × 0.6) + (Rules × 0.4)
```

- **Feature Extraction**: Process of transforming raw transaction data into ML model inputs:
   - Amount, transaction type, channel
   - Temporal features (hour, day of week, weekend flag)
   - Geographic features (domestic flag, distance from home)
   - Historical features (24h transaction count, 24h total amount, new merchant flag)
   - Device presence flag

- **Graceful Degradation**: System behavior when dependencies fail:
   - ML service unavailable → Use rules only (100% rule weight)
   - Account service unavailable → Use cached data or proceed without account context
   - Maintains availability over consistency when appropriate

**Assessment Lifecycle**

1. **Transaction Received**: Event consumed from Kafka topic `transactions.normalized`
2. **Duplicate Check**: Redis lookup for idempotency (48-hour window)
3. **Feature Gathering**: Parallel fetch of ML prediction, velocity metrics, geographic context
4. **Rule Evaluation**: Drools engine executes business rules
5. **Score Calculation**: Composite score computed from ML and rules
6. **Risk Classification**: Score mapped to risk level (LOW/MEDIUM/HIGH/CRITICAL)
7. **Decision Determination**: Strategy pattern applies risk-level-specific logic
8. **Invariant Validation**: Business rules enforced (e.g., CRITICAL must BLOCK)
9. **Persistence**: Assessment saved to PostgreSQL
10. **Event Publishing**: Domain events published to Kafka
11. **Velocity Update**: Transaction counters incremented in Redis (with TTL)

**Bounded Context Integration**

- **Account Management Context**: External system providing account holder information.  Anti-corruption layer implemented via `AccountServicePort` with circuit breaker and caching.

- **Transaction Processing Context**: External system generating transaction events.  Consumes normalized transaction events via Kafka.

- **Case Management Context**: Downstream system consuming high-risk alerts for manual review workflow.

- **Machine Learning Context**: AWS SageMaker endpoint providing fraud probability predictions.  Supports both cloud and local Docker deployment modes.

**Caching Strategy**

- **ML Predictions**: Cached unless fraud probability > 0.7 (high confidence fraud not cached)
- **Account Profiles**: Cached with circuit breaker fallback
- **Velocity Metrics**: Cached per account, evicted on counter updates
- **Seen Messages**: 48-hour TTL for duplicate detection
- **Cache Keys**: Hierarchical naming (e.g., `fraud-detection:mlPredictions::<transaction-id>`)

**Performance Concepts**

- **Throughput**: Target of 1,000 transactions/second sustained, 5,000 peak
- **Latency**: 95th percentile end-to-end assessment < 200ms
- **Availability**: 99.9% uptime with graceful degradation
- **Consumer Lag**: Kafka message backlog; should be < 100 messages
- **Connection Pool**: HikariCP database connection management (50 max, 10 min idle)
---
## Technology Stack

### Core Technologies

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Java** | 25 | Programming Language | Latest features, Virtual Threads for high concurrency, modern performance |
| **Spring Boot** | 4.0.1 | Application Framework | Latest release, mature ecosystem, auto-configuration, production-ready features |
| **PostgreSQL** | 16 | Relational Database | ACID compliance, JSONB support, excellent performance, proven reliability |
| **Redis** | 7.4 | In-Memory Cache | Millisecond latency, atomic operations for velocity counters, HyperLogLog for cardinality |
| **Apache Kafka** | 3.8.1 | Event Streaming | High-throughput, durable message queue, exactly-once semantics, KRaft mode (no Zookeeper) |
| **Drools** | 10.1.0 | Rule Engine | Externalized business rules, declarative DSL, dynamic rule updates without code changes |

### Machine Learning

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **AWS SageMaker** | SDK 2.28.11 | ML Inference | Managed ML endpoints, scalable, auto-scaling, production-grade monitoring |
| **XGBoost Model** | Custom | Fraud Detection | Proven accuracy for fraud detection, interpretable, fast inference |
| **Docker ML Model** | ghcr.io | Local Development | Containerized model for local testing, consistent environment, no AWS costs |
| **Resilience4j** | 2.2.0 | Fault Tolerance | Circuit breaker for ML calls, retry logic, bulkhead pattern, time limiter |

### Account Service Integration

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Mockoon** | latest | API Mocking | Mocks Account Management service for local testing, configurable responses, realistic scenarios |
| **Redis Cache** | (Spring Boot managed) | Profile Caching | Distributed caching for account profiles, ML predictions, and velocity metrics. Provides cache persistence and cluster support |
| **RestClient** | Spring Boot 4.0 | HTTP Integration | Modern replacement for RestTemplate, built-in resiliency |

### Infrastructure & DevOps

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Docker** | Latest | Containerization | Consistent environments across dev/test/prod, isolation, easy deployment |
| **Docker Compose** | Latest | Local Development | Multi-container orchestration, simplified local setup, declarative configuration |
| **GitHub Actions** | N/A | CI/CD Pipeline | Native GitHub integration, workflow automation, free for public repos, GHCR integration |
| **GitHub Container Registry** | N/A | Image Repository | Free, integrated with GitHub, supports multi-arch images (AMD64/ARM64) |
| **Testcontainers** | Latest | Integration Testing | Real dependencies in tests, reproducible environments, Docker-based, no mocking |

### Observability & Monitoring

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **OpenTelemetry** | Latest | Distributed Tracing | Vendor-neutral telemetry, traces requests across services, performance analysis |
| **Micrometer** | Latest | Metrics Collection | Vendor-neutral metrics facade, Prometheus format, rich metric types |
| **Grafana LGTM** | Latest | Unified Observability | All-in-one stack: Logs (Loki), Traces (Tempo), Metrics (Prometheus), Grafana UI |
| **Spring Boot Actuator** | 4.0.1 | Health & Metrics | Production-ready endpoints, health checks, metrics export, graceful shutdown |

### Security

| Technology | Version | Purpose | Reasoning                                                                     |
|------------|---------|---------|-------------------------------------------------------------------------------|
| **Keycloak** | 26.0.7 | Identity Provider | OAuth2/OIDC, fine-grained authorization, user management, realm import/export |
| **Spring Security** | 4.0.x | Application Security | OAuth2 Resource Server, method security, SASL/OAuth2 Bearer support           |
| **Strimzi OAuth** | 0.17.1 | Kafka OAuth | OAuth support for Kafka producers/consumers, token-based authentication       |

### Serialization & Schema Management

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Apache Avro** | Latest | Event Serialization | Schema evolution, compact binary format, type safety, backward/forward compatibility |
| **Apicurio Registry** | 2.6.13.Final | Schema Registry | Schema versioning, compatibility checks, centralized management, REST API, Web UI |

### Testing

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **JUnit 5** | 5.10.x | Unit Testing | Modern testing framework, parameterized tests, parallel execution, extensions |
| **Testcontainers** | Latest | Integration Testing | Real PostgreSQL, Kafka, Redis, Keycloak, ML Model in tests, no mocking required |
| **Testcontainers Keycloak** | 3.4.0 | OAuth Testing | Keycloak container for auth testing, realistic OAuth flows |
| **Awaitility** | 4.2.0 | Async Testing | Test async operations with timeout and polling, eventual consistency testing |

### Build & Development Tools

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **Gradle** | 9.x     | Build Tool | Fast incremental builds, dependency management, parallel execution, plugin ecosystem |
| **MapStruct** | 1.6.3   | Object Mapping | Compile-time mapping generation, type-safe, no reflection overhead |
| **Lombok** | Latest  | Boilerplate Reduction | @Builder, @Data, @Slf4j, reduces code verbosity, improved readability |
| **Flyway** | Latest  | Database Migration | Version-controlled schema changes, repeatable migrations, rollback support |
| **Avro Plugin** | 1.9.1   | Code Generation | Generates Java classes from Avro schemas, type-safe serialization |

### API Documentation

| Technology | Version | Purpose | Reasoning |
|------------|---------|---------|-----------|
| **SpringDoc OpenAPI** | 3.0.0 | API Documentation | Auto-generates OpenAPI 3.0 spec, Swagger UI, OAuth2 integration |

---

## Getting Started

### Prerequisites

**Required Software:**
- **JDK 25** (GraalVM recommended, or Eclipse Temurin)
- **Docker** (v20.10+) & **Docker Compose** (v2.0+)
- **Git**
- **Gradle** (or use `./gradlew` wrapper)
- **curl** (for testing)
- **jq** (optional, for JSON parsing)

**System Requirements:**
- **CPU**: 4+ cores recommended
- **RAM**: 8GB minimum, 16GB recommended
- **Disk**: 20GB free space (for Docker images and build artifacts)

### Quick Start (5 Minutes)

The fastest way to get the entire system running:

```bash
# 1. Clone repository
git clone https://github.com/itumelengManota/fraud-detection-service.git
cd fraud-detection-service

# 2. Start all infrastructure services (includes ML model, Mockoon, etc.)
docker-compose -f docker-compose/compose.yml up -d

# 3. Wait for services to be healthy (~30 seconds)
docker-compose -f docker-compose/compose.yml ps

# 4. Build and run the application
./gradlew bootRun
```

The application will start on **http://localhost:9001** 🚀

### Detailed Setup

#### Step 1: Clone the Repository

```bash
git clone https://github.com/itumelengManota/fraud-detection-service.git
cd fraud-detection-service
```

#### Step 2: Start Infrastructure Services

```bash
docker-compose -f docker-compose/compose.yml up -d
```

This starts **10 services**:

| Service | Port | Description | Status Check |
|---------|------|-------------|--------------|
| **postgres** | 5432 | PostgreSQL database | `docker exec fraud-detection-postgres pg_isready` |
| **sagemaker-model** | 8080 | ML model (local mode) | `curl http://localhost:8080/ping` |
| **mockoon** | 3001 | Account Service mock | `curl http://localhost:3001/accounts/ACC-12345-1/profiles` |
| **redis** | 6379 | Cache & velocity counters | `docker exec fraud-detection-redis redis-cli ping` |
| **kafka** | 9092 | Event streaming (KRaft) | `docker exec fraud-detection-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092` |
| **apicurio-registry** | 8081 | Schema registry API | `curl http://localhost:8081/apis/registry/v2/search/artifacts` |
| **apicurio-registry-ui** | 8082 | Schema registry UI | Navigate to http://localhost:8082 |
| **kafka-ui** | 8083 | Kafka management UI | Navigate to http://localhost:8083 |
| **keycloak** | 8180 | OAuth2/OIDC provider | `curl http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration` |
| **grafana-lgtm** | 3000, 4317, 4318 | Observability stack | Navigate to http://localhost:3000 |

**Wait for all services to be healthy:**

```bash
# Check all services
docker-compose -f docker-compose/compose.yml ps

# All services should show "healthy" or "running"
```

**Common startup times:**
- PostgreSQL: ~5 seconds
- Redis: ~2 seconds
- Kafka: ~10 seconds
- Keycloak: ~15 seconds
- ML Model: ~10 seconds
- Mockoon: ~2 seconds

#### Step 3: Verify Infrastructure

Run these commands to verify each service:

```bash
# 1. PostgreSQL
docker exec fraud-detection-postgres psql -U postgres -d fraud_detection -c "SELECT 1"

# 2. Redis
docker exec fraud-detection-redis redis-cli ping
# Expected: PONG

# 3. ML Model
curl http://localhost:8080/ping
# Expected: 200 OK (empty body)

# 4. Mockoon (Account Service)
curl http://localhost:3001/accounts/ACC-12345-1/profiles
# Expected: JSON with homeLocation

# 5. Kafka
docker exec fraud-detection-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
# Expected: List of topics

# 6. Apicurio Registry
curl http://localhost:8081/apis/registry/v2/search/artifacts
# Expected: JSON artifact list

# 7. Keycloak
curl http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration
# Expected: JSON OpenID configuration

# 8. Grafana
curl http://localhost:3000/api/health
# Expected: {"commit":"...","database":"ok","version":"..."}
```

#### Step 4: Build the Application

```bash
# Clean build with tests
./gradlew clean build
```

**Build Output:**
```
BUILD SUCCESSFUL in 2m 15s
12 actionable tasks: 12 executed
```

**Note on ML Model Image:**
The build process will check for the ML model Docker image (`fraud-detection-model:latest`). If not found, it will attempt to pull from GitHub Container Registry. If the pull fails, you'll see a warning with instructions to build locally.

**To manually pull the ML model image:**
```bash
# Option 1: Pull from GitHub Container Registry (recommended)
docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest
docker tag ghcr.io/itumelengmanota/fraud-detection-model:latest fraud-detection-model:latest

# Option 2: Build locally
https://github.com/itumelengManota/fraud-detection-ml-model.git
cd fraud-detection-ml-model/scripts
./download_model_from_s3.sh
./build_docker_image.sh
```

#### Step 5: Run the Application

```bash
# Run with Gradle
./gradlew bootRun

# Or run the JAR directly
java -jar build/libs/fraud-detection-service-0.0.1-SNAPSHOT.jar
```

**Startup Output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.0.1)

2026-01-06T16:00:00.000Z  INFO 1 --- [           main] c.t.f.FraudDetectionServiceApplication  : Starting FraudDetectionServiceApplication
2026-01-06T16:00:05.000Z  INFO 1 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 9001 (http)
2026-01-06T16:00:05.100Z  INFO 1 --- [           main] c.t.f.FraudDetectionServiceApplication  : Started FraudDetectionServiceApplication in 5.234 seconds
```

The application is now running on **http://localhost:9001** ✅

#### Step 6: Verify Application Health

```bash
# Health check
curl http://localhost:9001/actuator/health
# Expected: {"status":"UP"}

# Readiness check
curl http://localhost:9001/actuator/health/readiness
# Expected: {"status":"UP"}

# Liveness check
curl http://localhost:9001/actuator/health/liveness
# Expected: {"status":"UP"}
```

### Development Mode Features

When running in development mode (`./gradlew bootRun`), you get:

1. **Hot Reload**: Spring DevTools enabled for automatic restarts on code changes
2. **Docker Compose Integration**: Automatic service discovery
3. **Enhanced Logging**: Debug-level logging for development
4. **Actuator Endpoints**: Full actuator endpoints enabled
5. **Swagger UI**: Interactive API documentation

### Accessing the Application

| Service | URL | Credentials |
|---------|-----|-------------|
| **Application** | http://localhost:9001 | N/A (OAuth required) |
| **Swagger UI** | http://localhost:9001/swagger-ui.html | OAuth via Keycloak |
| **OpenAPI Spec** | http://localhost:9001/v3/api-docs | Public |
| **Health Check** | http://localhost:9001/actuator/health | Public |
| **Metrics** |  | Public |
| **Keycloak Admin** | http://localhost:8180/admin | admin / admin |
| **Kafka UI** | http://localhost:8083 | Public |
| **Apicurio UI** | http://localhost:8082 | Public |
| **Grafana** | http://localhost:3000 | admin / admin |

### Testing the Setup

#### 1. Get an OAuth Token

```bash
export TOKEN=$(curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fraud-detection-web" \
  -d "client_secret=fraud-detection-web-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "scope=openid fraud-detection-scopes" | jq -r '.access_token')

echo "Token: $TOKEN"
```

#### 2. Test Risk Assessment

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "00000000-0000-0000-0000-000000000001",
    "accountId": "ACC-12345-1",
    "amount": 100.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "merchantId": "MERCHANT-TEST",
    "merchantName": "Test Merchant",
    "merchantCategory": "E-COMMERCE",
    "location": {
      "latitude": -26.2041,
      "longitude": 28.0473,
      "country": "ZA",
      "city": "Johannesburg",
      "timestamp": "2026-01-06T16:00:00Z"
    },
    "deviceId": "DEVICE-TEST",
    "transactionTimestamp": "2026-01-06T16:00:00Z"
  }'
```

**Expected Response:**
```json
{
  "assessmentId": "...",
  "transactionId": "00000000-0000-0000-0000-000000000001",
  "riskScore": 25,
  "transactionRiskLevel": "LOW",
  "decision": "ALLOW",
  "assessmentTime": "2026-01-06T16:00:01.234Z"
}
```

#### 3. Test Account Service Integration

The transaction above uses `ACC-12345-1`, which maps to a Johannesburg location in Mockoon. Since the transaction is also in Johannesburg, the distance will be low, contributing to a lower risk score.

**Test with far location:**
```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "00000000-0000-0000-0000-000000000002",
    "accountId": "ACC-12345-1",
    "amount": 500.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "merchantId": "MERCHANT-TOKYO",
    "merchantName": "Tokyo Store",
    "merchantCategory": "RETAIL",
    "location": {
      "latitude": 35.6762,
      "longitude": 139.6503,
      "country": "JP",
      "city": "Tokyo",
      "timestamp": "2026-01-06T16:00:00Z"
    },
    "deviceId": "DEVICE-JP",
    "transactionTimestamp": "2026-01-06T16:00:00Z"
  }'
```

**Expected**: Higher risk score due to large distance from home location (~13,000 km).

#### 4. View in Kafka UI

1. Navigate to http://localhost:8083
2. Click "Topics"
3. View `fraud-detection.risk-assessments` topic
4. You should see the assessment events

#### 5. View Observability

**Grafana (Logs, Traces, Metrics):**
1. Navigate to http://localhost:3000
2. Login: admin / admin
3. Explore → Loki (Logs) / Tempo (Traces) / Prometheus (Metrics)

**Application Metrics:**
```bash
curl http://localhost:9001/actuator/prometheus | grep sagemaker
```

### Troubleshooting Setup

#### Issue: ML Model Image Not Found

**Error**: `fraud-detection-model:latest image not found`

**Solution**:
```bash
docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest
docker tag ghcr.io/itumelengmanota/fraud-detection-model:latest fraud-detection-model:latest
```

#### Issue: Port Already in Use

**Error**: `Bind for 0.0.0.0:9001 failed: port is already allocated`

**Solution**:
```bash
# Find process using port
lsof -i :9001

# Kill process
kill -9 <PID>

# Or change application port
export SERVER_PORT=9002
./gradlew bootRun
```

#### Issue: Keycloak Not Starting

**Error**: Keycloak container exits immediately

**Solution**:
```bash
# Check logs
docker logs fraud-detection-keycloak

# Restart with fresh data
docker-compose -f docker-compose/compose.yml down -v
docker-compose -f docker-compose/compose.yml up -d keycloak
```

#### Issue: Database Connection Failed

**Error**: `Connection to localhost:5432 refused`

**Solution**:
```bash
# Check PostgreSQL
docker exec fraud-detection-postgres pg_isready

# Restart PostgreSQL
docker-compose -f docker-compose/compose.yml restart postgres

# View logs
docker logs fraud-detection-postgres
```

### Development Workflow

1. **Make code changes**
2. **Application auto-restarts** (DevTools)
3. **Test via Swagger UI** or cURL
4. **View logs** in console or Grafana
5. **Debug with IntelliJ/VS Code** (Remote debug port: 5005)

### Stopping the Application

```bash
# Stop application (Ctrl+C if using bootRun)

# Stop all Docker services
docker-compose -f docker-compose/compose.yml down

# Stop and remove volumes (clean slate)
docker-compose -f docker-compose/compose.yml down -v
```

### Next Steps

Now that your environment is set up:

1. **Explore the API**: Visit http://localhost:9001/swagger-ui.html
2. **Run Integration Tests**: `./gradlew test`
3. **Monitor with Grafana**: http://localhost:3000
4. **Browse Kafka Topics**: http://localhost:8083
5. **Read the Full Documentation**: See sections below for detailed guides

---
## Machine Learning Integration

The fraud detection service uses an XGBoost-based machine learning model for fraud prediction. The system supports two deployment modes:

### Deployment Modes

#### 1. **Local Mode (Docker)** - Recommended for Development

Uses a containerized ML model that runs locally via Docker.

**Benefits:**
- ✅ **Free**: No AWS costs
- ✅ **Fast**: No network latency
- ✅ **Portable**: Works on any machine with Docker
- ✅ **Consistent**: Same model used in CI/CD and local testing

**Configuration:**
```yaml
aws:
  region: us-east-1
  sagemaker:
    local-mode: true
    endpoint-url: http://sagemaker-model:8080/invocations
    api-call-timeout: 10s
```

**Docker Compose Service:**
```yaml
sagemaker-model:
  image: ghcr.io/itumelengmanota/fraud-detection-model:latest
  container_name: fraud-detection-model
  ports:
    - '8080:8080'
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/ping"]
    interval: 30s
    timeout: 10s
    retries: 3
```

**Starting the Model:**
```bash
# Start all services including the ML model
docker-compose -f docker-compose/compose.yml up -d sagemaker-model

# Verify the model is running
curl http://localhost:8080/ping
# Expected: 200 OK

# Test prediction
curl -X POST http://localhost:8080/invocations \
  -H 'Content-Type: application/json' \
  -d '{
    "amount": 100.0,
    "hour": 14,
    "day_of_week": 1,
    "merchant_category": 3,
    "transaction_type": 0,
    "channel": 1,
    "is_domestic": 1,
    "is_weekend": 0,
    "has_device": 1,
    "transactions_last_24h": 2,
    "amount_last_24h": 150.0,
    "new_merchant": 0,
    "distance_from_home": 10.5
  }'
```

#### 2. **Cloud Mode (AWS SageMaker)** - Production

Deploys the model to AWS SageMaker for production use.

**Benefits:**
- ✅ **Scalable**: Auto-scaling based on load
- ✅ **Managed**: AWS handles infrastructure
- ✅ **Production-ready**: Built-in monitoring and logging

**Configuration:**
```yaml
aws:
  region: us-east-1
  sagemaker:
    local-mode: false
    endpoint-name: fraud-detection-endpoint
    model-version: 1.0.0
    api-call-timeout: 2s
```

**⚠️ Cost Warning:**
- Endpoint: ~$0.50/hour while running
- Training: ~$0.10-0.50 per run
- Always delete endpoints when not in use:
```bash
aws sagemaker delete-endpoint --endpoint-name fraud-detection-endpoint
aws sagemaker delete-endpoint-config --endpoint-config-name fraud-detection-endpoint
```

### Model Features

The ML model uses 13 features for fraud prediction:

| Feature | Description | Type |
|---------|-------------|------|
| `amount` | Transaction amount | Numeric |
| `hour` | Hour of day (0-23) | Numeric |
| `day_of_week` | Day of week (0-6) | Numeric |
| `merchant_category` | Merchant category code | Numeric |
| `transaction_type` | Transaction type code | Numeric |
| `channel` | Transaction channel code | Numeric |
| `is_domestic` | Domestic transaction (1/0) | Binary |
| `is_weekend` | Weekend transaction (1/0) | Binary |
| `has_device` | Device ID present (1/0) | Binary |
| `transactions_last_24h` | Transaction count (24h) | Numeric |
| `amount_last_24h` | Total amount (24h) | Numeric |
| `new_merchant` | New merchant for account (1/0) | Binary |
| `distance_from_home` | Distance from home (km) | Numeric |

### ML Model CI/CD

The ML model image is automatically built and published via GitHub Actions:

**Workflow**: `.github/workflows/build-model-image.yml`

**Triggers:**
- Push to `main` branch (changes in `scripts/` directory)
- Manual workflow dispatch
- New release publication

**Image Registry:**
- Repository: `ghcr.io/itumelengmanota/fraud-detection-model`
- Tags: `latest`, version tags, branch tags, commit SHA tags

**Pulling the Image:**
```bash
# Authenticate with GitHub Container Registry (if private)
echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin

# Pull the latest image
docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest

# Pull specific version
docker pull ghcr.io/itumelengmanota/fraud-detection-model:1.0.0
```

### Resilience Configuration

The ML adapter includes comprehensive resilience features:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      sagemakerML:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
  
  retry:
    instances:
      sagemakerML:
        max-attempts: 2
        wait-duration: 50ms
        enable-exponential-backoff: true
  
  timelimiter:
    instances:
      sagemakerML:
        timeout-duration: 500ms
  
  bulkhead:
    instances:
      sagemakerML:
        max-concurrent-calls: 50
```

### Switching Between Modes

**Development/Testing** → Use Local Mode:
```bash
# docker-compose/compose.yml already includes the model service
docker-compose up -d
```

**Production** → Use Cloud Mode:
```bash
# Deploy model to SageMaker (from ML project)
cd /path/to/fraud-detection-ml-model/scripts
python train_and_deploy_sagemaker.py --role-arn <YOUR_ROLE_ARN>

# Update application.yml
aws:
  sagemaker:
    local-mode: false
    endpoint-name: fraud-detection-endpoint
```

---
## Account Service Integration

The fraud detection service integrates with the Account Management bounded context to retrieve customer home location for distance-based fraud detection.

### Integration Architecture

```
Fraud Detection Service
         ↓
   AccountServicePort (Interface)
         ↓
   AccountServiceRestAdapter
         ↓ (HTTP REST)
   Account Management Service
```

### Mock Service for Testing (Mockoon)

For local development and testing, a **Mockoon** service mocks the Account Management API.

**Docker Compose Service:**
```yaml
mockoon:
  image: 'mockoon/cli:latest'
  ports:
    - '3001:3000'
  restart: unless-stopped
  volumes:
    - ./mockoon/data.json:/data/data.json
  command: ['--data', '/data/data.json']
```

**Base URL**: `http://localhost:3001`

**Mock Endpoints:**

1. **Get Account Profile**
   ```
   GET /accounts/:accountId/profiles
   ```

   **Test Accounts:**
   
   | Account ID | Location | Response |
   |------------|----------|----------|
   | `ACC-12345-1` | Johannesburg, ZA | 200 OK |
   | `ACC-12345-2` | London, UK | 200 OK |
   | `ACC-12345-3` | N/A | 404 Not Found |
   | `ACC-12345-4` | N/A | 500 Internal Server Error |
   | `ACC-TIMEOUT` | Johannesburg, ZA | 200 OK (5s delay) |
   | `ACC-CIRCUIT-BREAKER` | N/A | 503 Service Unavailable |
   | `ACC-SLOW` | Johannesburg, ZA | 200 OK (2.5s delay) |
   | `ACC-RETRY` | Johannesburg, ZA | 200 OK (after retry) |
   | `ACC-BULKHEAD` | Johannesburg, ZA | 200 OK (1s delay) |

   **Example Response (200 OK):**
   ```json
   {
     "accountId": "ACC-12345-1",
     "homeLocation": {
       "latitude": -26.2041,
       "longitude": 28.0473,
       "country": "ZA",
       "city": "Johannesburg"
     },
     "accountCreatedAt": "2020-01-15T00:00:00Z"
   }
   ```

**Testing the Mock Service:**
```bash
# Test successful response
curl http://localhost:3001/accounts/ACC-12345-1/profiles

# Test 404 response
curl http://localhost:3001/accounts/ACC-12345-3/profiles

# Test timeout scenario
curl http://localhost:3001/accounts/ACC-TIMEOUT/profiles

# Test circuit breaker trigger
for i in {1..10}; do 
  curl http://localhost:3001/accounts/ACC-CIRCUIT-BREAKER/profiles
done
```

### Resilience Features

The Account Service adapter includes comprehensive resilience:

**Circuit Breaker:**
- Opens after 50% failure rate
- Half-open after 30 seconds
- Protects against cascading failures

**Retry:**
- 3 attempts with exponential backoff
- Retries on connection errors

**Time Limiter:**
- 3-second timeout per request
- Prevents hanging calls

**Bulkhead:**
- Maximum 25 concurrent calls
- Prevents resource exhaustion

**Caching:**
- Redis cache for successful lookups with 48-hour TTL
- Falls back to cache on failures
- Configurable TTL (default: 48 hours)

### Production Configuration

For production, configure the actual Account Service URL:

```yaml
account-service:
  base-url: ${ACCOUNT_SERVICE_URL:https://account-service.prod.example.com}

resilience4j:
  circuitbreaker:
    instances:
      accountService:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  
  retry:
    instances:
      accountService:
        max-attempts: 3
        wait-duration: 100ms
  
  timelimiter:
    instances:
      accountService:
        timeout-duration: 3s
```

---
## Authentication & Authorization

### OAuth2 Architecture Overview

The service uses **Keycloak** for OAuth2/OIDC authentication with proper separation between:
- **Web Client**: User authentication (Swagger UI, web applications)
- **Kafka Client**: Service-to-service authentication (Kafka broker with SASL/OAUTHBEARER)
- **Resource Server**: JWT token validation (fraud-detection-service)

### Keycloak Configuration

**Realm**: `fraud-detection`  
**Authorization Server**: `http://localhost:8180/realms/fraud-detection`  
**Token Endpoint**: `http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token`

### Pre-configured Clients

#### 1. **fraud-detection-service** (Resource Server)
- **Type**: Bearer-only client
- **Purpose**: Validates JWT tokens from both web and Kafka clients
- **Secret**: `fraud-detection-secret`
- **Features**: Token validation only, no user interaction

#### 2. **fraud-detection-web** (Web Client)
- **Type**: Confidential client
- **Grant Types**: Authorization Code, Password (dev/test only)
- **Purpose**: User authentication for Swagger UI and web applications
- **Secret**: `fraud-detection-web-secret`
- **Redirect URIs**: `http://localhost:9001/*`, `http://localhost:3000/*`
- **Scopes**: openid, profile, email, fraud-detection-scopes
- **Token Lifespan**: 30 minutes (with refresh)

#### 3. **fraud-detection-kafka** (Service Client)
- **Type**: Confidential client
- **Grant Types**: Client Credentials ONLY
- **Purpose**: Service-to-service authentication for Kafka broker
- **Secret**: `fraud-detection-kafka-secret`
- **Scopes**: kafka
- **Service Account**: Enabled
- **Token Lifespan**: 60 minutes (no refresh, auto-renewed)

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

### Application Configuration

#### Spring Security (REST API)
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/fraud-detection
          jwk-set-uri: http://localhost:8180/realms/fraud-detection/protocol/openid-connect/certs
          audiences:
            - fraud-detection-service
            - fraud-detection-web
            - account
```

#### Kafka Security (SASL/OAUTHBEARER)
```yaml
spring:
  kafka:
    security:
      protocol: SASL_PLAINTEXT  # Use SASL_SSL in production
    
    properties:
      sasl:
        mechanism: OAUTHBEARER
        jaas:
          config: >
            org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required
            clientId='fraud-detection-kafka'
            clientSecret='fraud-detection-kafka-secret'
            scope='kafka'
            tokenEndpointUri='http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token';
```

#### Swagger UI OAuth
```yaml
springdoc:
  swagger-ui:
    oauth:
      client-id: fraud-detection-web
      client-secret: fraud-detection-web-secret
      use-pkce-with-authorization-code-grant: true
```

### Obtaining Access Tokens

#### Method 1: Password Grant (for testing)

```bash
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fraud-detection-web" \
  -d "client_secret=fraud-detection-web-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "scope=openid fraud-detection-scopes"
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 1800,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "scope": "openid fraud-detection-scopes"
}
```

#### Method 2: Client Credentials Grant (for service-to-service)

```bash
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=fraud-detection-kafka" \
  -d "client_secret=fraud-detection-kafka-secret" \
  -d "scope=kafka"
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 3600,
  "refresh_expires_in": 0,
  "token_type": "Bearer",
  "scope": "kafka"
}
```

#### Method 3: Authorization Code Flow (for web applications)

1. Redirect user to authorization endpoint:
```
http://localhost:8180/realms/fraud-detection/protocol/openid-connect/auth?
  response_type=code&
  client_id=fraud-detection-web&
  redirect_uri=http://localhost:9001/callback&
  scope=fraud:detect fraud:read&
  state=random-state-value
```

2. After user login, exchange code for token:
```bash
curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "client_id=fraud-detection-web" \
  -d "client_secret=fraud-detection-web-secret" \
  -d "code=<authorization_code>" \
  -d "redirect_uri=http://localhost:9001/callback"
```

### Token Validation

Tokens are validated by the application using:
- **Signature verification**: RSA256 using JWKS from Keycloak
- **Audience validation**: Must contain `fraud-detection-service`, `fraud-detection-web`, or `account`
- **Issuer validation**: Must be from `fraud-detection` realm
- **Expiration check**: Web tokens expire after 30 minutes, Kafka tokens after 60 minutes

### Security Configuration

**Endpoint Protection:**
```java
POST /fraud/assessments → Requires SCOPE_fraud:detect
GET /fraud/assessments/** → Requires SCOPE_fraud:read
/actuator/health, /actuator/info → Public
/swagger-ui/**, /v3/api-docs/** → Public
```

**Location**: `infrastructure.config.SecurityConfig`

### Environment Variables

#### Development
```bash
# Keycloak
export JWT_ISSUER_URI=http://localhost:8180/realms/fraud-detection

# Kafka OAuth
export KAFKA_CLIENT_ID=fraud-detection-kafka
export KAFKA_CLIENT_SECRET=fraud-detection-kafka-secret
```

#### Production
```bash
# Keycloak
export JWT_ISSUER_URI=https://keycloak.prod.example.com/realms/fraud-detection
export JWT_JWK_SET_URI=https://keycloak.prod.example.com/realms/fraud-detection/protocol/openid-connect/certs

# Kafka OAuth
export KAFKA_CLIENT_ID=fraud-detection-kafka
export KAFKA_CLIENT_SECRET=${VAULT_KAFKA_CLIENT_SECRET}
export KAFKA_SECURITY_PROTOCOL=SASL_SSL

# Use secret manager to inject these at runtime
```

### Security Best Practices

1. **Never commit secrets**: Use environment variables or secret management (Vault, AWS Secrets Manager)
2. **Rotate secrets regularly**: Minimum quarterly rotation for client secrets
3. **Use SASL_SSL in production**: Not SASL_PLAINTEXT
4. **Enable PKCE**: For Authorization Code flow (already configured)
5. **Limit token lifespans**: Shorter lifespans reduce risk of token theft
6. **Monitor failed authentications**: Set up alerts for repeated failures

### Testing Authentication

#### Unit/Integration Tests
Tests use `application-test.yml` with Kafka OAuth disabled (PLAINTEXT).

#### Swagger UI Testing
1. Navigate to http://localhost:9001/swagger-ui.html
2. Click "Authorize"
3. Credentials:
   - Client ID: fraud-detection-web
   - Client Secret: fraud-detection-web-secret
4. Login with: detector / detector123

---
## API Reference

### Base URL
```
http://localhost:9001
```

### Authentication

All endpoints (except actuator and documentation endpoints) require OAuth2 authentication. Include the access token in the Authorization header:

```
Authorization: Bearer <access_token>
```

See [Authentication & Authorization](#authentication--authorization) section for obtaining tokens.

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
    "city": "New York"
  },
  "deviceId": "DEVICE-12345",
  "transactionTimestamp": "2024-12-17T10:00:00Z"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `transactionId` | UUID | Yes | Not null | Unique transaction identifier |
| `accountId` | String | Yes | Not null | Customer account identifier |
| `amount` | BigDecimal | Yes | Not null | Transaction amount |
| `currency` | String | Yes | Not null | ISO 4217 currency code (e.g., USD, EUR) |
| `type` | String | Yes | Not blank | Transaction type (PURCHASE, WITHDRAWAL, TRANSFER, etc.) |
| `channel` | String | Yes | Not blank | Transaction channel (ONLINE, POS, ATM, MOBILE) |
| `merchantId` | String | No | - | Merchant identifier |
| `merchantName` | String | No | - | Merchant business name |
| `merchantCategory` | String | No | - | Merchant category code |
| `location` | Object | No | Valid | Transaction location details |
| `location.latitude` | Double | Yes* | Not null | GPS latitude coordinate |
| `location.longitude` | Double | Yes* | Not null | GPS longitude coordinate |
| `location.country` | String | Yes* | Valid ISO-3166-1 | ISO 3166-1 alpha-2 country code |
| `location.city` | String | Yes* | Not blank | City name |
| `deviceId` | String | No | - | Device identifier (fingerprint, IMEI, etc.) |
| `transactionTimestamp` | ISO-8601 | Yes | Not null | Transaction timestamp in UTC |

*Required if location object is provided

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

**Response Fields:**

| Field | Type | Description | Possible Values |
|-------|------|-------------|-----------------|
| `assessmentId` | UUID | Unique assessment identifier | - |
| `transactionId` | UUID | Transaction identifier (matches request) | - |
| `riskScore` | Integer | Composite risk score (0-100) | 0 = lowest risk, 100 = highest risk |
| `transactionRiskLevel` | String | Risk classification | LOW, MEDIUM, HIGH, CRITICAL |
| `decision` | String | Recommended action | ALLOW, CHALLENGE, REVIEW, BLOCK |
| `assessmentTime` | ISO-8601 | When assessment was completed | - |

**Risk Level to Decision Mapping:**

| Risk Level | Score Range | Decision | Description |
|------------|-------------|----------|-------------|
| LOW | 0-40 | ALLOW | Proceed with transaction |
| MEDIUM | 41-70 | CHALLENGE | Request additional authentication (2FA, OTP) |
| HIGH | 71-90 | REVIEW | Manual review by fraud analyst |
| CRITICAL | 91-100 | BLOCK | Immediate transaction rejection |

**Error Responses:**

**400 Bad Request** - Invalid request data:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/fraud/assessments",
  "errors": [
    {
      "field": "transactionId",
      "message": "Transaction ID cannot be null"
    },
    {
      "field": "amount",
      "message": "Amount cannot be null"
    }
  ]
}
```

**401 Unauthorized** - Missing or invalid token:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/fraud/assessments"
}
```

**403 Forbidden** - Insufficient permissions:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied. Required scope: SCOPE_fraud:detect",
  "path": "/fraud/assessments"
}
```

**422 Unprocessable Entity** - Business rule violation:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Critical risk must result in BLOCK decision",
  "path": "/fraud/assessments"
}
```

**429 Too Many Requests** - Rate limit exceeded:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 60 seconds.",
  "path": "/fraud/assessments"
}
```

**500 Internal Server Error** - Unexpected error:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "path": "/fraud/assessments"
}
```

---

#### 2. **Get Risk Assessment**

**Endpoint**: `GET /fraud/assessments/{transactionId}`  
**Description**: Retrieves a previously completed risk assessment  
**Authorization**: Requires `SCOPE_fraud:read`

**Path Parameters:**
- `transactionId` (UUID, required): Transaction identifier

**Example Request:**
```bash
curl -X GET http://localhost:9001/fraud/assessments/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $TOKEN"
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

**404 Not Found** - Assessment not found:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Risk assessment not found for transaction: 550e8400-e29b-41d4-a716-446655440000",
  "path": "/fraud/assessments/550e8400-e29b-41d4-a716-446655440000"
}
```

**401 Unauthorized** - Missing or invalid token (same as above)

**403 Forbidden** - Insufficient permissions (requires `fraud:read` scope)

**500 Internal Server Error** - Unexpected error (same as above)

---

#### 3. **Search Risk Assessments**

**Endpoint**: `GET /fraud/assessments`  
**Description**: Search risk assessments with filters and pagination  
**Authorization**: Requires `SCOPE_fraud:read`

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `transactionRiskLevels` | String[] | No | All levels | Filter by risk levels (LOW, MEDIUM, HIGH, CRITICAL) |
| `fromDate` | ISO-8601 | No | No filter | Filter assessments after this timestamp |
| `page` | Integer | No | 0 | Page number (0-indexed) |
| `size` | Integer | No | 20 | Page size (max 100) |
| `sort` | String | No | assessmentTime,desc | Sort specification (field,direction) |

**Example Requests:**

1. **Get all assessments (paginated):**
```bash
curl -X GET "http://localhost:9001/fraud/assessments?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

2. **Filter by risk level:**
```bash
curl -X GET "http://localhost:9001/fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

3. **Filter by date:**
```bash
curl -X GET "http://localhost:9001/fraud/assessments?fromDate=2024-12-01T00:00:00Z&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

4. **Combined filters with custom sort:**
```bash
curl -X GET "http://localhost:9001/fraud/assessments?transactionRiskLevels=HIGH&fromDate=2024-12-01T00:00:00Z&page=0&size=10&sort=riskScore,desc" \
  -H "Authorization: Bearer $TOKEN"
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
    },
    {
      "assessmentId": "8d1e7789-8526-51ef-b958-f18gd2g01bf8",
      "transactionId": "661f9511-f39c-52e5-b827-557766551111",
      "riskScore": 92,
      "transactionRiskLevel": "CRITICAL",
      "decision": "BLOCK",
      "assessmentTime": "2024-12-17T09:55:30.123Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 150,
  "totalPages": 8,
  "last": false,
  "first": true,
  "size": 20,
  "number": 0,
  "sort": {
    "sorted": true,
    "unsorted": false,
    "empty": false
  },
  "numberOfElements": 20,
  "empty": false
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `content` | Array | List of risk assessments for current page |
| `pageable.pageNumber` | Integer | Current page number (0-indexed) |
| `pageable.pageSize` | Integer | Number of items per page |
| `totalElements` | Long | Total number of assessments matching filters |
| `totalPages` | Integer | Total number of pages |
| `last` | Boolean | Whether this is the last page |
| `first` | Boolean | Whether this is the first page |
| `size` | Integer | Page size |
| `number` | Integer | Current page number |
| `numberOfElements` | Integer | Number of elements in current page |
| `empty` | Boolean | Whether the page is empty |

**Sorting Options:**

| Field | Description | Example |
|-------|-------------|---------|
| `assessmentTime` | Sort by assessment timestamp | `sort=assessmentTime,desc` |
| `riskScore` | Sort by risk score | `sort=riskScore,asc` |
| `transactionRiskLevel` | Sort by risk level | `sort=transactionRiskLevel,desc` |

**Error Responses:**

**400 Bad Request** - Invalid query parameters:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid risk level: SUPER_HIGH. Valid values: LOW, MEDIUM, HIGH, CRITICAL",
  "path": "/fraud/assessments"
}
```

**400 Bad Request** - Invalid date:
```json
{
  "timestamp": "2024-12-17T10:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "fromDate cannot be in the future",
  "path": "/fraud/assessments"
}
```

**401 Unauthorized** - Missing or invalid token (same as above)

**403 Forbidden** - Insufficient permissions (requires `fraud:read` scope)

---

### Public Endpoints

These endpoints do not require authentication:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health check |
| `GET /actuator/health/liveness` | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | Kubernetes readiness probe |
| `GET /actuator/info` | Application information |
| `GET /actuator/prometheus` | Prometheus metrics |
| `GET /swagger-ui.html` | Swagger UI documentation |
| `GET /swagger-ui/**` | Swagger UI resources |
| `GET /v3/api-docs` | OpenAPI specification (JSON) |
| `GET /v3/api-docs.yaml` | OpenAPI specification (YAML) |

---

### Rate Limiting

The API implements rate limiting to prevent abuse:

- **Assess Transaction**: 100 requests per minute per account
- **Get Assessment**: 200 requests per minute per user
- **Search Assessments**: 50 requests per minute per user

When rate limit is exceeded, the API returns `429 Too Many Requests` with a `Retry-After` header indicating seconds to wait.

---

### Testing with cURL

**Complete workflow example:**

```bash
# 1. Get access token
export TOKEN=$(curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fraud-detection-web" \
  -d "client_secret=fraud-detection-web-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "scope=openid fraud-detection-scopes" | jq -r '.access_token')

# 2. Assess a transaction
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
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
      "city": "New York"
    },
    "deviceId": "DEVICE-12345",
    "transactionTimestamp": "2024-12-17T10:00:00Z"
  }'

# 3. Get the assessment
curl -X GET http://localhost:9001/fraud/assessments/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $TOKEN"

# 4. Search high-risk assessments
curl -X GET "http://localhost:9001/fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

---

### Interactive Documentation

**Swagger UI** is available at http://localhost:9001/swagger-ui.html for interactive API exploration and testing.

To use Swagger UI:
1. Click "Authorize" button
2. Enter credentials:
   - Client ID: `fraud-detection-web`
   - Client Secret: `fraud-detection-web-secret`
3. Login with: `detector` / `detector123`
4. Try out the endpoints interactively

---
## Docker Compose Services

The development environment includes the following services:

### Service Overview

| Service | Port(s) | Purpose |
|---------|---------|---------|
| **postgres** | 5432 | PostgreSQL database |
| **sagemaker-model** | 8080 | ML model serving (local mode) |
| **mockoon** | 3001 | Account Service mock |
| **redis** | 6379 | Caching and velocity counters |
| **kafka** | 9092, 29092 | Event streaming |
| **apicurio-registry** | 8081 | Schema registry (API) |
| **apicurio-registry-ui** | 8082 | Schema registry (UI) |
| **kafka-ui** | 8083 | Kafka management UI |
| **keycloak** | 8180 | Identity provider |
| **grafana-lgtm** | 3000, 4317, 4318 | Observability stack |

### Detailed Service Descriptions

#### PostgreSQL Database
**Port**: 5432  
**Image**: `postgres:16`  
**Databases**: `fraud_detection`, `keycloak`

**Connection:**
```bash
docker exec -it fraud-detection-postgres psql -U postgres -d fraud_detection

# View tables
\dt

# Query risk assessments
SELECT * FROM risk_assessments ORDER BY assessment_time DESC LIMIT 10;
```

#### SageMaker Model (ML Service)
**Port**: 8080  
**Image**: `ghcr.io/itumelengmanota/fraud-detection-model:latest`

**Endpoints:**
- `GET /ping` - Health check
- `POST /invocations` - Fraud prediction

**Testing:**
```bash
# Health check
curl http://localhost:8080/ping

# Prediction request
curl -X POST http://localhost:8080/invocations \
  -H 'Content-Type: application/json' \
  -d '{"amount": 100.0, "hour": 14, ...}'
```

#### Mockoon (Account Service Mock)
**Port**: 3001  
**Image**: `mockoon/cli:latest`  
**Config**: `docker-compose/mockoon/data.json`

**Purpose**: Mocks the Account Management bounded context for testing account profile lookups.

**Available Test Scenarios:**
- Success (200): `ACC-12345-1`, `ACC-12345-2`
- Not Found (404): `ACC-12345-3`
- Server Error (500): `ACC-12345-4`
- Timeout: `ACC-TIMEOUT` (5 second delay)
- Circuit Breaker: `ACC-CIRCUIT-BREAKER` (503 errors)
- Slow Response: `ACC-SLOW` (2.5 second delay)
- Retry: `ACC-RETRY` (succeeds after retry)
- Bulkhead: `ACC-BULKHEAD` (1 second delay)

#### Redis
**Port**: 6379  
**Image**: `redis:7.4`

**Commands:**
```bash
# Connect
docker exec -it fraud-detection-redis redis-cli

# View velocity counters
KEYS velocity:*

# Get counter value
GET velocity:transaction:counter:5min:ACC-12345

# View HyperLogLog merchants
PFCOUNT velocity:merchants:5min:ACC-12345
```

#### Kafka
**Ports**: 9092 (external), 29092 (internal)  
**Image**: `apache/kafka:3.8.1`  
**Mode**: KRaft (no Zookeeper)

**Topics:**
- `transactions.normalized` - Input transactions
- `fraud-detection.risk-assessments` - Risk assessment results
- `fraud-detection.high-risk-alerts` - High/critical risk alerts

**Commands:**
```bash
# List topics
docker exec -it fraud-detection-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

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

#### Apicurio Registry
**API Port**: 8081  
**UI Port**: 8082  
**Image**: `apicurio/apicurio-registry:3.0.6`

**Purpose**: Schema registry for Avro schemas

**Web UI**: http://localhost:8082

**API Example:**
```bash
# List artifacts
curl http://localhost:8081/apis/registry/v2/search/artifacts

# Get specific schema
curl http://localhost:8081/apis/registry/v2/groups/default/artifacts/TransactionAvro
```

#### Kafka UI
**Port**: 8083  
**Image**: `provectuslabs/kafka-ui:v0.7.2`

**Web UI**: http://localhost:8083

**Features:**
- Browse topics and messages
- View consumer groups and lag
- Produce test messages
- Monitor cluster health
- Inspect Avro schemas

#### Keycloak
**Port**: 8180  
**Image**: `quay.io/keycloak/keycloak:26.0.7`  
**Mode**: Development (start-dev)

**Admin Console**: http://localhost:8180/admin  
**Admin Credentials**: `admin` / `admin`

**Realm**: `fraud-detection`  
**Import**: Auto-imported from `docker-compose/keycloak-config/realm-export.json`

#### Grafana LGTM Stack
**Ports**: 3000 (Grafana), 4317/4318 (OTLP)  
**Image**: `grafana/otel-lgtm:latest`

**Purpose**: Unified observability stack (Logs, Traces, Metrics)

**Grafana UI**: http://localhost:3000  
**Default Credentials**: `admin` / `admin`

### Starting Services

**Start all services:**
```bash
docker-compose -f docker-compose/compose.yml up -d
```

**Start specific services:**
```bash
# Just database and cache
docker-compose -f docker-compose/compose.yml up -d postgres redis

# Add ML model
docker-compose -f docker-compose/compose.yml up -d sagemaker-model

# Add messaging
docker-compose -f docker-compose/compose.yml up -d kafka apicurio-registry
```

**View logs:**
```bash
# All services
docker-compose -f docker-compose/compose.yml logs -f

# Specific service
docker-compose -f docker-compose/compose.yml logs -f sagemaker-model
```

**Check status:**
```bash
docker-compose -f docker-compose/compose.yml ps
```

**Stop services:**
```bash
docker-compose -f docker-compose/compose.yml down

# Stop and remove volumes
docker-compose -f docker-compose/compose.yml down -v
```
---
## Testing

### Unit Tests

Run unit tests:
```bash
./gradlew test
```

Unit tests cover:
- Domain logic and business rules
- Value objects and entities
- Service layer components
- Rule engine evaluations

### Integration Tests

Run integration tests:
```bash
./gradlew test --tests "*IntegrationTest"
```

Integration tests use Testcontainers to spin up:
- PostgreSQL database
- Redis cache
- Kafka broker
- Keycloak authentication server
- Mock Account Service
- SageMaker ML model container

### Testing with cURL

#### Setup: Get Access Token

First, obtain an access token from Keycloak:

```bash
export ACCESS_TOKEN=$(curl -s -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=fraud-detection-web" \
  -d "client_secret=fraud-detection-web-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "grant_type=password" | jq -r '.access_token')

echo $ACCESS_TOKEN
```

Available test users:
- `analyst` / `analyst123` - Read-only access (fraud:read)
- `detector` / `detector123` - Detection access (fraud:detect, fraud:read)
- `admin` / `admin123` - Full access (all permissions)

#### Test Scenario 1: Low-Risk Transaction

A normal transaction that should be allowed:

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "550e8400-e29b-41d4-a716-446655440001",
    "accountId": "ACC-12345-1",
    "amount": 45.99,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "merchantId": "MERCH-001",
    "merchantName": "Amazon",
    "merchantCategory": "RETAIL",
    "location": {
      "latitude": -26.2041,
      "longitude": 28.0473,
      "country": "ZA",
      "city": "Johannesburg"
    },
    "deviceId": "DEVICE-12345",
    "transactionTimestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'
```

Expected response:
```json
{
  "assessmentId": "...",
  "transactionId": "550e8400-e29b-41d4-a716-446655440001",
  "riskScore": 15,
  "transactionRiskLevel": "LOW",
  "decision": "ALLOW",
  "assessmentTime": "2024-01-15T10:30:00Z"
}
```

#### Test Scenario 2: Account Service Integration

Test with a valid account profile from the mock Account Service:

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "550e8400-e29b-41d4-a716-446655440002",
    "accountId": "ACC-12345-2",
    "amount": 150.00,
    "currency": "GBP",
    "type": "PURCHASE",
    "channel": "CARD",
    "merchantId": "MERCH-UK-001",
    "merchantName": "Tesco",
    "merchantCategory": "GROCERY",
    "location": {
      "latitude": 51.5074,
      "longitude": -0.1278,
      "country": "GB",
      "city": "London"
    },
    "deviceId": "DEVICE-UK-001",
    "transactionTimestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'
```

This account has a home location in London, so the transaction should show low geographic risk.

#### Test Scenario 3: Medium-Risk Transaction

A large amount transaction that should trigger a challenge:

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "550e8400-e29b-41d4-a716-446655440003",
    "accountId": "ACC-12345-1",
    "amount": 15000.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "ONLINE",
    "merchantId": "MERCH-002",
    "merchantName": "Best Buy",
    "merchantCategory": "ELECTRONICS",
    "location": {
      "latitude": -26.2041,
      "longitude": 28.0473,
      "country": "ZA",
      "city": "Johannesburg"
    },
    "deviceId": "DEVICE-12345",
    "transactionTimestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'
```

Expected response:
```json
{
  "assessmentId": "...",
  "transactionId": "550e8400-e29b-41d4-a716-446655440003",
  "riskScore": 55,
  "transactionRiskLevel": "MEDIUM",
  "decision": "CHALLENGE",
  "assessmentTime": "2024-01-15T10:35:00Z"
}
```

#### Test Scenario 4: High-Risk Transaction (Velocity)

Submit multiple rapid transactions to trigger velocity rules:

```bash
# Submit 6 transactions within 5 minutes
for i in {1..6}; do
  curl -X POST http://localhost:9001/fraud/assessments \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"transactionId\": \"550e8400-e29b-41d4-a716-44665544000$i\",
      \"accountId\": \"ACC-VELOCITY-TEST\",
      \"amount\": 99.99,
      \"currency\": \"USD\",
      \"type\": \"PURCHASE\",
      \"channel\": \"ONLINE\",
      \"merchantId\": \"MERCH-00$i\",
      \"merchantName\": \"Test Merchant $i\",
      \"merchantCategory\": \"RETAIL\",
      \"location\": {
        \"latitude\": -26.2041,
        \"longitude\": 28.0473,
        \"country\": \"ZA\",
        \"city\": \"Johannesburg\"
      },
      \"deviceId\": \"DEVICE-VEL-001\",
      \"transactionTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"
    }"
  sleep 1
done
```

The 6th transaction should be flagged as HIGH risk due to velocity violation.

Expected response (for 6th transaction):
```json
{
  "assessmentId": "...",
  "transactionId": "550e8400-e29b-41d4-a716-446655440006",
  "riskScore": 75,
  "transactionRiskLevel": "HIGH",
  "decision": "REVIEW",
  "assessmentTime": "2024-01-15T10:40:00Z"
}
```

#### Test Scenario 5: Critical Risk (Impossible Travel)

First, create a transaction in Johannesburg:

```bash
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "550e8400-e29b-41d4-a716-446655440010",
    "accountId": "ACC-TRAVEL-TEST",
    "amount": 100.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "POS",
    "merchantId": "MERCH-JNB-001",
    "merchantName": "Shop Johannesburg",
    "merchantCategory": "RETAIL",
    "location": {
      "latitude": -26.2041,
      "longitude": 28.0473,
      "country": "ZA",
      "city": "Johannesburg"
    },
    "deviceId": "DEVICE-TRAVEL-001",
    "transactionTimestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'
```

Wait 2 seconds, then create a transaction in New York (impossible to travel that distance in 2 seconds):

```bash
sleep 2

curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "550e8400-e29b-41d4-a716-446655440011",
    "accountId": "ACC-TRAVEL-TEST",
    "amount": 200.00,
    "currency": "USD",
    "type": "PURCHASE",
    "channel": "POS",
    "merchantId": "MERCH-NYC-001",
    "merchantName": "Shop New York",
    "merchantCategory": "RETAIL",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "country": "US",
      "city": "New York"
    },
    "deviceId": "DEVICE-TRAVEL-002",
    "transactionTimestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }'
```

Expected response:
```json
{
  "assessmentId": "...",
  "transactionId": "550e8400-e29b-41d4-a716-446655440011",
  "riskScore": 95,
  "transactionRiskLevel": "CRITICAL",
  "decision": "BLOCK",
  "assessmentTime": "2024-01-15T10:45:00Z"
}
```

#### Query Risk Assessments

Get a specific assessment:
```bash
curl -X GET "http://localhost:9001/fraud/assessments/550e8400-e29b-41d4-a716-446655440001" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Search high-risk assessments:
```bash
curl -X GET "http://localhost:9001/fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&page=0&size=20" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Search by date range:
```bash
YESTERDAY=$(date -u -d '1 day ago' +"%Y-%m-%dT%H:%M:%SZ")
curl -X GET "http://localhost:9001/fraud/assessments?fromDate=$YESTERDAY&page=0&size=20" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### Testing with Postman

#### Import Collection

1. Download the Postman collection from the repository:
   - `postman/Fraud-Detection-API.postman_collection.json`
   - `postman/Fraud-Detection-Environments.postman_environment.json`

2. Import into Postman:
   - Click **Import** in Postman
   - Select both files
   - Choose the **Local** environment

#### Configure Environment

The environment includes:
- `baseUrl`: http://localhost:9001
- `keycloakUrl`: http://localhost:8180
- `realm`: fraud-detection
- `clientId`: fraud-detection-web
- `clientSecret`: fraud-detection-web-secret
- `username`: detector
- `password`: detector123

#### Authentication Setup

The collection includes an **Authorization** folder with:
1. **Get Access Token** - Obtains JWT token from Keycloak
2. **Refresh Token** - Refreshes expired tokens

Run "Get Access Token" first to populate the `accessToken` environment variable.

#### Available Test Scenarios

The collection includes pre-configured requests for:

1. **Assessment - Low Risk**: Normal transaction
2. **Assessment - Medium Risk**: Large amount ($15,000)
3. **Assessment - High Risk**: Velocity testing
4. **Assessment - Critical Risk**: Impossible travel
5. **Assessment - Account Integration**: Tests with mock account service
6. **Query Assessment by ID**: Retrieves specific assessment
7. **Query High-Risk Assessments**: Filters by risk level
8. **Query by Date Range**: Time-based filtering

#### Collection Variables

Each request uses variables for easy customization:
- `{{transactionId}}` - Auto-generated UUID
- `{{accountId}}` - Test account identifier
- `{{amount}}` - Transaction amount
- `{{timestamp}}` - Current ISO timestamp

#### Running Tests

1. Select a request from the collection
2. Click **Send**
3. View the response in the **Response** panel
4. Check the **Test Results** tab for automated assertions

#### Automated Tests

Each request includes test scripts that verify:
- HTTP status codes (200, 404, etc.)
- Response structure and required fields
- Risk score calculations
- Decision alignment with risk levels
- Response time thresholds

Example test script:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has required fields", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('assessmentId');
    pm.expect(jsonData).to.have.property('transactionId');
    pm.expect(jsonData).to.have.property('riskScore');
    pm.expect(jsonData).to.have.property('transactionRiskLevel');
    pm.expect(jsonData).to.have.property('decision');
});

pm.test("Risk score is valid", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.riskScore).to.be.at.least(0);
    pm.expect(jsonData.riskScore).to.be.at.most(100);
});
```

### Testing with Kafka UI

Access Kafka UI at http://localhost:8083 to monitor the event-driven aspects of the system.

#### Topics to Monitor

**1. Input Topic: `transactions.normalized`**
- Contains incoming transaction events
- Format: Avro schema
- Source: External transaction processing systems
- Consumed by: `TransactionEventConsumer`

To view messages:
1. Navigate to **Topics** → **transactions.normalized**
2. Click **Messages** tab
3. Set **Oldest** or **Newest** position
4. View message content in JSON format

**2. Output Topic: `fraud-detection.risk-assessments`**
- Contains completed risk assessments
- Published by: `EventPublisherAdapter`
- Event type: `RiskAssessmentCompleted`
- Schema: `risk-assessment-completed.avsc`

Message structure:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "assessmentId": "660e8400-e29b-41d4-a716-446655440001",
  "finalScore": 75.0,
  "riskLevel": "HIGH",
  "decision": "REVIEW",
  "occurredAt": 1705320000000
}
```

**3. Alert Topic: `fraud-detection.high-risk-alerts`**
- Contains high and critical risk detections
- Published by: `EventPublisherAdapter`
- Event type: `HighRiskDetected`
- Schema: `high-risk-detected.avsc`
- Triggered: When risk level is HIGH or CRITICAL

Message structure:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "assessmentId": "660e8400-e29b-41d4-a716-446655440001",
  "riskLevel": "CRITICAL",
  "occurredAt": 1705320000000
}
```

#### Consumer Groups

Monitor consumer lag and performance:

1. Navigate to **Consumers**
2. Select **fraud-detection-service** group
3. View metrics:
   - **Lag**: Number of unprocessed messages
   - **Offset**: Current position in topic
   - **Members**: Active consumer instances
   - **Assignments**: Partition distribution

Healthy indicators:
- Lag: Should be 0 or minimal (<100)
- Members: Should match configured concurrency (10)
- State: Should be "Stable"

#### Schema Registry (Apicurio)

View Avro schemas at http://localhost:8082:

1. **Transaction Event Schema**
   - Group: `transactions.normalized`
   - Artifact: `TransactionAvro`
   - Versions: Track schema evolution

2. **Risk Assessment Schema**
   - Group: `fraud-detection.risk-assessments`
   - Artifact: `RiskAssessmentCompletedAvro`

3. **High Risk Alert Schema**
   - Group: `fraud-detection.high-risk-alerts`
   - Artifact: `HighRiskDetectedAvro`

#### Testing Event Flow

Complete end-to-end test:

1. **Publish a test transaction** to `transactions.normalized`:
   ```bash
   # Using Kafka UI "Produce Message" button
   # Or via kafka-console-producer
   ```

2. **Monitor consumption**:
   - Check **Consumers** tab for processing
   - Verify lag decreases

3. **Verify output events**:
   - Check `fraud-detection.risk-assessments` for completion event
   - If high risk, check `fraud-detection.high-risk-alerts`

4. **Inspect message details**:
   - Click on individual messages
   - View headers, key, and value
   - Check timestamp and partition information

#### Monitoring Consumer Performance

Track consumer metrics in Kafka UI:

**Message Processing Rate**:
- Go to **Topics** → Select topic → **Metrics**
- View "Messages In/Sec" graph
- Compare with consumer lag

**Partition Balance**:
- Check **Consumer Groups** → **fraud-detection-service**
- Verify even partition distribution across 10 consumer threads
- Look for rebalancing events

**Error Patterns**:
- Monitor for messages stuck in retry
- Check for DLQ (Dead Letter Queue) messages
- Review error logs in application logs

#### Troubleshooting

**High Consumer Lag**:
- Increase consumer concurrency in `application.yml`
- Check application logs for processing errors
- Verify database and Redis connectivity

**Duplicate Message Detection**:
- Messages cached in Redis for 48 hours
- Check Redis keys: `seen:transaction:*`
- Verify idempotency is working via logs

**Schema Compatibility Issues**:
- Review Apicurio Registry for schema versions
- Check compatibility mode (BACKWARD, FORWARD, FULL)
- Verify consumer's `specific.avro.reader` setting

**Partition Rebalancing**:
- Normal during consumer restarts
- Should stabilize within 30 seconds
- Excessive rebalancing indicates network or timeout issues

---
## CI/CD Pipeline

### GitHub Actions Workflow

The project uses GitHub Actions for continuous integration and deployment. The workflow is defined in `.github/workflows/commit-stage.yml`.

#### Commit Stage Pipeline

Triggered on every push to the repository:

```yaml
name: Commit Stage
on: push
```

**Pipeline Steps**:

1. **Build and Test**
   - Checkout source code
   - Set up GraalVM JDK 25
   - Pull ML model Docker image from GHCR
   - Run unit and integration tests
   - Execute code vulnerability scanning with Anchore

2. **Package and Publish** (main branch only)
   - Build application JAR with Gradle
   - Create multi-architecture Docker images (amd64, arm64)
   - Push images to GitHub Container Registry
   - Perform OCI image vulnerability scanning
   - Upload security scan results

#### Environment Variables

Required secrets and variables:

```yaml
env:
  REGISTRY: ghcr.io
  IMAGE_NAME: itumelengmanota/fraud-detection-service
  MODEL_IMAGE: ghcr.io/itumelengmanota/fraud-detection-model:latest
  VERSION: ${{ github.sha }}
```

#### ML Model Integration

The pipeline pulls the pre-built ML model image for integration tests:

```yaml
- name: Pull Model Image for Tests
  run: |
    echo "Pulling model image: ${{ env.MODEL_IMAGE }}"
    docker pull ${{ env.MODEL_IMAGE }}
    docker tag ${{ env.MODEL_IMAGE }} fraud-detection-model:latest
```

This ensures integration tests can run against the actual SageMaker model container.

#### Security Scanning

**Code Vulnerability Scanning**:
```yaml
- name: Code Vulnerability Scanning
  uses: anchore/scan-action@v6
  with:
    path: "${{ github.workspace }}"
    fail-build: false
    severity-cutoff: high
```

**Container Image Scanning**:
```yaml
- name: OCI Image Vulnerability Scanning
  uses: anchore/scan-action@v6
  with:
    image: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ env.VERSION }}
    fail-build: false
    severity-cutoff: high
```

Results are uploaded to GitHub Security tab via SARIF format.

#### Multi-Architecture Builds

The pipeline builds Docker images for multiple architectures:

```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3

- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3

- name: Build and push
  uses: docker/build-push-action@v6
  with:
    platforms: linux/amd64,linux/arm64
    push: true
    tags: |
      ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ env.VERSION }}
      ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
```

This enables deployment on both x86 and ARM-based infrastructure (AWS Graviton, Apple Silicon, etc.).

#### Docker Image Structure

The application uses a layered Docker image approach for optimal caching:

```dockerfile
FROM eclipse-temurin:25-jre AS builder
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:25-jre
RUN useradd spring
USER spring
WORKDIR /app
COPY --from=builder app/dependencies/ ./
COPY --from=builder app/spring-boot-loader/ ./
COPY --from=builder app/snapshot-dependencies/ ./
COPY --from=builder app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Benefits:
- **Layer caching**: Dependencies rarely change, maximizing cache hits
- **Smaller images**: Base dependencies layer is reused
- **Security**: Runs as non-root user
- **Fast deployments**: Only application layer changes on code updates

#### Deployment Artifacts

Each successful build produces:

1. **Docker Images**:
   - `ghcr.io/itumelengmanota/fraud-detection-service:latest`
   - `ghcr.io/itumelengmanota/fraud-detection-service:<git-sha>`

2. **Security Reports**:
   - Code vulnerability SARIF report
   - Container image vulnerability SARIF report
   - Available in GitHub Security tab

3. **Build Artifacts**:
   - Application JAR (Spring Boot executable)
   - Available in GitHub Actions artifacts

#### Local Pipeline Testing

Test the pipeline locally before pushing:

```bash
# Build the Docker image
./gradlew bootJar
docker build -t fraud-detection-service:local .

# Run security scan
docker run --rm -v $(pwd):/scan anchore/grype:latest dir:/scan

# Test multi-arch build
docker buildx create --use
docker buildx build --platform linux/amd64,linux/arm64 -t fraud-detection-service:multi-arch .
```

#### Continuous Deployment (Future)

The pipeline is designed to extend to continuous deployment:

```yaml
# Example deployment stage (not yet implemented)
deploy:
  name: Deploy to Production
  needs: [package]
  if: github.ref == 'refs/heads/main'
  runs-on: ubuntu-latest
  steps:
    - name: Deploy to Kubernetes
      uses: azure/k8s-deploy@v4
      with:
        manifests: |
          k8s/deployment.yaml
          k8s/service.yaml
```

## Infrastructure Management

### Docker Compose Setup

The development environment uses Docker Compose for local infrastructure. All services are defined in `docker-compose/compose.yml`.

#### Starting the Infrastructure

```bash
# Start all services
docker-compose -f docker-compose/compose.yml up -d

# View logs
docker-compose -f docker-compose/compose.yml logs -f

# Stop all services
docker-compose -f docker-compose/compose.yml down

# Stop and remove volumes (clean slate)
docker-compose -f docker-compose/compose.yml down -v
```

#### Core Services

**PostgreSQL Database**:
```yaml
postgres:
  image: 'postgres:16'
  ports:
    - '5432:5432'
  environment:
    - 'POSTGRES_PASSWORD=postgres'
    - 'POSTGRES_USER=postgres'
  volumes:
    - ./postgresql/init.sql:/docker-entrypoint-initdb.d/init.sql
```

Initializes two databases:
- `fraud_detection` - Main application database
- `keycloak` - Keycloak authentication database

**Redis Cache**:
```yaml
redis:
  image: 'redis:7.4'
  ports:
    - '6379:6379'
```

Used for:
- Velocity counters (transaction counts, amounts)
- ML prediction caching
- Account profile caching
- Duplicate message detection

**Kafka Broker**:
```yaml
kafka:
  image: 'apache/kafka:3.8.1'
  ports:
    - '9092:9092'
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: 'broker,controller'
    KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:29093'
    # KRaft mode (no ZooKeeper required)
```

**Apicurio Schema Registry**:
```yaml
apicurio-registry:
  image: 'apicurio/apicurio-registry:3.0.6'
  ports:
    - '8081:8080'
  environment:
    REGISTRY_STORAGE_KIND: 'inmemory'
```

Manages Avro schemas for Kafka messages.

**Keycloak Authentication**:
```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.0.7
  command:
    - start-dev
    - --import-realm
  ports:
    - "8180:8080"
  volumes:
    - ./keycloak-config/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
```

Pre-configured with:
- Realm: `fraud-detection`
- 3 test users (analyst, detector, admin)
- 3 clients (service, web, kafka)
- OAuth2/OIDC authentication

**SageMaker ML Model**:
```yaml
sagemaker-model:
  image: ghcr.io/itumelengmanota/fraud-detection-model:latest
  ports:
    - '8080:8080'
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/ping"]
    interval: 30s
    timeout: 10s
    retries: 3
```

Local SageMaker endpoint compatible container.

**Mock Account Service**:
```yaml
mockoon:
  image: 'mockoon/cli:latest'
  ports:
    - '3001:3000'
  volumes:
    - ./mockoon/data.json:/data/data.json
  command: ['--data', '/data/data.json']
```

Simulates external Account Management system with test account profiles.

**Observability Stack (Grafana LGTM)**:
```yaml
grafana-lgtm:
  image: 'grafana/otel-lgtm:latest'
  ports:
    - '3000:3000'    # Grafana UI
    - '4317:4317'    # OTLP gRPC
    - '4318:4318'    # OTLP HTTP
```

All-in-one observability stack with:
- **Loki**: Log aggregation
- **Grafana**: Visualization dashboards
- **Tempo**: Distributed tracing
- **Mimir**: Metrics storage

Access Grafana at http://localhost:3000 (no authentication required in dev mode).

#### Kafka UI

```yaml
kafka-ui:
  image: 'provectuslabs/kafka-ui:v0.7.2'
  ports:
    - '8083:8080'
  environment:
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 'kafka:29092'
    KAFKA_CLUSTERS_0_SCHEMAREGISTRY: 'http://apicurio-registry:8080/apis/ccompat/v7'
```

Access at http://localhost:8083 for monitoring Kafka topics, consumer groups, and schema registry.

#### Health Checks

Verify all services are healthy:

```bash
# Check service health
docker-compose -f docker-compose/compose.yml ps

# Expected output: All services in "Up" state
```

Individual service health checks:

```bash
# PostgreSQL
psql -h localhost -U postgres -d fraud_detection -c "SELECT 1"

# Redis
redis-cli ping
# Expected: PONG

# Kafka
docker exec -it fraud-detection-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Keycloak
curl http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration

# SageMaker Model
curl http://localhost:8080/ping
# Expected: {}

# Account Service
curl http://localhost:3001/accounts/ACC-12345-1/profiles
```

#### Resource Requirements

Minimum system requirements for running all services:

- **RAM**: 8GB (16GB recommended)
- **CPU**: 4 cores (8 cores recommended)
- **Disk**: 20GB free space

Monitor resource usage:

```bash
# View resource consumption
docker stats

# View disk usage
docker system df
```

#### Service Dependencies

Service startup order is managed by Docker Compose `depends_on`:

```
PostgreSQL
    ↓
Keycloak (requires PostgreSQL)
    ↓
Kafka
    ↓
Apicurio Registry (requires Kafka)
    ↓
Application (requires all above)
```

#### Networking

All services communicate on a Docker bridge network:

- Service discovery via container names (e.g., `kafka`, `postgres`)
- Application connects to services using internal ports
- External access via published ports

#### Data Persistence

**Persistent Data** (survives container restarts):
- PostgreSQL data volume (managed by Docker)
- Kafka data (KRaft metadata)

**Ephemeral Data** (cleared on restart):
- Redis cache
- Apicurio Registry schemas (in-memory mode)
- Mockoon state

To persist Redis data, add volume mount:

```yaml
redis:
  volumes:
    - redis-data:/data
```

#### Production Considerations

For production deployment, replace Docker Compose with:

**Kubernetes**:
```bash
# Example deployment structure
k8s/
├── namespace.yaml
├── postgres-statefulset.yaml
├── redis-deployment.yaml
├── kafka-statefulset.yaml
├── keycloak-deployment.yaml
├── fraud-detection-deployment.yaml
├── services.yaml
└── ingress.yaml
```

**Managed Services**:
- PostgreSQL → Amazon RDS / Azure Database for PostgreSQL
- Redis → Amazon ElastiCache / Azure Cache for Redis
- Kafka → Amazon MSK / Confluent Cloud
- Keycloak → Auth0 / AWS Cognito / Azure AD B2C
- SageMaker → AWS SageMaker Endpoint

**Configuration Management**:
- Replace hardcoded credentials with secrets management
- Use AWS Secrets Manager / HashiCorp Vault
- Externalize configuration via ConfigMaps / Parameter Store

## Performance Tuning

### Database Optimization

#### Connection Pooling (HikariCP)

The application uses HikariCP for efficient database connection management:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50        # Max connections
      minimum-idle: 10              # Min idle connections
      connection-timeout: 30000     # 30s connection wait
      idle-timeout: 600000          # 10min idle before eviction
      max-lifetime: 1800000         # 30min max connection lifetime
      leak-detection-threshold: 60000  # 60s leak detection
```

**Tuning Guidelines**:

- **maximum-pool-size**: Set to `(core_count × 2) + effective_spindle_count`
   - Example: 4 cores + 1 SSD = 9-10 connections
   - For cloud databases, match available connections

- **minimum-idle**: Keep 20% of maximum as idle
   - Prevents connection creation overhead during spikes

- **leak-detection-threshold**: Enable in development
   - Detects connection leaks (connections not returned to pool)
   - Set to 60000ms (60 seconds) or lower

Monitor connection pool:

```bash
# Via actuator endpoint
curl http://localhost:9001/actuator/metrics/hikaricp.connections.active
curl http://localhost:9001/actuator/metrics/hikaricp.connections.idle
```

#### Database Indexes

Critical indexes for query performance (defined in Flyway migrations):

```sql
-- Risk Assessments
CREATE INDEX idx_transaction_id ON risk_assessments (transaction_id);
CREATE INDEX idx_assessment_time ON risk_assessments (assessment_time);
CREATE INDEX idx_risk_level ON risk_assessments (risk_level);

-- Transactions
CREATE INDEX idx_transaction_account_id ON transaction (account_id);
CREATE INDEX idx_transaction_timestamp ON transaction (timestamp);
CREATE INDEX idx_transaction_account_id_timestamp ON transaction (account_id, timestamp);

-- Locations
CREATE INDEX idx_location_coordinates ON location (latitude, longitude);
```

Monitor slow queries:

```sql
-- Enable query logging in PostgreSQL
ALTER DATABASE fraud_detection SET log_min_duration_statement = 1000; -- Log queries > 1s

-- Check slow queries
SELECT query, mean_exec_time, calls 
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;
```

### Redis Caching Strategy

#### Cache Configuration

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 172800000  # 48 hours
      cache-null-values: false
      use-key-prefix: true
      key-prefix: "fraud-detection:"

  data:
    redis:
      lettuce:
        pool:
          max-active: 50      # Max connections
          max-idle: 20        # Max idle connections
          min-idle: 5         # Min idle connections
          max-wait: 3000ms    # Max wait for connection
```

#### Cached Components

**ML Predictions** (unless high risk):
```java
@Cacheable(value = "mlPredictions", 
           key = "#transaction.id().toString()", 
           unless = "#result.fraudProbability() > 0.7")
```

**Account Profiles** (with circuit breaker fallback):
```java
@Cacheable(value = "accountProfiles", 
           key = "#accountId", 
           unless = "#result == null")
```

**Velocity Metrics** (per account):
```java
@Cacheable(value = "velocityMetrics", 
           key = "#transaction.accountId()")
```

#### Cache Eviction

Cache is evicted on updates:

```java
@CacheEvict(value = "velocityMetrics", key = "#transaction.accountId()")
public void incrementCounters(Transaction transaction)
```

Monitor cache performance:

```bash
# Cache hit rate
redis-cli INFO stats | grep keyspace_hits
redis-cli INFO stats | grep keyspace_misses

# Memory usage
redis-cli INFO memory | grep used_memory_human

# Key count
redis-cli DBSIZE
```

#### Redis Data Structures

Optimized data structures for performance:

**HyperLogLog for Cardinality**:
```java
// Counts unique merchants/locations with O(1) space
redisTemplate.opsForHyperLogLog().add(key, value);
Long uniqueCount = redisTemplate.opsForHyperLogLog().size(key);
```

Memory: ~12KB per HyperLogLog (regardless of cardinality)

**Counters with TTL**:
```java
// Transaction counts with automatic expiration
redisTemplate.opsForValue().increment(key, 1);
redisTemplate.expire(key, window.getDuration());
```

**Key Naming Convention**:
```
velocity:transaction:counter:5min:ACC-12345
velocity:amount:1hour:ACC-12345
velocity:merchants:24hour:ACC-12345
seen:transaction:550e8400-e29b-41d4-a716-446655440001
```

### Kafka Consumer Optimization

#### Concurrency Configuration

```yaml
kafka:
  listener:
    concurrency: 10              # 10 parallel consumer threads
    type: batch                  # Batch processing
    poll-timeout: 3000           # 3s poll timeout
    ack-mode: manual             # Manual offset commit

  consumer:
    max-poll-records: 1000       # Max records per poll
    fetch-min-size: 1            # Min fetch size (bytes)
    fetch-max-wait: 500ms        # Max wait for min fetch size
    enable-auto-commit: false    # Manual commit
```

**Concurrency Guidelines**:
- Set to number of topic partitions for maximum parallelism
- Each thread handles one or more partitions
- Increase if consumer lag is growing

Monitor consumer lag:

```bash
# Via Kafka UI at http://localhost:8083
# Or CLI:
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group fraud-detection-service \
  --describe
```

#### Batch Processing

Process messages in batches for efficiency:

```java
@KafkaListener(topics = "#{kafkaTopicProperties.name}", 
               groupId = "#{kafkaTopicProperties.groupId}",
               concurrency = "#{kafkaTopicProperties.concurrency}")
@Transactional
public void consume(TransactionAvro avroTransaction, Acknowledgment acknowledgment)
```

Batch size controlled by `max-poll-records`:
- Higher values = better throughput, higher latency
- Lower values = lower throughput, lower latency

#### Idempotency

Prevent duplicate processing with Redis cache:

```java
// 48-hour deduplication window
if (seenMessageCache.hasProcessed(transactionId)) {
    log.debug("Duplicate transaction detected, skipping: {}", transactionId);
    acknowledgment.acknowledge();
    return;
}
```

Cache configuration:
```yaml
fraud-detection:
  transaction-event-consumer:
    idempotency:
      ttl-minutes: 2880  # 48 hours
```

### Thread Pool Configuration

#### Virtual Threads (Java 21+)

Enabled by default for improved concurrency:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Benefits:
- Millions of virtual threads vs thousands of platform threads
- Reduced memory overhead
- Better resource utilization for I/O-bound operations

#### Test Execution Parallelism

```gradle
tasks.named('test') {
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
    
    minHeapSize = "512m"
    maxHeapSize = "2g"
    
    jvmArgs = [
        '-XX:+UseParallelGC',
        '-XX:MaxGCPauseMillis=200'
    ]
}
```

### Circuit Breakers & Resilience

#### SageMaker ML Service

```yaml
resilience4j:
  circuitbreaker:
    instances:
      sagemakerML:
        sliding-window-size: 20
        failure-rate-threshold: 50        # Open if 50% fail
        slow-call-rate-threshold: 50      # Open if 50% slow
        slow-call-duration-threshold: 100ms
        wait-duration-in-open-state: 60s  # Stay open for 60s

  retry:
    instances:
      sagemakerML:
        max-attempts: 2
        wait-duration: 50ms
        enable-exponential-backoff: true

  timelimiter:
    instances:
      sagemakerML:
        timeout-duration: 500ms           # Kill slow calls

  bulkhead:
    instances:
      sagemakerML:
        max-concurrent-calls: 50          # Max parallel calls
```

**Fallback Strategy**:
```java
private MLPrediction fallbackPrediction() {
    return MLPrediction.unavailable();  // Returns 0.0 probability
}
```

System degrades gracefully when ML service is unavailable by relying solely on rule engine.

#### Account Service

```yaml
resilience4j:
  circuitbreaker:
    instances:
      accountService:
        sliding-window-size: 20
        failure-rate-threshold: 50
        slow-call-duration-threshold: 2s
        wait-duration-in-open-state: 30s

  retry:
    instances:
      accountService:
        max-attempts: 3
        wait-duration: 100ms
```

**Fallback Strategy**:
```java
private AccountProfile findAccountProfileFallback(String accountId, Exception ex) {
    // Try cache first
    Cache cache = cacheManager.getCache("accountProfiles");
    if (cache != null) {
        AccountProfile cachedProfile = cache.get(accountId, AccountProfile.class);
        if (cachedProfile != null) {
            return cachedProfile;
        }
    }
    return null;  // Proceed without account data
}
```

### Monitoring & Metrics

#### Key Performance Indicators

Access via Actuator at http://localhost:9001/actuator/metrics:

**Throughput**:
```bash
# Transactions processed per second
curl http://localhost:9001/actuator/metrics/kafka.consumer.records.consumed.total

# Risk assessments completed per second
curl http://localhost:9001/actuator/metrics/http.server.requests?tag=uri:/fraud/assessments
```

**Latency**:
```bash
# Average assessment time
curl http://localhost:9001/actuator/metrics/http.server.requests?tag=uri:/fraud/assessments

# ML prediction duration
curl http://localhost:9001/actuator/metrics/sagemaker.prediction.duration
```

**Resource Utilization**:
```bash
# JVM memory
curl http://localhost:9001/actuator/metrics/jvm.memory.used

# Database connections
curl http://localhost:9001/actuator/metrics/hikaricp.connections.active

# Redis connections
curl http://localhost:9001/actuator/metrics/lettuce.connections.active
```

**Error Rates**:
```bash
# ML prediction errors
curl http://localhost:9001/actuator/metrics/sagemaker.prediction.errors

# Circuit breaker state
curl http://localhost:9001/actuator/metrics/resilience4j.circuitbreaker.state
```

#### Grafana Dashboards

Access pre-built dashboards at http://localhost:3000:

1. **Application Overview**
   - Request rate and latency
   - Error rates
   - JVM metrics

2. **Database Performance**
   - Connection pool utilization
   - Query execution times
   - Transaction rollback rate

3. **Kafka Metrics**
   - Consumer lag
   - Message processing rate
   - Partition assignments

4. **Circuit Breaker Health**
   - Circuit state (closed/open/half-open)
   - Call success/failure rates
   - Retry attempts

#### Distributed Tracing

OTLP tracing to Tempo via Grafana LGTM:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling in production
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

View traces in Grafana:
1. Navigate to **Explore**
2. Select **Tempo** data source
3. Search by trace ID or service name

### Performance Benchmarks

#### Target Performance

**Throughput**:
- 1,000 transactions/second sustained
- 5,000 transactions/second peak

**Latency** (95th percentile):
- End-to-end assessment: < 200ms
- ML prediction: < 100ms
- Rule evaluation: < 50ms
- Database persistence: < 30ms

**Availability**:
- 99.9% uptime (8.76 hours downtime/year)
- Graceful degradation when dependencies fail

#### Load Testing

Use Apache JMeter or Gatling for load testing:

```bash
# Example with Gatling
sbt "gatling:test -s FraudDetectionSimulation"
```

Sample Gatling scenario:

```scala
scenario("Fraud Assessment Load Test")
  .exec(http("Get Token")
    .post("/realms/fraud-detection/protocol/openid-connect/token")
    .formParam("grant_type", "password")
    .formParam("client_id", "fraud-detection-web")
    .formParam("username", "detector")
    .formParam("password", "detector123")
    .check(jsonPath("$.access_token").saveAs("token")))
  .exec(http("Assess Transaction")
    .post("/fraud/assessments")
    .header("Authorization", "Bearer ${token}")
    .body(StringBody("""{"transactionId":"..."}"""))
    .check(status.is(200)))
  .inject(
    rampUsersPerSec(10) to 100 during (60 seconds),
    constantUsersPerSec(100) during (300 seconds)
  )
```

Monitor during load test:
- CPU/memory usage (`docker stats`)
- Consumer lag (Kafka UI)
- Circuit breaker state (Actuator)
- Response time distribution (Grafana)

---
## Troubleshooting

### Application Won't Start

#### Docker Compose Services Not Running

**Symptoms**:
- Application fails with connection errors
- `Connection refused` exceptions in logs

**Solution**:

```bash
# Check if all services are running
docker-compose -f docker-compose/compose.yml ps

# Start services if not running
docker-compose -f docker-compose/compose.yml up -d

# Check service health
docker-compose -f docker-compose/compose.yml ps

# View service logs
docker-compose -f docker-compose/compose.yml logs -f
```

**Common Issues**:

1. **Port conflicts**: Another process is using required ports
   ```bash
   # Check what's using port 9001 (application)
   lsof -i :9001
   
   # Check other critical ports
   lsof -i :5432   # PostgreSQL
   lsof -i :6379   # Redis
   lsof -i :9092   # Kafka
   lsof -i :8180   # Keycloak
   ```

2. **Insufficient resources**: Docker doesn't have enough memory/CPU
   ```bash
   # Check Docker resource limits
   docker info | grep -i memory
   
   # Increase Docker Desktop resources:
   # Docker Desktop → Settings → Resources
   # Recommended: 8GB RAM, 4 CPUs minimum
   ```

3. **Volume permission issues**:
   ```bash
   # Fix volume permissions
   sudo chown -R $(whoami):$(whoami) docker-compose/
   ```

#### Spring Boot Startup Failures

**Symptoms**:
- Application crashes during startup
- `APPLICATION FAILED TO START` in logs

**Check logs**:
```bash
# View application logs
./gradlew bootRun --console=plain

# Or in Docker
docker logs fraud-detection-service -f
```

**Common Causes**:

1. **Missing ML Model Image**:
   ```
   Error: Cannot pull fraud-detection-model:latest
   ```

   Solution:
   ```bash
   # Pull from GitHub Container Registry
   docker login ghcr.io -u YOUR_USERNAME -p YOUR_GITHUB_TOKEN
   docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest
   docker tag ghcr.io/itumelengmanota/fraud-detection-model:latest fraud-detection-model:latest
   
   # Or build locally if you have the ML model
   cd /path/to/fraud-detection-ml-model/scripts
   ./download_model_from_s3.sh
   ./build_docker_image.sh
   ```

2. **Drools Rule Compilation Errors**:
   ```
   Rule compilation errors: ...
   ```

   Solution:
   ```bash
   # Verify rule files syntax
   ls -la src/main/resources/rules/
   
   # Check for:
   # - Missing semicolons
   # - Incorrect package imports
   # - Syntax errors in DRL files
   ```

3. **Database Schema Issues**:
   ```
   Flyway migration failed
   ```

   Solution:
   ```bash
   # Reset database
   docker-compose -f docker-compose/compose.yml down -v
   docker-compose -f docker-compose/compose.yml up -d postgres
   
   # Wait 10 seconds for PostgreSQL to initialize
   sleep 10
   
   # Start application
   ./gradlew bootRun
   ```

#### Keycloak Configuration Issues

**Symptoms**:
- `Invalid issuer` errors
- Authentication failures
- `401 Unauthorized` responses

**Verify Keycloak setup**:

```bash
# Check Keycloak is running
curl http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration

# Verify realm was imported
docker logs keycloak | grep "Imported realm"

# Access Keycloak admin console
open http://localhost:8180
# Login: admin / admin
# Navigate to: Realms → fraud-detection
```

**Common Issues**:

1. **Realm not imported**:
   ```bash
   # Check if realm-export.json exists
   ls -la docker-compose/keycloak-config/realm-export.json
   
   # Restart Keycloak to re-import
   docker-compose -f docker-compose/compose.yml restart keycloak
   
   # Wait 30 seconds for startup
   sleep 30
   ```

2. **Client secrets mismatch**:
   ```yaml
   # Verify in application.yml
   springdoc:
     swagger-ui:
       oauth:
         client-id: fraud-detection-web
         client-secret: fraud-detection-web-secret  # Must match Keycloak
   ```

3. **JWT validation failures**:
   ```yaml
   # Check issuer-uri in application.yml
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             issuer-uri: http://localhost:8180/realms/fraud-detection
             jwk-set-uri: http://localhost:8180/realms/fraud-detection/protocol/openid-connect/certs
   ```

### Database Connection Issues

#### Connection Pool Exhausted

**Symptoms**:
```
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

**Diagnosis**:
```bash
# Check active connections
curl http://localhost:9001/actuator/metrics/hikaricp.connections.active

# Check connection pool config
curl http://localhost:9001/actuator/configprops | grep hikari
```

**Solution**:

```yaml
# Increase pool size in application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 100  # Increase from 50
      connection-timeout: 60000  # Increase timeout
```

**Identify connection leaks**:
```yaml
spring:
  datasource:
    hikari:
      leak-detection-threshold: 30000  # 30 seconds
```

Check logs for leak warnings:
```
java.lang.Exception: Apparent connection leak detected
```

#### Slow Queries

**Symptoms**:
- High response times (> 1 second)
- Database CPU utilization high

**Enable query logging**:
```yaml
# application.yml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**Find slow queries in PostgreSQL**:
```sql
-- Enable pg_stat_statements extension
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Find slowest queries
SELECT 
    query,
    mean_exec_time,
    calls,
    total_exec_time
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;
```

**Solution**:
- Add missing indexes (see Performance Tuning section)
- Optimize query patterns
- Add pagination to large result sets

#### Database Migration Failures

**Symptoms**:
```
FlywayException: Validate failed: Migrations have failed validation
```

**Check migration status**:
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;
```

**Force re-baseline**:
```bash
# Clean database (WARNING: destroys all data)
docker-compose -f docker-compose/compose.yml down -v
docker-compose -f docker-compose/compose.yml up -d postgres

# Wait for PostgreSQL
sleep 10

# Restart application (will run migrations)
./gradlew bootRun
```

**Fix checksum mismatch**:
```yaml
# application.yml - Only for development!
spring:
  flyway:
    validate-on-migrate: false
    out-of-order: true
```

### Kafka/Messaging Issues

#### Consumer Lag Growing

**Symptoms**:
- Messages piling up in topic
- Slow transaction processing
- High consumer lag in Kafka UI

**Check consumer lag**:
```bash
# Via Kafka UI
open http://localhost:8083

# Navigate to: Consumers → fraud-detection-service

# Or via CLI
docker exec fraud-detection-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group fraud-detection-service \
  --describe
```

**Solutions**:

1. **Increase consumer concurrency**:
   ```yaml
   # application.yml
   kafka:
     listener:
       concurrency: 20  # Increase from 10
   
   kafka:
     topics:
       transactions:
         concurrency: 20  # Match listener concurrency
   ```

2. **Optimize processing time**:
   - Enable caching (check Redis is working)
   - Reduce database queries
   - Optimize ML prediction timeout

3. **Increase partition count** (requires recreating topic):
   ```bash
   # Stop consumers first
   docker-compose -f docker-compose/compose.yml stop fraud-detection-service
   
   # Delete and recreate topic with more partitions
   docker exec fraud-detection-kafka kafka-topics \
     --bootstrap-server localhost:9092 \
     --delete --topic transactions.normalized
   
   docker exec fraud-detection-kafka kafka-topics \
     --bootstrap-server localhost:9092 \
     --create --topic transactions.normalized \
     --partitions 20 --replication-factor 1
   
   # Restart consumers
   docker-compose -f docker-compose/compose.yml start fraud-detection-service
   ```

#### Schema Registry Issues

**Symptoms**:
```
SerializationException: Error deserializing Avro message
Schema not found in registry
```

**Check Apicurio Registry**:
```bash
# Verify registry is accessible
curl http://localhost:8081/apis/registry/v2/groups

# List registered schemas
curl http://localhost:8081/apis/registry/v2/search/artifacts

# Check specific schema
curl http://localhost:8081/apis/registry/v2/groups/transactions.normalized/artifacts
```

**Solutions**:

1. **Schema not registered**:
   ```bash
   # Register schema manually via Apicurio UI
   open http://localhost:8082
   
   # Or restart application (will auto-register)
   ./gradlew bootRun
   ```

2. **Schema compatibility issue**:
   ```bash
   # Check compatibility mode
   curl http://localhost:8081/apis/registry/v2/groups/transactions.normalized/artifacts/TransactionAvro/rules
   
   # Update compatibility mode if needed
   curl -X POST http://localhost:8081/apis/registry/v2/groups/transactions.normalized/artifacts/TransactionAvro/rules \
     -H "Content-Type: application/json" \
     -d '{"type":"COMPATIBILITY","config":"BACKWARD"}'
   ```

3. **Cache issues**:
   ```yaml
   # Reduce schema cache time in application.yml
   spring:
     kafka:
       consumer:
         properties:
           apicurio.registry.check-period-ms: 10000  # Reduce from 30000
   ```

#### Consumer Rebalancing Issues

**Symptoms**:
```
Rebalance in progress
Partitions revoked
Consumer is not subscribed to any topics
```

**Check logs**:
```bash
# Look for rebalance events
./gradlew bootRun | grep -i rebalance
```

**Solutions**:

1. **Increase session timeout**:
   ```yaml
   spring:
     kafka:
       properties:
         session.timeout.ms: 60000  # Increase from 45000
         max.poll.interval.ms: 600000  # Increase from 300000
   ```

2. **Reduce processing time**:
   - Ensure processing completes within `max.poll.interval.ms`
   - Reduce `max-poll-records` if processing is slow

3. **Network issues**:
   ```bash
   # Check Kafka connectivity
   docker exec fraud-detection-kafka kafka-broker-api-versions \
     --bootstrap-server localhost:9092
   ```

#### Duplicate Message Processing

**Symptoms**:
- Same transaction processed multiple times
- Duplicate risk assessments in database

**Check duplicate detection**:
```bash
# Check Redis keys for seen messages
docker exec -it fraud-detection-redis redis-cli KEYS "seen:transaction:*" | head -10

# Check TTL on seen messages
docker exec -it fraud-detection-redis redis-cli TTL "seen:transaction:550e8400-e29b-41d4-a716-446655440001"
```

**Verify idempotency cache**:
```yaml
# application.yml
spring:
  cache:
    redis:
      time-to-live: 172800000  # 48 hours in milliseconds
```

**Solution**:

If duplicates still occur:
```java
// Check SeenMessageCache implementation
// Verify Redis transactions are enabled
spring:
  data:
    redis:
      enable-transaction-support: true
```

### Redis/Cache Issues

#### Redis Connection Failures

**Symptoms**:
```
io.lettuce.core.RedisConnectionException: Unable to connect to localhost:6379
```

**Check Redis status**:
```bash
# Verify Redis is running
docker ps | grep redis

# Test connection
redis-cli -h localhost -p 6379 ping
# Expected: PONG

# Check Redis logs
docker logs fraud-detection-redis
```

**Solution**:
```bash
# Restart Redis
docker-compose -f docker-compose/compose.yml restart redis

# Clear Redis data if corrupted
docker-compose -f docker-compose/compose.yml down
docker volume rm fraud-detection-redis-data
docker-compose -f docker-compose/compose.yml up -d redis
```

#### Cache Serialization Errors

**Symptoms**:
```
SerializationException: Could not read JSON
ClassNotFoundException during deserialization
```

**Check cache configuration**:
```yaml
spring:
  cache:
    type: redis
    redis:
      cache-null-values: false  # Don't cache null values
```

**Clear problematic cache entries**:
```bash
# Connect to Redis
docker exec -it fraud-detection-redis redis-cli

# List all keys
KEYS *

# Delete specific cache
DEL fraud-detection:mlPredictions::*

# Flush all cache (WARNING: clears all data)
FLUSHALL
```

**Solution**:

Update Redis serialization configuration if needed:
```java
// Ensure GenericJackson2JsonRedisSerializer is used
RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
```

#### Memory Issues

**Symptoms**:
```
OOM command not allowed when used memory > 'maxmemory'
```

**Check Redis memory**:
```bash
# Check memory usage
docker exec fraud-detection-redis redis-cli INFO memory

# Check used memory
docker exec fraud-detection-redis redis-cli INFO memory | grep used_memory_human
```

**Solution**:

1. **Increase Redis memory limit**:
   ```yaml
   # docker-compose/compose.yml
   redis:
     image: 'redis:7.4'
     command: redis-server --maxmemory 2gb --maxmemory-policy allkeys-lru
   ```

2. **Reduce cache TTL**:
   ```yaml
   spring:
     cache:
       redis:
         time-to-live: 86400000  # Reduce to 24 hours
   ```

3. **Clear old keys**:
   ```bash
   # Scan and delete old keys
   docker exec fraud-detection-redis redis-cli --scan --pattern "seen:transaction:*" | \
     xargs docker exec -i fraud-detection-redis redis-cli DEL
   ```

### Authentication/Authorization Errors

#### 401 Unauthorized

**Symptoms**:
- API returns 401 even with valid token
- Swagger UI authentication fails

**Verify token**:
```bash
# Get a fresh token
export ACCESS_TOKEN=$(curl -s -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=fraud-detection-web" \
  -d "client_secret=fraud-detection-web-secret" \
  -d "username=detector" \
  -d "password=detector123" \
  -d "grant_type=password" | jq -r '.access_token')

# Decode token to check claims
echo $ACCESS_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .

# Check expiration
echo $ACCESS_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .exp
```

**Common Issues**:

1. **Token expired**:
   ```bash
   # Get a new token (tokens expire after 5 minutes by default)
   # Run the token acquisition command again
   ```

2. **Wrong audience**:
   ```bash
   # Check token audience
   echo $ACCESS_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .aud
   # Should include: "fraud-detection-service"
   ```

3. **Incorrect issuer**:
   ```yaml
   # application.yml - must match Keycloak realm
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             issuer-uri: http://localhost:8180/realms/fraud-detection
   ```

#### 403 Forbidden

**Symptoms**:
- Token is valid but access denied
- Missing required authorities

**Check token permissions**:
```bash
# View token scopes and roles
echo $ACCESS_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq '.scope, .resource_access'
```

**Required authorities**:
- POST `/fraud/assessments` → requires `SCOPE_fraud:detect`
- GET `/fraud/assessments/**` → requires `SCOPE_fraud:read`

**Solution**:

1. **Use correct user**:
   ```bash
   # analyst - READ only
   # detector - READ + DETECT
   # admin - ALL permissions
   
   # Get token for detector user
   curl -X POST http://localhost:8180/realms/fraud-detection/protocol/openid-connect/token \
     -d "client_id=fraud-detection-web" \
     -d "client_secret=fraud-detection-web-secret" \
     -d "username=detector" \
     -d "password=detector123" \
     -d "grant_type=password"
   ```

2. **Fix Keycloak role mappings**:
   - Access Keycloak admin: http://localhost:8180
   - Navigate to: Realms → fraud-detection → Users
   - Select user → Role Mapping
   - Add missing client roles

#### Swagger UI OAuth2 Not Working

**Symptoms**:
- "Authorize" button doesn't work
- Redirect loop after authentication

**Check Swagger configuration**:
```yaml
# application.yml
springdoc:
  swagger-ui:
    oauth:
      client-id: fraud-detection-web
      client-secret: fraud-detection-web-secret
      use-pkce-with-authorization-code-grant: true
    oauth2-redirect-url: http://localhost:9001/swagger-ui/oauth2-redirect.html
```

**Verify redirect URI in Keycloak**:
1. Access Keycloak admin console
2. Navigate to: Clients → fraud-detection-web → Settings
3. Check "Valid Redirect URIs" includes:
   - `http://localhost:9001/*`
   - `http://localhost:9001/swagger-ui/*`
   - `http://localhost:9001/swagger-ui/oauth2-redirect.html`

**Solution**:
```bash
# Clear browser cache and cookies
# Try in incognito/private browsing mode

# Or use cURL for testing instead
curl -X POST http://localhost:9001/fraud/assessments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d @test-transaction.json
```

### ML Model Integration Issues

#### SageMaker Endpoint Timeout

**Symptoms**:
```
TimeLimiterException: Time limit exceeded
CircuitBreaker 'sagemakerML' is OPEN
```

**Check endpoint health**:
```bash
# Test local endpoint
curl http://localhost:8080/ping
# Expected: {}

# Check if container is running
docker ps | grep fraud-detection-model

# View model container logs
docker logs fraud-detection-model
```

**Solution**:

1. **Increase timeout**:
   ```yaml
   # application.yml
   aws:
     sagemaker:
       api-call-timeout: 15s        # Increase from 10s
       api-call-attempt-timeout: 10s # Increase from 5s
   
   resilience4j:
     timelimiter:
       instances:
         sagemakerML:
           timeout-duration: 2s  # Increase from 500ms
   ```

2. **Check model container resources**:
   ```bash
   # Monitor container resources
   docker stats fraud-detection-model
   
   # Ensure container has enough CPU/memory
   ```

3. **Circuit breaker opened**:
   ```bash
   # Check circuit breaker state
   curl http://localhost:9001/actuator/metrics/resilience4j.circuitbreaker.state
   
   # Wait for circuit breaker to close (60 seconds)
   # Or restart application
   ./gradlew bootRun
   ```

#### Model Prediction Errors

**Symptoms**:
```
Error invoking SageMaker endpoint
Invalid model input
```

**Test endpoint directly**:
```bash
# Test with sample data
curl -X POST http://localhost:8080/invocations \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.0,
    "transaction_type": 0,
    "channel": 2,
    "merchant_category": 3,
    "hour": 14,
    "day_of_week": 3,
    "is_domestic": 1,
    "is_weekend": 0,
    "has_device": 1,
    "distance_from_home": 0.0,
    "transactions_last_24h": 5,
    "amount_last_24h": 500.0,
    "new_merchant": 0
  }'
```

**Check feature extraction**:
```bash
# Enable debug logging
# application.yml
logging:
  level:
    com.twenty9ine.frauddetection.infrastructure.adapter.ml: DEBUG
```

**Solution**:

1. **Missing account profile**:
   - Model needs account data for feature extraction
   - Check Account Service (Mockoon) is running
   - Verify account exists: `curl http://localhost:3001/accounts/ACC-12345-1/profiles`

2. **Missing transaction history**:
   - Model uses last 24 hours of transactions
   - Submit multiple transactions for the same account first

3. **Invalid feature values**:
   - Check logs for feature extraction values
   - Verify all numeric features are valid

#### Scaling Issues

**Symptoms**:
- Raw probability values outside expected range
- Fraud scores always too high or too low

**Check scaling configuration**:
```yaml
aws:
  sagemaker:
    scaling:
      min-raw-probability: 0.00001
      max-raw-probability: 0.01
```

**Verify scaling logic**:
```bash
# Check logs for raw vs scaled probabilities
./gradlew bootRun | grep -i "probability"

# Should see:
# Raw probability: 0.00045123, Scaled fraud probability: 0.1234
```

**Solution**:

Adjust scaling parameters based on your model's output range:
```yaml
aws:
  sagemaker:
    scaling:
      min-raw-probability: 0.0001  # Adjust based on model
      max-raw-probability: 0.1     # Adjust based on model
```

### Performance Issues

#### High Response Times

**Symptoms**:
- API responses > 1 second
- Timeouts under load

**Profile the application**:
```bash
# Enable Spring Boot actuator metrics
curl http://localhost:9001/actuator/metrics/http.server.requests

# Check slow endpoints
curl 'http://localhost:9001/actuator/metrics/http.server.requests?tag=uri:/fraud/assessments'
```

**Common Bottlenecks**:

1. **Database queries**:
   ```bash
   # Check connection pool
   curl http://localhost:9001/actuator/metrics/hikaricp.connections.active
   
   # Enable SQL logging
   logging.level.org.hibernate.SQL=DEBUG
   ```

2. **Cache misses**:
   ```bash
   # Check cache hit rate
   docker exec fraud-detection-redis redis-cli INFO stats | grep keyspace
   ```

3. **ML predictions**:
   ```bash
   # Check prediction duration
   curl http://localhost:9001/actuator/metrics/sagemaker.prediction.duration
   
   # Verify circuit breaker isn't open
   curl http://localhost:9001/actuator/health/circuitBreakers
   ```

**Solutions**:
- See "Performance Tuning" section
- Enable caching for ML predictions
- Add database indexes
- Increase connection pool size
- Optimize Kafka consumer concurrency

#### Memory Issues

**Symptoms**:
```
OutOfMemoryError: Java heap space
Container killed by OOMKiller
```

**Check memory usage**:
```bash
# JVM memory
curl http://localhost:9001/actuator/metrics/jvm.memory.used

# Docker container memory
docker stats fraud-detection-service
```

**Solution**:

1. **Increase heap size**:
   ```bash
   # Set JAVA_OPTS environment variable
   export JAVA_OPTS="-Xms512m -Xmx2g"
   ./gradlew bootRun
   ```

2. **Docker memory limit**:
   ```yaml
   # docker-compose/compose.yml
   fraud-detection-service:
     deploy:
       resources:
         limits:
           memory: 4g
         reservations:
           memory: 2g
   ```

3. **Enable GC logging**:
   ```bash
   export JAVA_OPTS="-Xlog:gc*:file=/tmp/gc.log"
   ```

4. **Check for memory leaks**:
   ```bash
   # Generate heap dump
   jmap -dump:live,format=b,file=heap.bin $(pgrep java)
   
   # Analyze with VisualVM or Eclipse MAT
   ```

### Integration Test Failures

#### Testcontainers Issues

**Symptoms**:
```
Could not start container
Testcontainer startup timeout
```

**Check Docker daemon**:
```bash
# Verify Docker is running
docker info

# Check Docker disk space
docker system df
```

**Solution**:

1. **Enable Testcontainers reuse**:
   ```properties
   # ~/.testcontainers.properties
   testcontainers.reuse.enable=true
   ```

2. **Increase startup timeout**:
   ```java
   // In test configuration
   container.withStartupTimeout(Duration.ofMinutes(5));
   ```

3. **Clean up old containers**:
   ```bash
   # Remove stopped containers
   docker container prune -f
   
   # Remove unused images
   docker image prune -a -f
   
   # Remove unused volumes
   docker volume prune -f
   ```

#### Model Image Not Found

**Symptoms**:
```
⚠️  WARNING: Local model image not found!
Tests fail with connection refused to model endpoint
```

**Solution**:

```bash
# Pull model from GitHub Container Registry
docker login ghcr.io -u YOUR_USERNAME -p YOUR_GITHUB_TOKEN
docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest
docker tag ghcr.io/itumelengmanota/fraud-detection-model:latest fraud-detection-model:latest

# Verify image exists
docker images | grep fraud-detection-model

# Run tests
./gradlew test
```

#### Test Database Connection Issues

**Symptoms**:
```
Failed to connect to test database
Flyway migration failed in tests
```

**Check Testcontainers PostgreSQL**:
```bash
# View test logs
./gradlew test --info | grep -i postgres

# Increase Docker resources if needed
# Docker Desktop → Settings → Resources
```

**Solution**:

1. **Wait for database initialization**:
   ```java
   @Testcontainers
   class IntegrationTest {
       @Container
       static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
           .withStartupAttempts(3)
           .withStartupTimeout(Duration.ofMinutes(2));
   }
   ```

2. **Clear test cache**:
   ```bash
   ./gradlew clean test
   ```

### Getting Help

If you continue experiencing issues:

1. **Check logs**:
   ```bash
   # Application logs
   ./gradlew bootRun --console=plain > app.log 2>&1
   
   # Docker logs
   docker-compose -f docker-compose/compose.yml logs > docker.log
   
   # Specific service
   docker logs fraud-detection-service > service.log
   ```

2. **Enable debug logging**:
   ```yaml
   # application.yml
   logging:
     level:
       com.twenty9ine.frauddetection: DEBUG
       org.springframework: DEBUG
   ```

3. **Gather diagnostics**:
   ```bash
   # System info
   docker info > docker-info.txt
   java -version > java-version.txt
   ./gradlew --version > gradle-version.txt
   
   # Service health
   curl http://localhost:9001/actuator/health > health.json
   curl http://localhost:9001/actuator/env > environment.json
   ```

4. **Search existing issues**:
   - GitHub Issues: [Project Repository]
   - Stack Overflow: Tag `spring-boot` + `fraud-detection`

5. **Report new issues**:
   Include:
   - Error messages and stack traces
   - Configuration files (remove sensitive data)
   - Steps to reproduce
   - Environment details (OS, Docker version, Java version)

---
## Additional Resources

### Service URLs

- **Application**: http://localhost:9001
- **OpenAPI Spec**: http://localhost:9001/v3/api-docs
- **Swagger UI**: http://localhost:9001/swagger-ui.html
- **Actuator**: http://localhost:9001/actuator
- **ML Model**: http://localhost:8080
- **Mockoon (Account Service)**: http://localhost:3001
- **Keycloak Admin**: http://localhost:8180/admin
- **Apicurio UI**: http://localhost:8082
- **Kafka UI**: http://localhost:8083
- **Grafana**: http://localhost:3000

### Documentation

- **Spring Boot**: https://docs.spring.io/spring-boot/
- **Spring Security OAuth2**: https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html
- **XGBoost**: https://xgboost.readthedocs.io/
- **SageMaker Local Mode**: https://sagemaker.readthedocs.io/en/stable/overview.html#local-mode
- **AWS SageMaker**: https://docs.aws.amazon.com/sagemaker/
- **Mockoon**: https://mockoon.com/docs/
- **Keycloak**: https://www.keycloak.org/documentation
- **Apache Kafka**: https://kafka.apache.org/documentation/
- **Drools**: https://docs.drools.org/
- **Apicurio Registry**: https://www.apicur.io/registry/docs/
- **Testcontainers**: https://testcontainers.com/

---
## TODO
- [ ] Add API Gateway with rate limiting
- [ ] Replace Resilience4j with Spring Framework 7 native resilience features
- [ ] Externalise configurations
- [ ] Add Kubernetes deployment manifests
---

**Last Updated**: 16 January 2026  
**Maintained By**: Ignatius Itumeleng Manota - itumeleng.manota@gmail.com  
**Version**: 1.0.4

---
