# Phase 7 — Deployment, Observability & Hardening

> **Goal:** Package the application as a production-ready artifact, deploy it as a
> `systemd` service on Linux, and configure observability so failures are visible.

---

## 7.1 Build Pipeline

### Full Build (Backend + Frontend)

```bash
# From project root
mvn clean package -DskipTests

# What this does:
# 1. frontend-maven-plugin runs: npm install && ng build --configuration production
# 2. Angular dist/ copies into orchestrator-api/src/main/resources/static/
# 3. Spring Boot packages everything into a single fat JAR
```

### Output

```
orchestrator-api/target/orchestrator-api-1.0.0.jar   (~80 MB with Angular assets)
```

### Angular Deep Link Fix

Spring Boot must serve `index.html` for all non-API, non-static routes so Angular's
router can handle them on hard refresh:

```java
@Controller
public class SpaFallbackController {

    @GetMapping(value = { "/", "/{path:[^\\.]*}", "/{path:.*}/**" })
    public String forward() {
        return "forward:/index.html";
    }
}
```

---

## 7.2 Deployment Directory Layout

```
/opt/orchestrator/
├── orchestrator-api.jar         ← fat JAR (replaced on each deploy)
├── orchestrator.env             ← secrets and env config (chmod 600)
├── logs/
│   ├── app.log                  ← Spring Boot app log (JSON structured)
│   └── archived/
├── archives/                    ← job archive output (configured in application.yml)
└── .ssh/
    └── known_hosts              ← SFTP host keys
```

```bash
# Create OS user and directories
sudo useradd -r -s /bin/false -d /opt/orchestrator orchestrator
sudo mkdir -p /opt/orchestrator/{logs,logs/archived,archives,.ssh}
sudo chown -R orchestrator:orchestrator /opt/orchestrator
sudo chmod 700 /opt/orchestrator/.ssh
```

---

## 7.3 Environment File

```bash
# /opt/orchestrator/orchestrator.env
# Permissions: chmod 600, owned by orchestrator user

DB_HOST=your-oracle-host
DB_SERVICE=ORCL
DB_USER=orchestrator_app
DB_PASSWORD=CHANGE_ME

JWT_SECRET=CHANGE_ME_32_CHARS_MINIMUM_HERE_XXXX
ORCHESTRATOR_ENCRYPTION_KEY=CHANGE_ME_EXACTLY_32_CHARS_HERE_

JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC
```

---

## 7.4 Systemd Service Unit

```ini
# /etc/systemd/system/orchestrator.service

[Unit]
Description=Job Orchestration Platform
Documentation=https://your-wiki/orchestrator
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=orchestrator
Group=orchestrator
WorkingDirectory=/opt/orchestrator

EnvironmentFile=/opt/orchestrator/orchestrator.env
ExecStart=/usr/bin/java \
  ${JAVA_OPTS} \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /opt/orchestrator/orchestrator-api.jar \
  --spring.profiles.active=prod

# Restart policy
Restart=on-failure
RestartSec=10
StartLimitIntervalSec=60
StartLimitBurst=3

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/opt/orchestrator/logs /opt/orchestrator/archives
PrivateTmp=true

# Resource limits
LimitNOFILE=65536

# Graceful shutdown — allow 60s for running jobs to finish
TimeoutStopSec=60

StandardOutput=journal
StandardError=journal
SyslogIdentifier=orchestrator

[Install]
WantedBy=multi-user.target
```

### Systemd Commands

```bash
# Install and enable
sudo systemctl daemon-reload
sudo systemctl enable orchestrator
sudo systemctl start orchestrator

# Operations
sudo systemctl status orchestrator
sudo journalctl -u orchestrator -f          # live logs
sudo journalctl -u orchestrator --since "1 hour ago"

# Deploy new version
sudo systemctl stop orchestrator
sudo cp orchestrator-api-1.0.0.jar /opt/orchestrator/orchestrator-api.jar
sudo systemctl start orchestrator
```

---

## 7.5 Structured Logging

Configure Logback for JSON output so logs can be ingested by log aggregators.

### `logback-spring.xml`

```xml
<configuration>

  <springProfile name="prod">
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/opt/orchestrator/logs/app.log</file>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>runId</includeMdcKeyName>
        <includeMdcKeyName>jobId</includeMdcKeyName>
        <includeMdcKeyName>username</includeMdcKeyName>
      </encoder>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/opt/orchestrator/logs/archived/app.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>2GB</totalSizeCap>
      </rollingPolicy>
    </appender>

    <root level="INFO">
      <appender-ref ref="JSON_FILE"/>
    </root>
  </springProfile>

  <springProfile name="!prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

</configuration>
```

Add MDC in the engine for log correlation:

```java
// In JobExecutionOrchestrator.execute()
MDC.put("runId", String.valueOf(ctx.getRunId()));
MDC.put("jobId", String.valueOf(ctx.getJobId()));
try {
    // ... execute steps
} finally {
    MDC.clear();
}
```

---

## 7.6 Spring Boot Actuator

```yaml
# application-prod.yml

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
  health:
    db:
      enabled: true
```

Secure the actuator endpoints (add to `SecurityConfig`):

```java
.requestMatchers("/actuator/health").permitAll()         // load balancer checks
.requestMatchers("/actuator/**").hasRole("ADMIN")        // everything else admin-only
```

### Custom Health Indicators

