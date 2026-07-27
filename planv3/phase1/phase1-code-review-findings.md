# Phase 1 — Code Review Findings

> Ground-truth inspection of the actual codebase before writing any plan. Every claim cites a file path and, where relevant, a class/method name.

---

## 1. Current Step-Execution Engine

| Aspect | Finding | File |
|--------|---------|------|
| **StepExecutor interface** | Already exists with two methods: `StepType getSupportedType()` and `StepResult execute(ExecutionContext ctx, JobStep step) throws Exception` | `../../src/main/java/com/novakai/orchestrator/engine/StepExecutor.java` |
| **Factory / Registry** | `StepExecutorFactory` (Spring `@Component`) auto-collects `List<StepExecutor>` via constructor injection, builds `Map<StepType, StepExecutor>`. Exposes `resolve(StepType)` which throws `IllegalArgumentException` if type not found. | `../../src/main/java/com/novakai/orchestrator/engine/StepExecutorFactory.java` |
| **Orchestrator** | `JobExecutionOrchestrator.execute()` iterates steps sequentially (for-loop over `job.getSteps()` filtered by enabled flag). Calls `executorFactory.resolve(step.getStepType())` then `executor.execute(ctx, step)`. Supports `continueOnFailure=N` abort. | `src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java:48-63` |
| **Single-step execution** | `executeSingleStep()` method exists for test/re-run of individual steps. | `src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java:79-101` |

### Key contradiction vs. input plan assumptions

The input plan assumed a **switch/if-else dispatch** that needs to be replaced by SPI. **Reality**: The codebase already has an SPI-like pattern — `StepExecutor` interface + `StepExecutorFactory` with Spring auto-collection. The bottleneck is not the dispatch mechanism; it's that:
1. `StepType` is a **closed enum** (hardcoded 5 values) — adding a new step type requires modifying the enum and the DB CHECK constraint.
2. Config parsing is **executor-internal** using typed records (`JavaExecConfig`, `SftpConfig`, etc.) parsed by a shared `JsonParser`. There is no `getConfigSchema()` for UI form generation.
3. `StepResult` is a minimal record `(boolean success, int exitCode, String logOutput)` — no structured outputs map, no execution time tracking.

---

## 2. How Each of the 5 Current Step Types Is Dispatched

All 5 executors implement `StepExecutor` and are Spring `@Component`:

| Step Type | Class | Config Record | Notable Dependencies Injected |
|-----------|-------|---------------|-------------------------------|
| `ENV_SETUP` | `EnvSetupStepExecutor` | `EnvSetupConfig(javaHome, classpathEntries, extraEnvVars)` | `JsonParser` |
| `LOG_CLEANUP` | `LogCleanupStepExecutor` | `LogCleanupConfig(directory, filePattern, extraPatterns)` | `JsonParser` |
| `JAVA_EXEC` | `JavaExecStepExecutor` | `JavaExecConfig(mainClass, jarPath, args, jvmArgs, timeoutMinutes)` | `JsonParser`, `@Value(default-step-timeout-minutes)` |
| `SFTP` | `SftpStepExecutor` | `SftpConfig(host, port, username, credentialRef, remoteDir, filePattern, direction, ...)` | `JobCredentialRepository`, `CredentialDecryptionService`, `JsonParser`, `@Value(known-hosts-file)` |
| `ARCHIVE` | `ArchiveStepExecutor` | `ArchiveConfig(sourceDir, filePatterns, archiveDir, archiveFormat, deleteOriginal)` | `JsonParser` |

Dispatch chain: `JobExecutionOrchestrator.executeStep()` → `executorFactory.resolve(step.getStepType())` → `executor.execute(ctx, step)`.

---

## 3. Step Configuration Storage

**Confirmed**: Config is already stored as a **generic JSON CLOB blob** — NOT per-type columns.

- Column: `JOB_STEP.STEP_CONFIG CLOB` (mapped as `@Lob @Column(name = "STEP_CONFIG") private String stepConfig`)
- Source: V1 migration (`src/main/resources/db/migration/V1__create_job_definition.sql:19`)
- Entity: `JobStep.java:38`

Each executor parses this JSON blob into its own typed record via `JsonParser.parse(step.getStepConfig(), ConfigClass.class)`. The 5 config records live in `domain/config/`:
- `JavaExecConfig.java` — record with fields: mainClass, jarPath, args, jvmArgs, timeoutMinutes
- `SftpConfig.java` — record with fields: host, port, username, credentialRef, remoteDir, filePattern, direction, remoteFileName, connectionTimeoutSeconds, authTimeoutSeconds
- `LogCleanupConfig.java` — record with fields: directory, filePattern, extraPatterns
- `ArchiveConfig.java` — record with fields: sourceDir, filePatterns, archiveDir, archiveFormat, deleteOriginal
- `EnvSetupConfig.java` — record with fields: javaHome, classpathEntries, extraEnvVars

