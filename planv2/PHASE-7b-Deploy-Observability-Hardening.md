# Phase 7b — Deploy: Observability, Metrics & Log Retention

> **Goal:** Add structured JSON logging with rotation, Spring Boot Actuator health
> indicators, Micrometer metrics for job runs, and a nightly log retention cleanup task.
> After this phase the platform is production-hardened.

> **Depends on:** Phase 7a (deployed JAR, systemd unit, `application-prod.yml`)  
> **Produces:** `logback-spring.xml`, custom health indicators, Micrometer metrics,
> `LogRetentionTask`

---

## 7b.1 Structured JSON Logging with Logback

Add the Logstash Logback encoder dependency:

```xml
<!-- pom.xml -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

### `src/main/resources/logback-spring.xml`

```xml
<configuration>

  <!-- ── PRODUCTION profile: structured JSON to rolling file ── -->
  <springProfile name="prod">

    <appender name="JSON_FILE"
              class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/opt/orchestrator/logs/app.log</file>

      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <!-- Include MDC fields set by the engine for log correlation -->
        <includeMdcKeyName>runId</includeMdcKeyName>
        <includeMdcKeyName>jobId</includeMdcKeyName>
        <includeMdcKeyName>stepId</includeMdcKeyName>
        <includeMdcKeyName>username</includeMdcKeyName>
      </encoder>

      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <!-- Daily rotation, compressed, kept for 30 days, total cap 2 GB -->
        <fileNamePattern>
          /opt/orchestrator/logs/archived/app.%d{yyyy-MM-dd}.log.gz
        </fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>2GB</totalSizeCap>
      </rollingPolicy>
    </appender>

    <root level="INFO">
      <appender-ref ref="JSON_FILE" />
    </root>

    <!-- Suppress noisy frameworks -->
    <logger name="org.hibernate.SQL"            level="WARN" />
    <logger name="org.springframework.security" level="WARN" />
    <logger name="com.zaxxer.hikari"            level="WARN" />
  </springProfile>

  <!-- ── All other profiles: coloured console ── -->
  <springProfile name="!prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%cyan(%thread)] %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG">
      <appender-ref ref="CONSOLE" />
    </root>
    <logger name="org.hibernate.SQL" level="DEBUG" />
  </springProfile>

</configuration>
```

### MDC Injection in the Engine

Add these MDC calls in `JobExecutionOrchestrator.execute()` so every log line from
a running job includes the `runId` and `jobId` in the JSON output:

```java
// At the top of execute()
MDC.put("runId", String.valueOf(ctx.getRunId()));
MDC.put("jobId", String.valueOf(ctx.getJobId()));

try {
    // ... execute steps
} finally {
    MDC.clear();    // always clear MDC at the end of a run
}

// In each step executor, also set stepId:
MDC.put("stepId", String.valueOf(step.getStepId()));
// ... execute step ...
MDC.remove("stepId");
```

---

## 7b.2 Spring Boot Actuator Configuration

```yaml
# Add to application-prod.yml

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized   # full detail only for authenticated admins
      probes:
        enabled: true                 # /actuator/health/liveness and /readiness
  health:
    db:
      enabled: true
  info:
    env:
      enabled: true
```

Add to `SecurityConfig.filterChain()`:

```java
.requestMatchers("/actuator/health", "/actuator/health/liveness",
                 "/actuator/health/readiness").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

---

## 7b.3 Custom Health Indicators

```java
// com.yourco.orchestrator.health.JobEngineHealthIndicator

@Component
@RequiredArgsConstructor
public class JobEngineHealthIndicator implements HealthIndicator {

    private final JobRunRepository runRepo;

    @Override
    public Health health() {
        try {
            long activeRuns = runRepo.countByStatus(RunStatus.RUNNING);
            long pendingRuns = runRepo.countByStatus(RunStatus.PENDING);
            return Health.up()
                .withDetail("activeRuns",  activeRuns)
                .withDetail("pendingRuns", pendingRuns)
                .build();
        } catch (Exception ex) {
            return Health.down()
                .withException(ex)
                .build();
        }
    }
}
```

```java
// com.yourco.orchestrator.health.FilesystemHealthIndicator

@Component
public class FilesystemHealthIndicator implements HealthIndicator {

    @Value("${orchestrator.archive.base-dir}")
    private String archiveDir;

    @Override
    public Health health() {
        Map<String, String> checks = new LinkedHashMap<>();
        boolean allOk = true;

        // Archive directory
        boolean archiveWritable = Files.isWritable(Path.of(archiveDir));
        checks.put("archiveDir", archiveWritable ? "OK" : "NOT_WRITABLE");
        if (!archiveWritable) allOk = false;

        // Log directory
        boolean logWritable = Files.isWritable(Path.of("/opt/orchestrator/logs"));
        checks.put("logDir", logWritable ? "OK" : "NOT_WRITABLE");
        if (!logWritable) allOk = false;

        return allOk
            ? Health.up().withDetails(checks).build()
            : Health.down().withDetails(checks).build();
    }
}
```

Add `countByStatus` to `JobRunRepository`:

```java
long countByStatus(RunStatus status);
```

---

## 7b.4 Micrometer Metrics

Instrument key operations so you can monitor job health in Prometheus / Grafana.

### Counter and Timer Beans