```java
@Component
public class JobEngineHealthIndicator implements HealthIndicator {

    private final JobRunRepository runRepo;
    private final JobLaunchService launchService;

    @Override
    public Health health() {
        long activeRuns = runRepo.countByStatus(RunStatus.RUNNING);
        // Could add check for thread pool saturation here
        return Health.up()
            .withDetail("activeRuns", activeRuns)
            .build();
    }
}

@Component
public class WorkingDirHealthIndicator implements HealthIndicator {

    @Value("${orchestrator.archive.base-dir}")
    private String archiveDir;

    @Override
    public Health health() {
        boolean writable = Files.isWritable(Path.of(archiveDir));
        return writable
            ? Health.up().withDetail("archiveDir", archiveDir).build()
            : Health.down().withDetail("archiveDir", archiveDir + " is not writable").build();
    }
}
```

---

## 7.7 Micrometer Metrics

Instrument key operations for monitoring:

```java
@Service
@RequiredArgsConstructor
public class MetricInstrumentedLaunchService extends JobLaunchService {

    private final MeterRegistry meterRegistry;

    @Override
    public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) {
        meterRegistry.counter("job.run.started",
            "triggerType", triggerType.name()).increment();
        return super.launch(jobId, triggerType, triggeredBy);
    }
}

// In JobExecutionOrchestrator.execute() finally block:
Timer.Sample sample = Timer.start(meterRegistry);
// ... execute
sample.stop(Timer.builder("job.run.duration")
    .tag("status", run.getStatus().name())
    .register(meterRegistry));
```

Key metrics to expose:

| Metric | Description |
|--------|-------------|
| `job.run.started` | Count of jobs launched, by trigger type |
| `job.run.duration` | Run duration histogram, by status |
| `job.step.duration` | Step duration histogram, by step type |
| `job.run.active` | Gauge: currently running jobs |

---

## 7.8 Log Retention Cleanup Job

Schedule a nightly task to delete old `JOB_RUN` records and their step logs:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LogRetentionTask {

    private final JobRunRepository runRepo;

    @Value("${orchestrator.engine.log-retention-days:30}")
    private int retentionDays;

    // Runs at 3:00 AM every night
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldRuns() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = runRepo.deleteByCreatedAtBefore(cutoff);
        log.info("Log retention: purged {} run records older than {} days", deleted, retentionDays);
    }
}
```

Add the repository method:

```java
@Modifying
@Transactional
@Query("DELETE FROM JobRun r WHERE r.createdAt < :cutoff AND r.status NOT IN ('RUNNING','PENDING')")
int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
```

---

## 7.9 Deployment Checklist

### Pre-deployment

- [ ] Oracle schema user created with correct grants (SELECT, INSERT, UPDATE, DELETE on all app tables)
- [ ] Flyway migration runs cleanly on target DB
- [ ] `/opt/orchestrator/orchestrator.env` created with real secrets, `chmod 600`
- [ ] `ORCHESTRATOR_ENCRYPTION_KEY` is exactly 32 characters
- [ ] `JWT_SECRET` is at least 32 characters
- [ ] Default admin password changed after first login
- [ ] SFTP `known_hosts` file populated for all target SFTP hosts
- [ ] Working directories for all jobs exist and are writable by `orchestrator` user
- [ ] Job archive directory exists and is writable

### Post-deployment validation

- [ ] `systemctl status orchestrator` shows `active (running)`
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] Login via UI, verify JWT returned
- [ ] Create a test job with a single `JAVA_EXEC` step (`java -version`)
- [ ] Trigger the test job manually, verify `SUCCESS`
- [ ] View live log in UI during run — confirm SSE streaming works
- [ ] Create a schedule (`*/1 * * * * *`), wait 65 seconds, confirm automatic run
- [ ] Verify log rotation at `/opt/orchestrator/logs/archived/`
- [ ] Verify graceful shutdown: `systemctl stop orchestrator` waits for active run to complete

---

## 7.10 Upgrade Procedure

```bash
# Zero-downtime is not achievable with a single instance — plan for a short maintenance window

# 1. Notify users (optionally block job triggers in UI via maintenance mode flag)
# 2. Stop service (waits up to 60s for active run to complete)
sudo systemctl stop orchestrator

# 3. Backup current JAR
cp /opt/orchestrator/orchestrator-api.jar \
   /opt/orchestrator/orchestrator-api.jar.bak

# 4. Deploy new JAR
cp target/orchestrator-api-NEW.jar /opt/orchestrator/orchestrator-api.jar

# 5. Start service (Flyway runs any new migrations automatically)
sudo systemctl start orchestrator

# 6. Validate
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# 7. Rollback if needed
sudo systemctl stop orchestrator
cp /opt/orchestrator/orchestrator-api.jar.bak /opt/orchestrator/orchestrator-api.jar
sudo systemctl start orchestrator
```

---

## Phase 7 Acceptance Criteria

- [ ] `mvn clean package` produces a single self-contained JAR
- [ ] Systemd service starts, runs, and survives a server reboot
- [ ] App log rotates daily and compresses old files
- [ ] `GET /actuator/health` shows all indicators GREEN including DB and archive dir
- [ ] Custom metrics appear at `/actuator/metrics/job.run.started`
- [ ] Nightly retention task runs and purges records older than configured days
- [ ] `systemctl stop` during an active run waits for the run to finish (up to 60s)
- [ ] Full upgrade procedure tested in staging without data loss
- [ ] All sensitive config is in the env file — zero secrets in `application.yml` or JAR

---

**Previous:** [Phase 6 — Security](./PHASE-6-Security.md)  
**Back to:** [README — Plan Index](./README.md)
