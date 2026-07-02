# Setup Guide — Novakai Orchestrator

A job orchestration platform that lets you define jobs composed of ordered steps, trigger them manually or via cron schedules, and monitor live log output through a browser dashboard.

---

## Table of Contents

- [System Requirements](#system-requirements)
- [Quick Start](#quick-start)
- [Database Setup](#database-setup)
- [Configuration](#configuration)
- [Build](#build)
- [Run Modes](#run-modes)
- [Development Mode](#development-mode)
- [CLI Mode](#cli-mode)
- [Initial Login](#initial-login)
- [Troubleshooting](#troubleshooting)

---

## System Requirements

| Component | Version |
|---|---|
| JDK | 21 (or later) |
| Maven | 3.8+ |
| Oracle Database | 19c (production) |
| Node.js | 22.22.3 (auto-installed by frontend-maven-plugin) |

The build plugin auto-installs Node.js and npm, so you do not need a separate Node installation for production builds. You only need it if running the Angular dev server independently.

---

## Quick Start

```bash
# Clone and enter the project
git clone <repo-url>
cd orchestrator

# Set required environment variables
export DB_HOST="your-oracle-host"
export DB_SERVICE="your-oracle-service"
export DB_USER="your_db_user"
export DB_PASSWORD="your_db_password"
export JWT_SECRET="at-least-32-characters-long-secret-key"

# Build (includes frontend)
mvn clean package

# Run
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

The server starts on port **8080**. Open `http://localhost:8080` in your browser.

---

## Database Setup

### Production (Oracle)

Flyway runs automatically on startup and applies all pending migrations. No manual schema setup is required — just ensure the database user has `CREATE TABLE` privileges.

**Migrations applied:**

| Migration | Creates |
|---|---|
| V1 | `JOB_DEFINITION`, `JOB_STEP`, `JOB_ENV_VAR` |
| V2 | `JOB_RUN`, `JOB_RUN_STEP` |
| V3 | `JOB_SCHEDULE`, `JOB_CREDENTIAL`, `AUDIT_LOG` |
| V4 | `APP_USER` + seed users |
| V5 | `JAVA_HOME` and `CLASSPATH` columns on `JOB_DEFINITION` |

### Test (H2)

For local testing without Oracle, use the test profile:

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar --spring.profiles.active=test
```

This uses a file-based H2 database at `data/orchestrator_test.mv.db` with Flyway disabled and `ddl-auto=update`. The H2 console is available at `http://localhost:8080/h2-console`.

---

## Configuration

All configuration lives in `src/main/resources/application.yml` (and profile-specific variants). Override any property via environment variables or JVM system properties.

### Required Environment Variables

| Variable | Description |
|---|---|
| `DB_HOST` | Oracle database hostname |
| `DB_SERVICE` | Oracle service name |
| `DB_USER` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing key (minimum 32 characters) |

### Optional Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ORCH_HOME` | `/opt/orchestrator` | Base directory for archives and SSH known_hosts |
| `ORCHESTRATOR_ENCRYPTION_KEY` | *(none)* | AES key for encrypting stored credentials |
| `JAVA_OPTS` | *(none)* | JVM options (e.g., `-Xms512m -Xmx1g`) |
| `LOG_FILE` | `logs/orchestrator.log` | Log file path |

### Spring Profiles

| Profile | Purpose |
|---|---|
| **default (web)** | Full web app with Oracle DB, Flyway, file logging |
| **cli** | Headless mode — no web server, `CommandLineRunner` drives execution |
| **test** | H2 file-based DB, Flyway disabled, seed users auto-created |

---

## Build

### Full build (backend + frontend)

```bash
mvn clean package
```

This builds the Angular frontend (auto-installs Node.js via `frontend-maven-plugin`) and packages everything into a fat JAR at `target/orchestrator-0.0.1-SNAPSHOT.jar`.

### Skip frontend build (faster backend iteration)

```bash
mvn clean package -DskipTests -Dskip.npm
```

### Run tests

```bash
mvn test
# or with explicit test profile
mvn -Ptest test
```

---

## Run Modes

### Web Mode (default)

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

Starts the embedded Tomcat on port 8080 with the full Angular SPA and REST API.

### With specific profile

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar --spring.profiles.active=cli
```

---

## Development Mode

Run the backend and frontend separately for hot-reload:

**Terminal 1 — Backend:**
```bash
mvn spring-boot:run
```

**Terminal 2 — Frontend:**
```bash
cd orchestrator-ui
npm install
ng serve
```

The Angular dev server runs on port **4200** and proxies API calls to port 8080. Open `http://localhost:4200`.

---

## CLI Mode

Run jobs from the terminal without a web server:

```bash
# Run a full job by name
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar --spring.profiles.active=cli run-job "MyJob"

# Run a single step by name
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar --spring.profiles.active=cli run-step "MyStep"
```

Or use the bundled shell scripts:

**Linux:**
```bash
source scripts/auth.sh
run_job "MyJob"
```

**Windows:**
```cmd
call scripts\auth.bat
call scripts\run-job.bat "MyJob"
```

Script environment variables:

| Variable | Default | Description |
|---|---|---|
| `ORCHESTRATOR_URL` | `http://localhost:8080` | API base URL |
| `ORCHESTRATOR_USER` | `admin` | Username |
| `ORCHESTRATOR_PASS` | `changeme` | Password |
| `ORCHESTRATOR_TOKEN` | *(none)* | Pre-existing JWT (skips login) |

---

## Initial Login

Three seed users are created on first startup. All have `PASSWORD_EXPIRED=Y`, so you **must change the password on first login**:

| Username | Role | Permissions |
|---|---|---|
| `admin` | ADMIN | Full access — jobs, runs, config, credentials, audit |
| `operator` | OPERATOR | View + execute jobs and steps |
| `viewer` | VIEWER | Read-only — view jobs and run history |

**Default password:** `changeme`

---

## Troubleshooting

### Build fails with Node.js errors

The `frontend-maven-plugin` downloads Node.js automatically. If the download fails, check your network proxy settings or run the frontend build manually:

```bash
cd orchestrator-ui
npm install
npm run build
cd ..
mvn clean package -Dskip.npm
```

### Flyway migration fails on startup

Check that the database user has `CREATE TABLE` privileges. To re-run migrations from scratch, drop the `SCHEMA_VERSION` and `VERSIONED_METADATA` tables in Oracle (only do this in a test environment):

```sql
DROP TABLE schema_version;
DROP TABLE versioned_metadata;
```

### "Password expired" keeps showing

After changing your password via the UI, the `PASSWORD_EXPIRED` flag is set to `N`. If it persists, check the `APP_USER` table:

```sql
SELECT username, password_expired FROM app_user;
```

### Logs not appearing

Check the `LOG_FILE` environment variable and ensure the `logs/` directory is writable. In CLI mode, logs go to stdout only.

### Port 8080 already in use

Start the app with a different port:

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar --server.port=8081
```
