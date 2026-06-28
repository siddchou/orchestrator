# Phase 1 — Foundation & Core Domain Model

> **Goal:** Set up the Spring Boot project structure, Oracle database schema via Flyway,
> and all JPA entities. No business logic yet — just the data layer and project skeleton.

---

## 1.1 Project Bootstrap

### Maven Multi-Module Structure

```
orchestrator-parent/
├── pom.xml                        ← parent POM
├── orchestrator-api/              ← Spring Boot app (REST + engine)
│   ├── src/main/java/
│   └── pom.xml
└── orchestrator-ui/               ← Angular frontend (built separately)
    ├── src/
    └── package.json
```

> **Note:** Keep the Angular project in a sibling module. The Maven build will invoke
> `npm run build` and copy the `dist/` output into
> `orchestrator-api/src/main/resources/static/` before packaging the JAR.

### Key Dependencies (`orchestrator-api/pom.xml`)

```xml
<!-- Core -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Database -->
<dependency>
  <groupId>com.oracle.database.jdbc</groupId>
  <artifactId>ojdbc11</artifactId>
  <version>21.9.0.0</version>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-oracle</artifactId>
</dependency>

<!-- Utilities -->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <optional>true</optional>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>

<!-- SFTP (Phase 2) -->
<dependency>
  <groupId>org.apache.sshd</groupId>
  <artifactId>sshd-sftp</artifactId>
  <version>2.12.0</version>
</dependency>

<!-- Archive (Phase 2) -->
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-compress</artifactId>
  <version>1.26.1</version>
</dependency>
```

### `application.yml` Skeleton

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//${DB_HOST}:1521/${DB_SERVICE}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway owns the schema — never let Hibernate modify it
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.OracleDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

orchestrator:
  engine:
    thread-pool-size: 10
    default-step-timeout-minutes: 60
    log-retention-days: 30
  sftp:
    known-hosts-file: /opt/orchestrator/.ssh/known_hosts
  archive:
    base-dir: /opt/orchestrator/archives

server:
  port: 8080
```

---

## 1.2 Database Schema

All migrations live in `src/main/resources/db/migration/`.  
Naming convention: `V{version}__{description}.sql`

### Migration: `V1__create_job_definition.sql`

```sql
CREATE TABLE JOB_DEFINITION (
    JOB_ID          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    JOB_NAME        VARCHAR2(200)  NOT NULL,
    DESCRIPTION     VARCHAR2(1000),
    WORKING_DIR     VARCHAR2(500)  NOT NULL,
    ENABLED         CHAR(1)        DEFAULT 'Y' NOT NULL CHECK (ENABLED IN ('Y','N')),
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT UQ_JOB_NAME UNIQUE (JOB_NAME)
);

CREATE TABLE JOB_STEP (
    STEP_ID             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    JOB_ID              NUMBER         NOT NULL,
    STEP_NAME           VARCHAR2(200)  NOT NULL,
    STEP_ORDER          NUMBER         NOT NULL,
    STEP_TYPE           VARCHAR2(50)   NOT NULL
                        CHECK (STEP_TYPE IN ('ENV_SETUP','LOG_CLEANUP','JAVA_EXEC','SFTP','ARCHIVE')),
    STEP_CONFIG         CLOB,          -- JSON payload specific to step type
    CONTINUE_ON_FAILURE CHAR(1)        DEFAULT 'N' NOT NULL CHECK (CONTINUE_ON_FAILURE IN ('Y','N')),
    ENABLED             CHAR(1)        DEFAULT 'Y' NOT NULL CHECK (ENABLED IN ('Y','N')),
    CONSTRAINT FK_STEP_JOB FOREIGN KEY (JOB_ID) REFERENCES JOB_DEFINITION(JOB_ID) ON DELETE CASCADE,
    CONSTRAINT UQ_STEP_ORDER UNIQUE (JOB_ID, STEP_ORDER)
);

CREATE TABLE JOB_ENV_VAR (
    ENV_ID      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    JOB_ID      NUMBER,               -- NULL = global variable
    VAR_NAME    VARCHAR2(200)  NOT NULL,
    VAR_VALUE   VARCHAR2(2000) NOT NULL,
    IS_GLOBAL   CHAR(1)        DEFAULT 'N' NOT NULL CHECK (IS_GLOBAL IN ('Y','N')),
    CONSTRAINT FK_ENV_JOB FOREIGN KEY (JOB_ID) REFERENCES JOB_DEFINITION(JOB_ID) ON DELETE CASCADE
);

