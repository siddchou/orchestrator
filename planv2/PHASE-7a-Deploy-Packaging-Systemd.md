# Phase 7a — Deploy: Packaging, Directory Layout & Systemd

> **Goal:** Package the application as a single deployable fat JAR, set up the Linux
> directory structure, write the environment file with secrets, create the systemd
> service unit, and document the upgrade procedure.

> **Depends on:** All previous phases complete and the fat JAR builds cleanly  
> **Produces:** Deployment directory layout, `orchestrator.env`, `orchestrator.service`,
> `SpaFallbackController`, upgrade runbook

---

## 7a.1 Full Maven Build

```bash
# From the project root — builds Angular first, then packages everything into one JAR
mvn clean package -DskipTests

# Output:
# orchestrator-api/target/orchestrator-api-1.0.0.jar   (~80–100 MB)
```

The fat JAR contains:
- All Java classes and dependencies
- Angular `dist/` assets in `BOOT-INF/classes/static/`
- `application.yml` (no secrets — those come from the env file)
- Flyway migrations in `BOOT-INF/classes/db/migration/`

To skip Angular build during rapid backend iteration:

```bash
mvn clean package -DskipTests -Dskip.npm
```

---

## 7a.2 Deployment Directory Layout

```
/opt/orchestrator/
├── orchestrator-api.jar         ← symlink or direct copy; replaced on upgrade
├── orchestrator.env             ← secrets; chmod 600; owned by orchestrator user
├── logs/
│   ├── app.log                  ← active log (rotated nightly by Logback)
│   └── archived/                ← compressed old logs
├── archives/                    ← job archive output (configured in application.yml)
└── .ssh/
    └── known_hosts              ← SFTP host fingerprints
```

### Create OS User and Directories

```bash
# Create a locked system account — no login shell, no home directory outside /opt/orchestrator
sudo useradd \
  --system \
  --shell /usr/sbin/nologin \
  --home-dir /opt/orchestrator \
  --create-home \
  orchestrator

# Create sub-directories
sudo mkdir -p /opt/orchestrator/{logs,logs/archived,archives,.ssh}

# Set ownership
sudo chown -R orchestrator:orchestrator /opt/orchestrator

# Harden SSH dir
sudo chmod 700 /opt/orchestrator/.ssh

# Copy JAR
sudo cp target/orchestrator-api-1.0.0.jar /opt/orchestrator/orchestrator-api.jar
sudo chown orchestrator:orchestrator /opt/orchestrator/orchestrator-api.jar
```

---

## 7a.3 Environment File

```bash
# /opt/orchestrator/orchestrator.env
# Permissions: sudo chmod 600 /opt/orchestrator/orchestrator.env
# Owner:       sudo chown orchestrator:orchestrator /opt/orchestrator/orchestrator.env

# Oracle connection
DB_HOST=your-oracle-host.internal
DB_SERVICE=ORCL
DB_USER=orchestrator_app
DB_PASSWORD=CHANGE_ME_DB_PASSWORD

# JWT signing key — minimum 32 ASCII characters
JWT_SECRET=CHANGE_ME_AT_LEAST_32_CHARS_HERE_XX

# AES-256 credential encryption key — EXACTLY 32 ASCII characters
ORCHESTRATOR_ENCRYPTION_KEY=CHANGE_ME_EXACTLY_32_CHARS_HERE_

# JVM tuning
JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

> **Never** commit this file to source control. Add `/opt/orchestrator/orchestrator.env`
> to `.gitignore` and to your secrets management system (Vault, AWS Secrets Manager, etc.).

---

## 7a.4 Spring `application-prod.yml`

Create a production profile that does not contain any secrets:

```yaml
# src/main/resources/application-prod.yml

spring:
  datasource:
    url: jdbc:oracle:thin:@//${DB_HOST}:1521/${DB_SERVICE}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false

server:
  port: 8080

logging:
  config: classpath:logback-spring.xml

orchestrator:
  engine:
    thread-pool-size: 10
    default-step-timeout-minutes: 60
    log-retention-days: 30
  sftp:
    known-hosts-file: /opt/orchestrator/.ssh/known_hosts
  archive:
    base-dir: /opt/orchestrator/archives
  security:
    jwt-expiry-hours: 8
```

---

## 7a.5 Systemd Service Unit

```ini
# /etc/systemd/system/orchestrator.service

[Unit]
Description=Job Orchestration Platform
Documentation=https://your-wiki/orchestrator
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=orchestrator
Group=orchestrator
WorkingDirectory=/opt/orchestrator

# Load secrets from env file (chmod 600)
EnvironmentFile=/opt/orchestrator/orchestrator.env

ExecStart=/usr/bin/java \
    ${JAVA_OPTS} \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -jar /opt/orchestrator/orchestrator-api.jar