**No migration needed for config storage format.** The plan's assumption about "per-type columns → JSON blob" is incorrect; the blob already exists.

---

## 4. SFTP Credential Handling (AES-256-GCM)

| Aspect | Finding | File |
|--------|---------|------|
| **Encryption algorithm** | `AES/GCM/NoPadding` with 12-byte IV, 128-bit auth tag | `CredentialDecryptionService.java:38` |
| **Key source** | Environment variable `ORCHESTRATOR_ENCRYPTION_KEY`, padded to 32 bytes if shorter | `CredentialDecryptionService.java:22-34` |
| **Storage** | `JOB_CREDENTIAL(CREDENTIAL_ID, CREDENTIAL_REF UNIQUE, CRED_TYPE CHECK PASSWORD\|SSH_KEY, CRED_VALUE VARCHAR2(4000))` | V3 migration |
| **Lookup pattern** | SFTP executor calls `credentialRepo.findByCredentialRef(config.credentialRef())` then `decryptionService.decrypt(cred.getCredValue())` | `SftpStepExecutor.java:86-90` |
| **Credential ref in config** | The `credentialRef` string is stored inside the step's JSON config (not as a FK). Only SFTP currently uses credentials. | `SftpConfig.java:9` |

Secrets are referenced by name (`credentialRef`) in step config, never inlined — this matches the plan's assumption.

---

## 5. SSE Log-Streaming Mechanism

| Component | Role | File |
|-----------|------|------|
| `ExecutionContext.liveLogQueue` | `BlockingQueue<String>` — per-run log buffer | `ExecutionContext.java:22` |
| `JobLaunchService.liveLogQueues` | `ConcurrentHashMap<Long, BlockingQueue<String>>` keyed by runId. Created at launch time (`new LinkedBlockingQueue<>()`) and stored in map. | `JobLaunchService.java:52-53,118-119` |
| Executors push logs | Each executor writes to `ctx.getLiveLogQueue().add(line)` during execution (with null-check) | e.g., `JavaExecStepExecutor.java:109-111,136-138` |
| `LogStreamController.streamLog(runId)` | Gets queue from `launchService.getLiveLogQueue(runId)`, spawns virtual thread that polls queue and sends via `SseEmitter`. Checks run status every 1s when no log arrives. | `LogStreamController.java:30-86` |
| Cleanup | `JobLaunchService.cleanupRun(runId)` removes from all three maps (activeFutures, activeContexts, liveLogQueues) after execution completes | `JobLaunchService.java:198-201` |

**Design implication for new StepContext**: The new `StepContext` must carry a reference to the `BlockingQueue<String> liveLogQueue` (or a `LogSink` abstraction wrapping it) so executors can push logs. The SSE controller reads from this queue — changing the contract breaks streaming.

---

## 6. Retry/Timeout Logic

| Aspect | Finding |
|--------|---------|
| **Retry** | **No retry logic exists anywhere.** No `@Retryable`, no retry loop, no backoff. A failed step fails permanently (or continues if `continueOnFailure=Y`). |
| **Timeout** | Per-executor, config-driven: `JavaExecStepExecutor` reads `timeoutMinutes` from `JavaExecConfig` with a default of 60 min (`@Value("${orchestrator.engine.default-step-timeout-minutes:60}")`). Uses `process.waitFor(timeout, TimeUnit.MINUTES)` and kills on timeout. SFTP has `connectionTimeoutSeconds` and `authTimeoutSeconds`. Other executors have no explicit timeout. |
| **Cancellation** | `ExecutionContext.cancelRequested` (volatile boolean) checked in loops. `JobLaunchService.cancel(runId)` interrupts the Future + sets flag. Orchestrator checks `ctx.isCancelRequested() || Thread.currentThread().isInterrupted()` before each step. |

---

## 7. Build & Dependency Versions

