# Novakai Orchestrator — Setup & Usage Guide

## Overview

A job orchestration platform built with Spring Boot 4.1 (Java 21) and Angular 22. It replaces Windows batch job scripts with a configurable, UI-driven application that runs on Linux. Users define jobs with ordered steps, trigger runs manually or via cron schedules, and monitor live log output through a browser dashboard.

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 21 or later |
| Maven | 3.8+ |
| Oracle Database | 19c (production) |
| Node.js | 22.22.3 (auto-installed by Maven plugin) |

---

## Quick Start

### 1. First-Time Setup

Set the required environment variables before starting the application:

```bash
export DB_HOST="your-oracle-host"
export DB_SERVICE="your_service_name"
export DB_USER="your_db_user"
export DB_PASSWORD="your_db_password"
export JWT_SECRET="your-secret-key-at-least-32-characters-long"
export ORCH_HOME="/opt/orchestrator"   # optional, defaults to /opt/orchestrator
```

### 2. Build

```bash
mvn clean package
```

This builds the backend and the Angular frontend into a single fat JAR. The `frontend-maven-plugin` installs Node.js and runs `npm install` + `npm run build` inside `orchestrator-ui/`.

### 3. Run

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

The server starts on port **8080**. Flyway will automatically apply any pending database migrations on first startup.

### 4. Access the UI

Open `http://localhost:8080` in a browser and log in with your credentials.

---

## Development Mode

Run backend and frontend separately for faster iteration:

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

The Angular dev server serves on port 4200 and proxies API calls to the backend on 8080.

### Running Tests

```bash
# Run all tests (uses H2 in-memory DB)
mvn test

# Run with test profile explicitly
mvn -Ptest test
```

The `test` profile uses an H2 in-memory database with three seed users:

| Username | Password | Role |
|---|---|---|
| admin | changeme | ADMIN |
| operator | changeme | OPERATOR |
| viewer | changeme | VIEWER |

---

## Configuration

All configuration lives in `src/main/resources/application.yml` and is driven by environment variables.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_HOST` | Yes | — | Oracle DB hostname |
| `DB_SERVICE` | Yes | — | Oracle service name |
| `DB_USER` | Yes | — | Database username |
| `DB_PASSWORD` | Yes | — | Database password |
| `JWT_SECRET` | Yes | built-in dev default | JWT signing key (minimum 32 characters) |
| `ORCH_HOME` | No | `/opt/orchestrator` | Base directory for archives and SSH known_hosts |

### Engine Settings (override via `application.yml`)

```yaml
orchestrator:
  engine:
    thread-pool-size: 10           # concurrent job runs
    default-step-timeout-minutes: 60
    log-retention-days: 30
  sftp:
    known-hosts-file: ${ORCH_HOME}/.ssh/known_hosts
  archive:
    base-dir: ${ORCH_HOME}/archives
  security:
    jwt-expiry-hours: 8
```

---

## Job Step Types

Each job consists of ordered steps executed sequentially:

| Step Type | Description |
|---|---|
| `ENV_SETUP` | Sets environment variables for subsequent steps |
| `JAVA_EXEC` | Executes a Java class with configurable classpath and arguments |
| `SFTP` | Transfers files to/from a remote SFTP server |
| `LOG_CLEANUP` | Cleans up old log files |
| `ARCHIVE` | Archives job output files |

---

## REST API Reference

All API responses are wrapped in `{ success: boolean, data: ..., error: string }`.

### Authentication (`/api/auth`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Returns JWT token, role, password-expired flag |
| `POST` | `/api/auth/change-password` | Authenticated | Change current user password |
| `GET` | `/api/auth/me` | Authenticated | Get current user info and refresh token |

### Jobs (`/api/jobs`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/jobs` | VIEWER+ | List all job definitions |
| `GET` | `/api/jobs/{id}` | VIEWER+ | Get job definition with steps |
| `POST` | `/api/jobs` | ADMIN | Create new job definition |
| `PUT` | `/api/jobs/{id}` | ADMIN | Update job definition |
| `DELETE` | `/api/jobs/{id}` | ADMIN | Delete job definition |
| `POST` | `/api/jobs/{id}/steps` | ADMIN | Add step to job |
| `PUT` | `/api/jobs/{id}/steps/{stepId}` | ADMIN | Update step |
| `DELETE` | `/api/jobs/{id}/steps/{stepId}` | ADMIN | Remove step |
| `POST` | `/api/jobs/{id}/env-vars` | ADMIN | Add environment variable |
| `POST` | `/api/jobs/{id}/schedules` | ADMIN | Add cron schedule |

### Job Runs (`/api`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/jobs/{id}/run` | OPERATOR+ | Trigger manual run |
| `POST` | `/api/jobs/name/{name}/run` | OPERATOR+ | Trigger run by job name |
| `GET` | `/api/runs` | VIEWER+ | List runs (paginated, filterable) |
| `GET` | `/api/runs/{runId}` | VIEWER+ | Get run detail with steps |
| `POST` | `/api/runs/{runId}/cancel` | OPERATOR+ | Cancel running job |
| `GET` | `/api/runs/{runId}/steps/{stepId}/log` | VIEWER+ | Get step log output |

### Log Streaming

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/runs/{runId}/log-stream` | VIEWER+ | SSE stream of live logs |

### System (`/api/system`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/system/health` | Public | Health check |
| `GET` | `/api/system/env-validate` | ADMIN | Validate environment config |
| `GET` | `/api/system/cron-validate` | ADMIN | Validate cron expression |
| `GET` | `/api/system/env-vars` | ADMIN | List global environment variables |