CREATE INDEX IDX_ENV_JOB ON JOB_ENV_VAR(JOB_ID);
CREATE INDEX IDX_ENV_GLOBAL ON JOB_ENV_VAR(IS_GLOBAL);
```

### Migration: `V2__create_job_run.sql`

```sql
CREATE TABLE JOB_RUN (
    RUN_ID          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    JOB_ID          NUMBER         NOT NULL,
    TRIGGERED_BY    VARCHAR2(200),
    TRIGGER_TYPE    VARCHAR2(20)   NOT NULL
                    CHECK (TRIGGER_TYPE IN ('MANUAL','SCHEDULED','API')),
    STATUS          VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL
                    CHECK (STATUS IN ('PENDING','RUNNING','SUCCESS','FAILED','PARTIAL','CANCELLED')),
    STARTED_AT      TIMESTAMP,
    ENDED_AT        TIMESTAMP,
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT FK_RUN_JOB FOREIGN KEY (JOB_ID) REFERENCES JOB_DEFINITION(JOB_ID)
);

CREATE TABLE JOB_RUN_STEP (
    RUN_STEP_ID     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    RUN_ID          NUMBER         NOT NULL,
    STEP_ID         NUMBER         NOT NULL,
    STEP_ORDER      NUMBER         NOT NULL,
    STATUS          VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL
                    CHECK (STATUS IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED')),
    LOG_OUTPUT      CLOB,
    EXIT_CODE       NUMBER,
    STARTED_AT      TIMESTAMP,
    ENDED_AT        TIMESTAMP,
    CONSTRAINT FK_RUN_STEP_RUN  FOREIGN KEY (RUN_ID)  REFERENCES JOB_RUN(RUN_ID)  ON DELETE CASCADE,
    CONSTRAINT FK_RUN_STEP_STEP FOREIGN KEY (STEP_ID) REFERENCES JOB_STEP(STEP_ID)
);

CREATE INDEX IDX_RUN_JOB    ON JOB_RUN(JOB_ID);
CREATE INDEX IDX_RUN_STATUS ON JOB_RUN(STATUS);
CREATE INDEX IDX_RUN_STEP_RUN ON JOB_RUN_STEP(RUN_ID);
```

### Migration: `V3__create_schedule_and_credential.sql`

```sql
CREATE TABLE JOB_SCHEDULE (
    SCHEDULE_ID     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    JOB_ID          NUMBER         NOT NULL,
    CRON_EXPRESSION VARCHAR2(100)  NOT NULL,
    ENABLED         CHAR(1)        DEFAULT 'Y' NOT NULL CHECK (ENABLED IN ('Y','N')),
    NEXT_FIRE_TIME  TIMESTAMP,
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT FK_SCHEDULE_JOB FOREIGN KEY (JOB_ID) REFERENCES JOB_DEFINITION(JOB_ID) ON DELETE CASCADE,
    CONSTRAINT UQ_JOB_SCHEDULE UNIQUE (JOB_ID)  -- one schedule per job
);

CREATE TABLE JOB_CREDENTIAL (
    CREDENTIAL_ID   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    CREDENTIAL_REF  VARCHAR2(100)  NOT NULL,   -- name used in step config JSON
    CRED_TYPE       VARCHAR2(20)   NOT NULL CHECK (CRED_TYPE IN ('PASSWORD','SSH_KEY')),
    CRED_VALUE      VARCHAR2(4000) NOT NULL,   -- AES-256 encrypted value
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT UQ_CRED_REF UNIQUE (CREDENTIAL_REF)
);

CREATE TABLE AUDIT_LOG (
    AUDIT_ID    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USERNAME    VARCHAR2(200)  NOT NULL,
    ACTION      VARCHAR2(200)  NOT NULL,
    ENTITY_TYPE VARCHAR2(100),
    ENTITY_ID   NUMBER,
    DETAIL      VARCHAR2(2000),
    CREATED_AT  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX IDX_AUDIT_USER   ON AUDIT_LOG(USERNAME);
CREATE INDEX IDX_AUDIT_ENTITY ON AUDIT_LOG(ENTITY_TYPE, ENTITY_ID);
```

---

## 1.3 Java Enums

```java
// com.yourco.orchestrator.domain.enums

public enum StepType {
    ENV_SETUP, LOG_CLEANUP, JAVA_EXEC, SFTP, ARCHIVE
}

public enum RunStatus {
    PENDING, RUNNING, SUCCESS, FAILED, PARTIAL, CANCELLED
}

public enum TriggerType {
    MANUAL, SCHEDULED, API
}

public enum CredentialType {
    PASSWORD, SSH_KEY
}
```

---

## 1.4 JPA Entities

### `JobDefinition.java`

```java
@Entity @Table(name = "JOB_DEFINITION")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "JOB_ID")
    private Long jobId;

    @Column(name = "JOB_NAME", nullable = false, unique = true)
    private String jobName;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "WORKING_DIR", nullable = false)
    private String workingDir;

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "jobDefinition", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<JobStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "jobDefinition", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<JobEnvVar> envVars = new ArrayList<>();

    @OneToOne(mappedBy = "jobDefinition", cascade = CascadeType.ALL,
              orphanRemoval = true, fetch = FetchType.LAZY)
    private JobSchedule schedule;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

### `JobStep.java`

