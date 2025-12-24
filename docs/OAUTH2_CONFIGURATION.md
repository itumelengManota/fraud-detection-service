# OAuth2 Architecture - Fraud Detection Service

## Overview
This service uses Keycloak for OAuth2 authentication with proper separation between:
- **Web Client**: User authentication (Swagger UI, web applications)
- **Kafka Client**: Service-to-service authentication (Kafka broker)

## Keycloak Configuration

### Realm: fraud-detection

#### Clients

##### 1. fraud-detection-service (Resource Server)
- **Type**: Bearer-only
- **Purpose**: Validates JWT tokens from both web and Kafka clients
- **Secret**: fraud-detection-secret

##### 2. fraud-detection-web (Web Client)
- **Type**: Confidential
- **Grant Types**: Authorization Code, Password (dev/test only)
- **Purpose**: User authentication for Swagger UI and web applications
- **Secret**: fraud-detection-web-secret
- **Redirect URIs**: `http://localhost:9001/*`
- **Scopes**: openid, profile, email, fraud-detection-scopes

##### 3. fraud-detection-kafka (Service Client)
- **Type**: Confidential
- **Grant Types**: Client Credentials ONLY
- **Purpose**: Service-to-service authentication for Kafka
- **Secret**: fraud-detection-kafka-secret
- **Scopes**: kafka
- **Service Account**: Enabled

#### Users
- **analyst** (password: analyst123) - Role: fraud_analyst
- **detector** (password: detector123) - Role: fraud_detector  
- **admin** (password: admin123) - Role: fraud_admin

## Application Configuration

### Spring Security (REST API)
```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: http://localhost:8180/realms/fraud-detection
  audiences: fraud-detection-service, fraud-detection-web
```

### Kafka Security
```yaml
spring.kafka.properties.sasl.jaas.config:
  clientId: fraud-detection-kafka
  clientSecret: fraud-detection-kafka-secret
  scope: kafka
```

### Swagger UI
```yaml
springdoc.swagger-ui.oauth:
  client-id: fraud-detection-web
  client-secret: fraud-detection-web-secret
```

## Environment Variables

### Development
```bash
# Keycloak
export JWT_ISSUER_URI=http://localhost:8180/realms/fraud-detection

# Kafka OAuth
export KAFKA_CLIENT_ID=fraud-detection-kafka
export KAFKA_CLIENT_SECRET=fraud-detection-kafka-secret
```

### Production
```bash
# Keycloak
export JWT_ISSUER_URI=https://keycloak.prod.example.com/realms/fraud-detection
export JWT_JWK_SET_URI=https://keycloak.prod.example.com/realms/fraud-detection/protocol/openid-connect/certs

# Kafka OAuth
export KAFKA_CLIENT_ID=fraud-detection-kafka
export KAFKA_CLIENT_SECRET=${VAULT_KAFKA_CLIENT_SECRET}
export KAFKA_SECURITY_PROTOCOL=SASL_SSL
```

## Testing

### Unit/Integration Tests
Tests use `application-test.yml` with Kafka OAuth disabled (PLAINTEXT).

### Swagger UI Testing
1. Navigate to http://localhost:9001/swagger-ui.html
2. Click "Authorize"
3. Credentials:
   - Client ID: fraud-detection-web
   - Client Secret: fraud-detection-web-secret
4. Login with: detector / detector123

## Token Lifespans
- **Web tokens**: 30 minutes (with refresh)
- **Kafka tokens**: 60 minutes (no refresh, auto-renewed)

## Security Notes
- Never commit client secrets to version control
- Use environment variables or secret management (Vault, AWS Secrets Manager)
- Rotate secrets regularly (minimum quarterly)
- Use SASL_SSL in production (not SASL_PLAINTEXT)
