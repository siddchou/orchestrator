# Job Orchestration Platform

A configurable, UI-driven job orchestration system built with Spring Boot and Angular. Designed to replace Windows batch scripts with a Linux-compatible platform.

> **Documentation:** A new MkDocs documentation site is being set up at [docs-site/](docs-site/index.md). Once content migration is complete, this README will be shortened to point there.

## Overview

This platform provides:

- **Job Definition**: Create workflows composed of multiple steps (Java execution, SFTP transfers, log cleanup, archiving)
- **Scheduling**: Cron-based scheduling for automated job runs
- **Execution Engine**: Robust step-by-step job execution with error handling and logging
- **Real-time Monitoring**: Live run status and logs via Server-Sent Events (SSE)
- **Credential Management**: Secure storage for passwords and SSH keys

## Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.x, Spring Data JPA |
| Frontend | Angular 22+, TypeScript, Material Design |
| Database | Oracle 19c |
| Build | Maven, Angular CLI |

## Quick Start

### Prerequisites

- Java 21 JDK (`JAVA_HOME` configured)
- Maven 3.9+
- Node.js 20+ and npm
- Oracle 19c database

### Building the Project

```bash
# Build frontend and package all resources
mvn clean package

# Or build without running tests
mvn clean package -DskipTests
```

The build produces a fat JAR with all dependencies and compiled Angular assets.

### Running the Application

```bash
# Run with default H2 database (development)
mvn spring-boot:run

# Run with Oracle database
mvn spring-boot:run -Dspring.profiles.active=oracle
```

Access the application at `http://localhost:8080`

## Project Structure

```
orchestrator/
├── src/main/java/com/novakai/orchestrator/
│   ├── api/              # REST API controllers and DTOs
│   ├── config/           # Spring configuration classes
│   ├── domain/           # Domain models and enums
│   ├── engine/           # Job execution engine
│   │   └── executors/    # Step type implementations (JAVA_EXEC, SFTP, LOG_CLEANUP, ARCHIVE)
│   └── repository/       # JPA repositories
├── src/main/resources/
│   ├── db/migration/     # Flyway database migrations
│   └── static/           # Compiled Angular frontend
└── src/test/             # Unit and integration tests

orchestrator-ui/          # Angular application source
```

## Step Types

### 1. JAVA_EXEC
Execute Java classes or JAR files with configurable JVM arguments and timeouts.

**Configuration:**
- Main class (or JAR path)
- JVM args (space-separated)
- Program args (space-separated)
- Timeout in minutes

### 2. SFTP
Transfer files via SFTP protocol with support for password and SSH key authentication.

**Configuration:**
- Host and port
- Username
- Credential reference (PASSWORD or SSH_KEY type)
- Remote directory path
- File pattern (glob syntax)
- Direction: UPLOAD or DOWNLOAD
- Connection and auth timeouts

### 3. LOG_CLEANUP
Remove old log files matching a pattern.

**Configuration:**
- Directory to scan
- File pattern (e.g., `*.log`, `job_*.txt`)
- Additional patterns (comma-separated)

### 4. ARCHIVE
Compress files into ZIP or TAR_GZ archives with optional deletion of originals.

**Configuration:**
- Source directory
- File patterns (comma-separated)
- Archive format: ZIP or TAR_GZ
- Delete original files after archiving

### 5. ENV_SETUP (Internal)
Set environment variables for job execution context.

## Authentication & Security

The platform supports:

- **JWT-based authentication**: Token-based auth with configurable expiration
- **Role-based access control (RBAC)**: ADMIN vs USER roles
- **Credential encryption**: Sensitive data encrypted at rest using AES-256

### Credential Types

| Type | Description |
|------|-------------|
| `PASSWORD` | Plain text password for SFTP or other services |
| `SSH_KEY` | SSH private key content (PEM format) or file path |

## API Endpoints

### Jobs
- `GET /api/jobs` - List jobs with pagination and search
- `POST /api/jobs` - Create a new job
- `PUT /api/jobs/{id}` - Update a job
- `DELETE /api/jobs/{id}` - Delete a job
- `GET /api/jobs/{id}/steps` - List job steps
- `POST /api/jobs/{id}/steps` - Add a step to a job

### Execution
- `POST /api/jobs/{id}/run` - Trigger a manual run
- `GET /api/runs` - List execution history with filters
- `GET /api/runs/{id}` - Get detailed run information
- `GET /api/runs/{id}/logs` - SSE stream for real-time logs

### Scheduling
- `PUT /api/jobs/{id}/schedule` - Create/update schedule
- `DELETE /api/jobs/{id}/schedule` - Delete schedule
- `POST /api/system/cron/validate` - Validate cron expression

### Configuration
- `GET /api/config/env` - Get global environment variables
- `POST /api/config/env` - Add global environment variable
- `DELETE /api/config/env/{id}` - Remove global environment variable
- `GET /api/system/health` - Health check endpoint

## Frontend Usage

### Starting the Development Server

```bash
cd orchestrator-ui
ng serve
```

The UI will be available at `http://localhost:4200`

### Available Routes

| Route | Description |
|-------|-------------|
| `/login` | Authentication page |
| `/dashboard` | Overview with stats and recent runs |
| `/jobs` | Job list and management |
| `/jobs/new` | Create new job |
| `/jobs/:id` | Edit existing job |
| `/runs` | Execution history and filters |
| `/runs/:id` | Run details with logs |
| `/config` | Global configuration (ADMIN only) |

## Database Schema

The database uses Flyway for schema management. Key tables:

- `JOB_DEFINITION` - Job metadata and configuration
- `JOB_STEP` - Individual steps within a job
- `JOB_RUN` - Execution records
- `JOB_RUN_STEP` - Per-step execution details
- `JOB_CREDENTIAL` - Encrypted credentials storage
- `SYSTEM_ENV_VAR` - Global environment variables

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SftpStepExecutorTest

# Code coverage report
mvn clean test jacoco:report
```

## Deployment

### Production Build

```bash
# Clean build with frontend optimization
mvn clean package -Pprod

# The resulting JAR is self-contained with embedded Angular assets
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

### Docker (Optional)

A Dockerfile can be created using:

```dockerfile
FROM eclipse-temurin:21-jdk
COPY target/orchestrator-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## Phase Development

This project follows a phased development approach:

| Phase | Focus |
|-------|-------|
| Phase 1 | Database schema and JPA entities |
| Phase 2 | Job execution engine (all step types) |
| Phase 3 | REST API and SSE log streaming |
| Phase 4 | Cron-based scheduling |
| Phase 5 | Angular UI implementation |
| Phase 6 | Authentication and security |
| Phase 7 | Production deployment setup |

See `plan/README.md` for detailed phase information.

## License

MIT
