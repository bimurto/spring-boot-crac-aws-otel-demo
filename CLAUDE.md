# CLAUDE.md

## Project Overview

Spring Boot 3.3.11 demo app (Kotlin 1.9.25, Java 21) showcasing CRaC checkpoint/restore with AWS services and OpenTelemetry. The app integrates S3, SQS, and SNS via Spring Cloud AWS, with CRaC-aware OTel exporters that suppress telemetry until restore.

## Build & Run

```bash
# Start local infra (PostgreSQL + LocalStack)
docker-compose up -d

# Build
./gradlew bootJar

# Run locally (no CRaC)
./gradlew bootRun

# Run with CRaC
./scripts/crac/local/start_api.sh
./scripts/crac/local/create-checkpoint.sh
./scripts/crac/local/from_checkpoint_api.sh
```

## Test

```bash
./gradlew test
```

## Project Structure

- `src/main/kotlin/io/bimurto/crac/` — Main source root
  - `config/` — AWS clients, CRaC lifecycle, OTel customizers, local resource initializer
  - `listener/` — SQS message listeners (AbstractListener base + SqsListener)
  - `messaging/` — SNS and SQS notification senders
- `scripts/crac/` — Checkpoint creation and restore scripts (local and CI/CD)
- `src/main/resources/` — Spring config: `application.yml`, `application-local.yml`, `application-crac.yml`

## Key Architecture Decisions

- **CRaC-aware OTel exporters**: `CracSpanExporter`, `CracMetricExporter`, `CracLogRecordExporter` wrap base exporters and drop all telemetry until `CracConfig.isRestored` is true. This prevents checkpoint-phase data from polluting observability.
- **Conditional CRaC activation**: `CracConfig` is gated by `crac.enabled=true` property and `crac` Spring profile.
- **Local AWS bootstrapping**: `SqsLocalInitializer` auto-creates S3 bucket, SNS topic, and SQS queue on LocalStack when running with `local` profile (skipped during CRaC checkpoint).
- **Encrypted checkpoints**: Checkpoint pipeline encrypts with AES-256-CBC before S3 upload, versioned by `APP_VERSION` and auto-incrementing directory number.

## Spring Profiles

- `local` (default) — LocalStack endpoints, SQL logging, auto-creates AWS resources
- `crac` — Enables CRaC hooks, HikariCP pool suspension, disables JDBC metadata access, suppresses OTel exports until restore

## Dependencies

- Spring Boot 3.3.11 (web, data-jpa, actuator)
- Spring Cloud AWS 3.2.1 (S3, SNS, SQS)
- OpenTelemetry Instrumentation BOM 2.19.0
- PostgreSQL + Flyway 8.5.13 (migrations currently disabled)
- BellSoft Liberica JDK 21 with CRaC support
- Jackson with SNAKE_CASE naming strategy
- Logstash Logback Encoder for JSON logging

## Code Style

- Kotlin with strict JSR-305 null safety (`-Xjsr305=strict`)
- JVM target: 21
- Jackson property naming: SNAKE_CASE
- Spring Boot layered jar packaging (output: `demo.jar`)
