# Fraud Detection Model Image

## Overview

The fraud detection model runs as a containerized service that provides ML predictions
via HTTP endpoints. The image is built and published via GitHub Actions.

## Image Location

**GitHub Container Registry:**
```
ghcr.io/itumelengmanota/fraud-detection-model:latest
```

## Local Development

### Option 1: Pull from GHCR (Recommended)
```bash
# Authenticate with GitHub (one-time setup)
echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin

# Pull the image
docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest

# Start all services
cd docker-compose
docker-compose up -d
```

### Option 2: Build Locally

If you need to build the image locally (e.g., for testing model changes):
```bash
# 1. Navigate to ML project
cd /Users/ignatiusitumelengmanota/git/fraud-detection-ml-model/scripts

# 2. Download model from S3
./download_model_from_s3.sh

# 3. Build Docker image
./build_docker_image.sh

# 4. Start services
cd /Users/ignatiusitumelengmanota/git/fraud-detection-service/docker-compose
docker-compose up -d
```

## GitHub Personal Access Token

To pull private images from GHCR, create a token with `packages:read` scope:

1. Go to: https://github.com/settings/tokens/new
2. Select scopes: `read:packages`
3. Generate token
4. Login to GHCR:
```bash
   echo YOUR_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin
```

## Testing the Model Service
```bash
# Health check
curl http://localhost:8080/ping

# Prediction
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

## Image Update Process

The model image is automatically rebuilt and published when:
- Code is pushed to the `main` branch of the ML project
- Changes are made to `scripts/` directory
- A new release is created
- Manual workflow trigger

### Checking for Updates
```bash
# Pull latest version
docker pull ghcr.io/itumelengmanota/fraud-detection-model:latest

# Restart services to use new image
cd docker-compose
docker-compose down
docker-compose up -d
```

## Troubleshooting

### Authentication Issues
```
Error response from daemon: unauthorized
```

**Solution:** Re-authenticate with GHCR:
```bash
echo YOUR_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin
```

### Image Not Found
```
Error response from daemon: manifest unknown
```

**Solution:** Check if the image has been published:
- Visit: https://github.com/itumelengmanota/fraud-detection-model/pkgs/container/fraud-detection-model

### Different Architecture

If running on ARM (M1/M2 Mac) or AMD64 (Intel):
```bash
# Pull specific architecture
docker pull --platform linux/arm64 ghcr.io/itumelengmanota/fraud-detection-model:latest
# or
docker pull --platform linux/amd64 ghcr.io/itumelengmanota/fraud-detection-model:latest
```

## CI/CD Integration

The Spring Boot application's GitHub Actions automatically:
1. Authenticates with GHCR
2. Pulls the model image
3. Runs integration tests using the model
4. No manual intervention needed