| Item | Confirmed Value | File |
|------|-----------------|------|
| **Spring Boot** | 4.1.0 (parent POM) | `pom.xml:8` |
| **Java version** | 21 | `pom.xml:30` |
| **Package root** | `com.novakai.orchestrator` | `pom.xml:12` |
| **Database driver** | Oracle JDBC ojdbc11 21.9.0.0 (runtime) + H2 for test | `pom.xml:57-76` |
| **Flyway** | flyway-core + flyway-database-oracle (Spring Boot managed version) | `pom.xml:63-69` |
| **SFTP library** | Apache SSHD 2.18.0 (sshd-core + sshd-sftp) | `pom.xml:97-106` |
| **Archive library** | Commons Compress 1.26.1 | `pom.xml:109-113` |
| **JSON** | Jackson databind (Spring Boot managed) | `pom.xml:86-88` |
| **Lombok** | 1.18.32 | `pom.xml:80-84` |
| **JWT** | jjwt 0.12.7 | `pom.xml:115-132` |
| **Actuator** | spring-boot-starter-actuator (present) | `pom.xml:51-53` |
| **RestClient/WebClient** | NOT explicitly declared, but Spring Boot 4.1's `spring-boot-starter-web` includes `RestClient`. No `webflux` dependency → no `WebClient`. | — |
| **Micrometer/Prometheus** | NOT present (Phase 6 concern) | — |
| **OpenTelemetry** | NOT present (Phase 6 concern) | — |

---

## 8. Existing Test Coverage

| Test File | What It Covers | Quality |
|-----------|---------------|---------|
| `StepExecutorFactoryTest.java` | Resolves all 5 step types to correct executor class via Spring context | Good — integration test, will need updating when StepType enum changes |
| `JobExecutionOrchestratorTest.java` | Single-step success, full job execution, cancelled run (ENV_SETUP only) | Moderate — skips on Windows for ENV_SETUP |
| `JavaExecStepExecutorTest.java` | Null config, main class exec, JVM args, classpath separator, null jvmArgs, live log queue | Good — 6 unit tests covering happy path + edge cases |
| `EnvSetupStepExecutorTest.java` | [Not read in detail] | Unknown |
| `LogCleanupStepExecutorTest.java` | [Not read in detail] | Unknown |
| `ArchiveStepExecutorTest.java` | [Not read in detail] | Unknown |
| `SftpStepExecutorTest.java` | API exploration only — no real tests (compilation check for SftpClient methods) | Minimal |

---

## 9. Items Marked [NOT FOUND IN REPO]

Nothing critical was missed. All items from the Step 0 checklist were located:
- ✅ Step-execution engine — found
- ✅ Dispatch mechanism — found (StepExecutorFactory with Spring auto-collection)
- ✅ JOB_STEP schema — found (V1 migration, config already as CLOB JSON blob)
- ✅ SFTP credential handling — found (AES/GCM/NoPadding confirmed)
- ✅ SSE log-streaming — found (LogStreamController + BlockingQueue pattern)
- ✅ Retry/timeout logic — found (per-executor timeout, no retry)
- ✅ Build files — found (Spring Boot 4.1.0, Java 21)

---

## 10. Summary of Contradictions vs. Input Plan Assumptions

| Input Plan Claim | Actual Reality | Impact on Plan |
|-----------------|----------------|----------------|
| "Replace hardcoded switch/if-else dispatch" | Dispatch is already SPI-based via StepExecutorFactory + Spring auto-collection | Phase 1 is less about creating the SPI and more about **extending** it: open the enum, add config schema support, enrich StepResult |
| "JOB_STEP may have rigid per-type columns needing migration to config_json" | `STEP_CONFIG CLOB` already exists as a generic JSON blob since V1 | No data migration needed for config format. The real DB change is relaxing the CHECK constraint on STEP_TYPE column |
| "StepContext design — new concept" | `ExecutionContext` already exists with most needed fields (runId, jobId, workingDir, envVars, liveLogQueue, cancelRequested) | New StepContext should extend or replace ExecutionContext, not start from scratch |
| "StepResult needs outputs map + execution time" | Current StepResult is `(boolean success, int exitCode, String logOutput)` — minimal | Needs expansion but backward compat with existing consumers matters |

---

## Post-Implementation Note

These findings were captured **before** Phase 1 implementation. The subsequent plan documents (phase1-00 through phase1-07) describe the design derived from these findings. All identified gaps have been addressed in code:

| Finding | Resolution | Code Location |
|---------|-----------|---------------|
| Closed `StepType` enum | Opened to plain String with overloaded setters | `JobStep.java:30`, `V6__relax_step_type_constraint.sql` |
| No config schema contract | Added via `StepConfigSchema` + `FieldDefinition` in SPI | `engine/spi/StepConfigSchema.java` |
| Minimal StepResult | Expanded to include outputs map, execution time, status enum with backward-compat methods | `engine/spi/StepResult.java` |
| No retry logic | Added via `RetryPolicy` + orchestrator retry loop | `JobExecutionOrchestrator.java:156-180` |
| SPI already exists but closed | Extended to open system: new `engine.spi.StepExecutor` with String-based type, registry dispatch | `engine/spi/StepExecutorRegistry.java`, `engine/spi/StepExecutor.java` |
| No pre-execute validation | Added presence-only required-field validation in orchestrator | `JobExecutionOrchestrator.java:245-274` |