# Restart policy — restart on crash but not on deliberate stop
Restart=on-failure
RestartSec=15
StartLimitIntervalSec=120
StartLimitBurst=3

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/opt/orchestrator/logs /opt/orchestrator/archives
PrivateTmp=true
ProtectHome=true

# Resource limits
LimitNOFILE=65536

# Graceful shutdown — allow up to 60s for an active run to finish
TimeoutStopSec=60

StandardOutput=journal
StandardError=journal
SyslogIdentifier=orchestrator

[Install]
WantedBy=multi-user.target
```

### Install and Enable

```bash
sudo cp orchestrator.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable orchestrator
sudo systemctl start orchestrator

# Verify
sudo systemctl status orchestrator
sudo journalctl -u orchestrator -f          # live log tail
```

---

## 7a.6 SPA Fallback — Spring Boot Must Serve `index.html` for Angular Routes

When a user hard-refreshes `/jobs/42`, the browser sends `GET /jobs/42` to Spring Boot,
which has no handler for it and returns 404. Fix:

```java
// com.yourco.orchestrator.config.SpaFallbackController

@Controller
public class SpaFallbackController {

    /**
     * Forward any request that:
     *   - does not start with /api
     *   - does not contain a file extension (is not a static asset)
     * to index.html so Angular's router handles it.
     */
    @GetMapping(value = {
        "/",
        "/{path:[^\\.]*}",
        "/{path:(?!api)[^\\.]*}/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
```

---

## 7a.7 Upgrade Procedure

```bash
# 1. Build new JAR on build machine
mvn clean package -DskipTests

# 2. Copy to server
scp target/orchestrator-api-1.0.0.jar deploy@your-server:/tmp/

# 3. On server: stop service (waits up to 60s for active run to finish)
ssh deploy@your-server
sudo systemctl stop orchestrator

# 4. Backup current JAR
sudo cp /opt/orchestrator/orchestrator-api.jar \
        /opt/orchestrator/orchestrator-api.jar.bak

# 5. Deploy new JAR
sudo cp /tmp/orchestrator-api-1.0.0.jar /opt/orchestrator/orchestrator-api.jar
sudo chown orchestrator:orchestrator /opt/orchestrator/orchestrator-api.jar

# 6. Start service (Flyway runs any new migrations automatically on startup)
sudo systemctl start orchestrator

# 7. Validate
sudo systemctl status orchestrator
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# 8. Rollback if needed
sudo systemctl stop orchestrator
sudo cp /opt/orchestrator/orchestrator-api.jar.bak /opt/orchestrator/orchestrator-api.jar
sudo systemctl start orchestrator
```

---

## 7a.8 Pre-Deployment Checklist

- [ ] Oracle schema user created with grants: `SELECT, INSERT, UPDATE, DELETE` on all app tables
- [ ] `/opt/orchestrator/orchestrator.env` created with real secrets; `chmod 600`
- [ ] `ORCHESTRATOR_ENCRYPTION_KEY` is exactly 32 characters
- [ ] `JWT_SECRET` is at least 32 characters
- [ ] Default admin password changed after first login
- [ ] SFTP `known_hosts` populated for all target hosts (`ssh-keyscan -H host >> known_hosts`)
- [ ] Working directories for all jobs exist and are writable by `orchestrator` OS user
- [ ] Archive directory `/opt/orchestrator/archives` exists and is writable
- [ ] Java 21 JRE installed at `/usr/bin/java` (or update `ExecStart` path)
- [ ] Port 8080 is open in firewall or load balancer is configured

---

## Phase 7a Acceptance Criteria

- [ ] `mvn clean package` produces a single fat JAR containing Angular static assets
- [ ] `java -jar orchestrator-api.jar --spring.profiles.active=prod` starts cleanly on Linux
- [ ] `GET http://localhost:8080/` serves the Angular app
- [ ] `GET http://localhost:8080/jobs/42` serves `index.html` (not 404)
- [ ] `GET http://localhost:8080/api/jobs` returns JSON (not the Angular app)
- [ ] Systemd service starts, and `systemctl status` shows `active (running)`
- [ ] Service survives a server reboot (`systemctl is-enabled orchestrator` returns `enabled`)
- [ ] `systemctl stop orchestrator` during an active run waits up to 60s (verify with a long-running test job)
- [ ] Upgrade procedure: new JAR deployed, service restarted, Flyway migrations applied, health endpoint returns UP

---

**Previous:** [Phase 6b — RBAC, Credentials & Audit](./PHASE-6b-Security-RBAC-Credentials-Audit.md)  
**Next:** [Phase 7b — Observability & Hardening](./PHASE-7b-Deploy-Observability-Hardening.md)