### Credentials (`/api/credentials`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| CRUD | `/api/credentials/**` | ADMIN | Manage SFTP/database credentials |

### Audit (`/api/audit`)

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/audit` | ADMIN | Query audit log entries |

---

## Role-Based Access Control

| Role | Permissions |
|---|---|
| **VIEWER** | Read jobs, runs, logs, and live stream |
| **OPERATOR** | Viewer + trigger/cancel runs |
| **ADMIN** | Operator + full CRUD on jobs, schedules, env vars, credentials, audit |

Authentication is stateless JWT. Tokens expire after 8 hours (configurable via `jwt-expiry-hours`).

---

## Database Schema

Flyway manages the Oracle database schema via 4 migration scripts:

| Migration | Description |
|---|---|
| `V1__create_job_definition.sql` | Job definitions, steps, env vars |
| `V2__create_job_run.sql` | Job runs, run steps, run status tracking |
| `V3__create_schedule_and_credential.sql` | Cron schedules, credential store |
| `V4__create_app_user.sql` | User accounts, roles, audit log |

Migrations run automatically on startup. Flyway is disabled in the test profile (H2 uses `create-drop`).

---

## Deployment

### Build the Fat JAR

```bash
mvn clean package -DskipTests
# Output: target/orchestrator-0.0.1-SNAPSHOT.jar (~80–100 MB)
```

The fat JAR contains all Java classes, dependencies, Angular static assets, and Flyway migrations. To skip the Angular build during rapid backend iteration:

```bash
mvn clean package -DskipTests -Dskip.npm
```

### Deployment Directory Layout

```
/opt/orchestrator/
├── orchestrator.jar              # fat JAR
├── orchestrator.env              # secrets file (chmod 600)
├── logs/
│   ├── app.log                   # active log (rotated nightly)
│   └── archived/                 # compressed old logs
├── archives/                     # job archive output
└── .ssh/
    └── known_hosts               # SFTP host fingerprints
```

### Create OS User and Directories

```bash
sudo useradd --system --shell /usr/sbin/nologin --home-dir /opt/orchestrator --create-home orchestrator
sudo mkdir -p /opt/orchestrator/{logs,logs/archived,archives,.ssh}
sudo chown -R orchestrator:orchestrator /opt/orchestrator
sudo chmod 700 /opt/orchestrator/.ssh
```

### Environment File

Create `/opt/orchestrator/orchestrator.env` with the required secrets:

```bash
DB_HOST=your-oracle-host.internal
DB_SERVICE=ORCL
DB_USER=orchestrator_app
DB_PASSWORD=CHANGE_ME
JWT_SECRET=CHANGE_ME_AT_LEAST_32_CHARS_HERE_XX
ORCHESTRATOR_ENCRYPTION_KEY=CHANGE_ME_EXACTLY_32_CHARS_HERE_
JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

```bash
sudo chmod 600 /opt/orchestrator/orchestrator.env
sudo chown orchestrator:orchestrator /opt/orchestrator/orchestrator.env
```

### Systemd Service

Install `/etc/systemd/system/orchestrator.service`:

```ini
[Unit]
Description=Job Orchestration Platform
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=orchestrator
Group=orchestrator
WorkingDirectory=/opt/orchestrator
EnvironmentFile=/opt/orchestrator/orchestrator.env
ExecStart=/usr/bin/java ${JAVA_OPTS} \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -jar /opt/orchestrator/orchestrator.jar
Restart=on-failure
RestartSec=15
TimeoutStopSec=60
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/opt/orchestrator/logs /opt/orchestrator/archives
PrivateTmp=true
ProtectHome=true
LimitNOFILE=65536
StandardOutput=journal
StandardError=journal
SyslogIdentifier=orchestrator

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now orchestrator
sudo systemctl status orchestrator
sudo journalctl -u orchestrator -f   # live log tail
```

### Upgrade Procedure

```bash
# 1. Copy new JAR to server
scp target/orchestrator-0.0.1-SNAPSHOT.jar deploy@server:/tmp/

# 2. Stop service (waits up to 60s for active run to finish)
sudo systemctl stop orchestrator

# 3. Backup and deploy
sudo cp /opt/orchestrator/orchestrator.jar /opt/orchestrator/orchestrator.jar.bak
sudo cp /tmp/orchestrator-0.0.1-SNAPSHOT.jar /opt/orchestrator/orchestrator.jar
sudo chown orchestrator:orchestrator /opt/orchestrator/orchestrator.jar

# 4. Start — Flyway applies any new migrations automatically
sudo systemctl start orchestrator

# 5. Validate
curl -s http://localhost:8080/actuator/health
```

### Pre-Deployment Checklist

- [ ] Oracle schema user has `SELECT, INSERT, UPDATE, DELETE` grants on all app tables
- [ ] `orchestrator.env` created with real secrets and `chmod 600`
- [ ] `JWT_SECRET` is at least 32 characters
- [ ] Default admin password changed after first login
- [ ] SFTP `known_hosts` populated (`ssh-keyscan -H host >> known_hosts`)
- [ ] Working directories for all jobs exist and are writable by the `orchestrator` OS user
- [ ] Java 21 JRE installed at `/usr/bin/java`
- [ ] Port 8080 is accessible