```java
// com.yourco.orchestrator.metrics.JobMetrics

@Component
public class JobMetrics {

    private final Counter runStartedCounter;
    private final Counter runSuccessCounter;
    private final Counter runFailedCounter;
    private final DistributionSummary runDurationSummary;
    private final AtomicLong activeRunGauge;

    public JobMetrics(MeterRegistry registry) {
        this.runStartedCounter = Counter.builder("orchestrator.run.started")
            .description("Total job runs started")
            .register(registry);

        this.runSuccessCounter = Counter.builder("orchestrator.run.completed")
            .tag("result", "success")
            .description("Total successful job runs")
            .register(registry);

        this.runFailedCounter = Counter.builder("orchestrator.run.completed")
            .tag("result", "failed")
            .description("Total failed job runs")
            .register(registry);

        this.runDurationSummary = DistributionSummary.builder("orchestrator.run.duration.seconds")
            .description("Job run duration in seconds")
            .baseUnit("seconds")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);

        this.activeRunGauge = new AtomicLong(0);
        Gauge.builder("orchestrator.run.active", activeRunGauge, AtomicLong::get)
            .description("Currently active (RUNNING) job runs")
            .register(registry);
    }

    public void recordRunStarted()  { runStartedCounter.increment(); activeRunGauge.incrementAndGet(); }
    public void recordRunSuccess(long durationSeconds) {
        runSuccessCounter.increment();
        activeRunGauge.decrementAndGet();
        runDurationSummary.record(durationSeconds);
    }
    public void recordRunFailed(long durationSeconds) {
        runFailedCounter.increment();
        activeRunGauge.decrementAndGet();
        runDurationSummary.record(durationSeconds);
    }
}
```

### Instrument `JobExecutionOrchestrator`

```java
// In JobExecutionOrchestrator.execute()

// At the top of execute():
jobMetrics.recordRunStarted();
LocalDateTime runStart = LocalDateTime.now();

// In the finally block:
long durationSeconds = ChronoUnit.SECONDS.between(runStart, LocalDateTime.now());
if (run.getStatus() == RunStatus.SUCCESS || run.getStatus() == RunStatus.PARTIAL) {
    jobMetrics.recordRunSuccess(durationSeconds);
} else {
    jobMetrics.recordRunFailed(durationSeconds);
}
```

---

## 7b.5 Log Retention Cleanup Task

Nightly task that purges old `JOB_RUN` rows (and their cascaded `JOB_RUN_STEP` rows)
to prevent unbounded DB growth.

```java
// com.yourco.orchestrator.task.LogRetentionTask

@Component
@RequiredArgsConstructor
@Slf4j
public class LogRetentionTask {

    private final JobRunRepository runRepo;

    @Value("${orchestrator.engine.log-retention-days:30}")
    private int retentionDays;

    /**
     * Runs at 03:00 AM every night.
     * Only deletes runs in a terminal state — never deletes RUNNING or PENDING runs.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldRuns() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("Log retention: purging runs older than {} (cutoff: {})", retentionDays, cutoff);

        int deleted = runRepo.deleteCompletedRunsOlderThan(cutoff);
        log.info("Log retention: deleted {} run record(s)", deleted);
    }
}
```

Add to `JobRunRepository`:

```java
@Modifying
@Transactional
@Query("""
    DELETE FROM JobRun r
    WHERE r.createdAt < :cutoff
    AND r.status NOT IN ('RUNNING', 'PENDING')
    """)
int deleteCompletedRunsOlderThan(@Param("cutoff") LocalDateTime cutoff);
```

Enable `@Scheduled` in the main application class:

```java
@SpringBootApplication
@EnableScheduling        // ← ADD THIS
public class OrchestratorApplication { ... }
```

---

## 7b.6 `application-prod.yml` — Final Complete Version

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//${DB_HOST}:1521/${DB_SERVICE}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

server:
  port: 8080
  shutdown: graceful           # wait for in-flight requests before shutdown
  tomcat:
    threads:
      max: 200

spring.lifecycle:
  timeout-per-shutdown-phase: 60s   # matches systemd TimeoutStopSec

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
  health:
    db:
      enabled: true

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

## 7b.7 Key Metrics Available at `/actuator/metrics`

| Metric name | Description |
|-------------|-------------|
| `orchestrator.run.started` | Total runs launched since startup |
| `orchestrator.run.completed{result=success}` | Successful runs |
| `orchestrator.run.completed{result=failed}` | Failed/partial/cancelled runs |
| `orchestrator.run.duration.seconds` | Duration histogram (p50, p90, p99) |
| `orchestrator.run.active` | Currently running jobs (gauge) |
| `hikaricp.connections.active` | Active DB connections |
| `jvm.memory.used` | JVM heap usage |
| `process.cpu.usage` | Process CPU load |

---

## Phase 7b Acceptance Criteria

- [ ] Application starts in `prod` profile and writes JSON to `/opt/orchestrator/logs/app.log`
- [ ] Each log line includes `runId` and `jobId` fields while a job is executing
- [ ] Log rotates at midnight — a `.gz` file appears in `logs/archived/`
- [ ] `GET /actuator/health` returns `{ "status": "UP" }` with DB and filesystem checks
- [ ] `GET /actuator/health` returns `{ "status": "DOWN" }` if archive dir is not writable
- [ ] `GET /actuator/metrics/orchestrator.run.started` shows the correct count after triggering jobs
- [ ] `GET /actuator/metrics/orchestrator.run.active` shows `1` while a job runs and `0` after
- [ ] Log retention task fires at 03:00 and deletes rows older than configured days (verify in test with a short retention like 0 days)
- [ ] Runs in `RUNNING` state are NOT deleted by the retention task
- [ ] `GET /actuator/metrics/orchestrator.run.duration.seconds` shows p50/p90/p99 after several runs

---

**Previous:** [Phase 7a — Packaging & Systemd](./PHASE-7a-Deploy-Packaging-Systemd.md)  
**Back to:** [README — Plan Index](./README.md)
