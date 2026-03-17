# Spring Boot CRaC + AWS + OpenTelemetry Demo

A Spring Boot 3.3 application demonstrating [CRaC (Coordinated Restore at Checkpoint)](https://docs.azul.com/core/crac) for near-instant container startup, integrated with AWS services (S3, SQS, SNS) and OpenTelemetry observability.

## Overview

CRaC allows a running JVM to be checkpointed (snapshotted) and later restored, skipping the entire startup and warmup phase. This demo shows how to:

- Configure CRaC with Spring Boot and manage lifecycle callbacks
- Integrate OpenTelemetry so that telemetry is suppressed during checkpoint and only exported after restore
- Use AWS SDK v2 clients (S3, SQS, SNS) with OpenTelemetry tracing interceptors
- Automate checkpoint creation, encryption, and S3 storage for deployment pipelines

## Tech Stack

| Component          | Version / Detail                              |
|--------------------|-----------------------------------------------|
| Kotlin             | 1.9.25                                        |
| Java               | 21 (BellSoft Liberica JDK with CRaC support)  |
| Spring Boot        | 3.3.11                                        |
| Spring Cloud AWS   | 3.2.1                                         |
| OpenTelemetry      | Instrumentation BOM 2.19.0                    |
| Database           | PostgreSQL 14.15                              |
| Migrations         | Flyway 8.5.13 (currently disabled)            |
| Local AWS          | LocalStack 4.3.0                              |
| Build              | Gradle 8.13 (Kotlin DSL)                      |

## Project Structure

```
src/main/kotlin/io/bimurto/crac/
├── Application.kt                    # Spring Boot entry point
├── config/
│   ├── AwsConfig.kt                  # S3, SQS, SNS clients with OTel tracing
│   ├── CracConfig.kt                 # CRaC lifecycle (checkpoint/restore hooks)
│   ├── OpenTelemetryConfig.kt        # OTel customizers, CRaC-aware exporters
│   └── SqsLocalInitializer.kt       # Auto-creates local AWS resources
├── listener/
│   ├── AbstractListener.kt           # Base SQS message handler
│   └── SqsListener.kt               # Listens on "demo-queue"
└── messaging/
    ├── SNSNotificationSender.kt      # SNS publisher
    └── SQSNotificationSender.kt      # SQS publisher

scripts/crac/
├── automatic_checkpoint_creation.sh  # Full checkpoint pipeline (create, encrypt, upload to S3)
├── from_checkpoint_api.sh            # Restore API from S3 checkpoint
└── local/                            # Local development scripts
    ├── create-checkpoint.sh
    ├── start_api.sh
    └── from_checkpoint_api.sh
```

## Prerequisites

- Java 21 (CRaC-enabled JDK, e.g., BellSoft Liberica or Azul Zulu)
- Docker & Docker Compose
- Gradle 8.13+ (or use the wrapper)

## Getting Started

### 1. Start local infrastructure

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** on port `5432`
- **LocalStack** on port `4566` (S3, SQS, SNS)

### 2. Build the application

```bash
./gradlew bootJar
```

### 3. Run locally (without CRaC)

```bash
./gradlew bootRun
```

The application starts with the `local` profile, which auto-creates AWS resources (S3 bucket, SNS topic, SQS queue) in LocalStack via `SqsLocalInitializer`.

### 4. Run with CRaC (local)

```bash
# Start the app with CRaC enabled
./scripts/crac/local/start_api.sh

# Create a checkpoint
./scripts/crac/local/create-checkpoint.sh

# Restore from checkpoint
./scripts/crac/local/from_checkpoint_api.sh
```

## Spring Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Default. Points AWS clients at LocalStack, auto-creates resources, enables SQL logging |
| `crac`  | Enables CRaC lifecycle hooks, suspends HikariCP pool, disables JDBC metadata access, suppresses OTel exports until restore |

## CRaC Integration Details

### Checkpoint/Restore Lifecycle

`CracConfig` registers as a CRaC `Resource`:
- **Before checkpoint**: Logs and sleeps 5s to allow socket closure
- **After restore**: Sets `isRestored = true`, enabling telemetry export

### OpenTelemetry + CRaC

Custom exporters (`CracSpanExporter`, `CracMetricExporter`, `CracLogRecordExporter`) wrap the base exporters and silently drop all telemetry until the application is restored from a checkpoint. This prevents stale checkpoint-phase data from reaching your observability backend.

A `UrlPathSampler` allows dropping spans for specific URL paths via the `OTEL_DROP_SPANS` environment variable (comma-separated paths).

### Automated Checkpoint Pipeline

`scripts/crac/automatic_checkpoint_creation.sh`:
1. Starts the application with CRaC JVM flags
2. Polls the health endpoint until healthy
3. Creates a checkpoint via `jcmd <PID> JDK.checkpoint`
4. Compresses and encrypts the checkpoint (AES-256-CBC)
5. Uploads the encrypted checkpoint to S3

`scripts/crac/from_checkpoint_api.sh`:
1. Downloads the latest checkpoint from S3
2. Decrypts and extracts
3. Restores the JVM with `-XX:CRaCRestoreFrom=/checkpoint`

## Docker

The Dockerfile uses a multi-stage build with BellSoft Liberica JDK 21 CRaC:

```bash
./gradlew bootJar
docker build -t spring-boot-crac-demo .
```

## Configuration

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles | `local` |
| `DB_HOSTNAME` | PostgreSQL host | `localhost:5432` |
| `DB_NAME` | Database name | `demo` |
| `AWS_DEFAULT_REGION` | AWS region | `eu-west-1` |
| `CRAC_ENABLED` | Enable CRaC lifecycle | `true` (in `crac` profile) |
| `CRAC_CHECKPOINT_SECRET` | Encryption passphrase for checkpoints | Required for checkpoint scripts |
| `S3_BUCKET` | S3 bucket for checkpoint storage | Required for checkpoint scripts |
| `APP_VERSION` | Version prefix for S3 checkpoint path | Required for checkpoint scripts |
| `OTEL_DROP_SPANS` | Comma-separated URL paths to drop from traces | — |

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `/health/check` | Health check |
| `/health/check/lb` | Load balancer info |
| `/spring-metrics` | Spring metrics |