```java
@Entity @Table(name = "JOB_STEP")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STEP_ID")
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID", nullable = false)
    private JobDefinition jobDefinition;

    @Column(name = "STEP_NAME", nullable = false)
    private String stepName;

    @Column(name = "STEP_ORDER", nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "STEP_TYPE", nullable = false)
    private StepType stepType;

    @Lob
    @Column(name = "STEP_CONFIG")
    private String stepConfig;   // JSON string — deserialize per StepType in engine

    @Column(name = "CONTINUE_ON_FAILURE", nullable = false)
    private String continueOnFailure = "N";

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";
}
```

### `JobRun.java`

```java
@Entity @Table(name = "JOB_RUN")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RUN_ID")
    private Long runId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID", nullable = false)
    private JobDefinition jobDefinition;

    @Column(name = "TRIGGERED_BY")
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRIGGER_TYPE", nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "jobRun", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<JobRunStep> runSteps = new ArrayList<>();
}
```

### `JobRunStep.java`

```java
@Entity @Table(name = "JOB_RUN_STEP")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobRunStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RUN_STEP_ID")
    private Long runStepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RUN_ID", nullable = false)
    private JobRun jobRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private JobStep jobStep;

    @Column(name = "STEP_ORDER")
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private RunStatus status = RunStatus.PENDING;

    @Lob
    @Column(name = "LOG_OUTPUT")
    private String logOutput;

    @Column(name = "EXIT_CODE")
    private Integer exitCode;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;
}
```

---

## 1.5 Spring Data Repositories

```java
// One interface each — standard Spring Data JPA

public interface JobDefinitionRepository extends JpaRepository<JobDefinition, Long> {
    Optional<JobDefinition> findByJobName(String jobName);
    List<JobDefinition> findByEnabledOrderByJobNameAsc(String enabled);
}

public interface JobStepRepository extends JpaRepository<JobStep, Long> {
    List<JobStep> findByJobDefinition_JobIdOrderByStepOrderAsc(Long jobId);
}

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    Page<JobRun> findByJobDefinition_JobId(Long jobId, Pageable pageable);
    Page<JobRun> findByStatus(RunStatus status, Pageable pageable);
    boolean existsByJobDefinition_JobIdAndStatus(Long jobId, RunStatus status);
}

public interface JobRunStepRepository extends JpaRepository<JobRunStep, Long> {
    List<JobRunStep> findByJobRun_RunIdOrderByStepOrderAsc(Long runId);
}

public interface JobEnvVarRepository extends JpaRepository<JobEnvVar, Long> {
    List<JobEnvVar> findByIsGlobal(String isGlobal);
    List<JobEnvVar> findByJobDefinition_JobId(Long jobId);
}

public interface JobScheduleRepository extends JpaRepository<JobSchedule, Long> {
    Optional<JobSchedule> findByJobDefinition_JobId(Long jobId);
    List<JobSchedule> findByEnabled(String enabled);
}

public interface JobCredentialRepository extends JpaRepository<JobCredential, Long> {
    Optional<JobCredential> findByCredentialRef(String credentialRef);
}
```

---

## 1.6 Step Config JSON Contracts

Each step type deserializes `STEP_CONFIG` (CLOB) into a dedicated Java record.  
Define these records now; the engine (Phase 2) will use them.

```java
// ENV_SETUP
public record EnvSetupConfig(
    String javaHome,
    List<String> classpathEntries,
    Map<String, String> extraEnvVars
) {}

// LOG_CLEANUP
public record LogCleanupConfig(
    String directory,       // absolute path or relative to job working dir
    String filePattern      // glob, e.g. "*.log" or "job_*.log"
) {}

// JAVA_EXEC
public record JavaExecConfig(
    String mainClass,
    String jarPath,         // optional — if running a fat JAR directly
    List<String> args,
    List<String> jvmArgs,
    Integer timeoutMinutes  // null = use global default
) {}

// SFTP
public record SftpConfig(
    String host,
    int port,
    String username,
    String credentialRef,   // references JOB_CREDENTIAL.CREDENTIAL_REF
    String remoteDir,
    String filePattern,     // glob for local files to send
    String direction        // "UPLOAD" or "DOWNLOAD"
) {}

// ARCHIVE
public record ArchiveConfig(
    String sourceDir,
    List<String> filePatterns,
    String archiveDir,
    String archiveFormat    // "ZIP" or "TAR_GZ"
) {}
```

---

## Phase 1 Acceptance Criteria

- [ ] Spring Boot app starts cleanly and connects to Oracle 19c
- [ ] Flyway runs all 3 migrations on first startup with no errors
- [ ] All JPA entities map correctly — verify with a simple `findAll()` test
- [ ] `ddl-auto: validate` passes (Hibernate schema matches Flyway-created tables)
- [ ] Step config JSON contracts deserialize correctly for all 5 step types
- [ ] Unit tests cover all repository methods with an H2 or embedded test schema

---

**Next:** [Phase 2 — Job Execution Engine](./PHASE-2-Engine.md)
