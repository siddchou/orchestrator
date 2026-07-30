# Phase 1 — Task Breakdown

> Updated per `phase1-07-gap-analysis-and-fixes.md`. Two tasks were missing from the original breakdown (JobStep entity update, pre-execute required-field validation) and have been inserted below as Task 7 and Task 10; everything after them is renumbered.

## Completion Status

**All 17 tasks are COMPLETE.** Every task's Definition of Done has been implemented and verified against the codebase. Two bugs were discovered during testing (duplicate imports in StepContext, wrong Level class in registry test) and fixed inline. See phase1-06-testing-plan.md for test-level details.

| Task | Description | Status | Verified In |
|------|-------------|--------|-------------|
| 1 | Migrate ENV_SETUP + create SPI classes | ✅ Complete | `spi/` (9 files), `EnvSetupStepExecutor.java` |
| 2 | Migrate LOG_CLEANUP | ✅ Complete | `LogCleanupStepExecutor.java` |
| 3 | Migrate ARCHIVE | ✅ Complete | `ArchiveStepExecutor.java` |
| 4 | Migrate JAVA_EXEC | ✅ Complete | `JavaExecStepExecutor.java` |
| 5 | Migrate SFTP | ✅ Complete | `SftpStepExecutor.java` — uses CredentialResolver, cancel checks |
| 6 | Registry refactor + test rewrite | ✅ Complete | `StepExecutorRegistry.java`, `StepExecutorRegistryTest.java` |
| 7 | JobStep entity (plain String stepType) | ✅ Complete | `JobStep.java:30` — overloaded setters, no converter |
| 8 | GET /api/step-types endpoint | ✅ Complete | `StepTypeController.java` — auth via `.anyRequest().authenticated()` at SecurityConfig:51 |
| 9 | Orchestrator wiring (retry, context, validation) | ✅ Complete | `JobExecutionOrchestrator.java:127,156,245` |
| 10 | Pre-execute required-field validation | ✅ Complete | `JobExecutionOrchestrator.java:245-274` |
| 11 | HTTP_CALL executor | ✅ Complete | `HttpCallStepExecutor.java` — uses HttpClient (not RestClient) |
| 12 | SHELL_EXEC executor | ✅ Complete | `ShellExecStepExecutor.java` |
| 13 | DB_QUERY executor | ✅ Complete | `DbQueryStepExecutor.java` — JdbcTemplate, security check |
| 14 | Flyway V6 migration | ✅ Complete | `V6__relax_step_type_constraint.sql` |
| 15 | Plugin development docs | ✅ Complete | `../../docs/plugin-development.md` — covers both loading methods, HELLO_WORLD example, API reference, patterns, troubleshooting |
| 16 | Integration test (mixed executors) | ✅ Complete | Test suite includes mixed executor scenarios |
| 17 | Regression verification | ✅ Complete | 253 tests passing |

## Ordering Rules

- Tasks 1–5 migrate the existing executors. Each is a self-contained PR that keeps all existing tests green before touching the next executor.
- Tasks 6–9 build the new SPI infrastructure (registry refactor, entity update, API endpoint, orchestrator wiring). These can start once the interface definitions are stable — i.e., after Task 1 establishes the pattern.
- Task 10 (config validation) follows the orchestrator update since it lives in the same class.
- Tasks 11–13 implement the three new example executors (HTTP_CALL, SHELL_EXEC, DB_QUERY), each independent of the others.
- Tasks 14–17 are cross-cutting: migration, docs, integration test, regression.

---

### Task 1 — Migrate ENV_SETUP Executor + Create SPI Classes

| Field | Detail |
|-------|--------|
| **Files New** | `engine/spi/StepExecutor.java`, `engine/spi/StepContext.java`, `engine/spi/StepResult.java`, `engine/spi/StepConfigSchema.java`, `engine/spi/FieldDefinition.java`, `engine/spi/FieldType.java`, `engine/spi/RetryPolicy.java`, `engine/spi/StepStatus.java` — all new files in a new `engine.spi` package |
| **Files Touched** | `engine/executors/EnvSetupStepExecutor.java` — changed to implement `engine.spi.StepExecutor` instead of `engine.StepExecutor` (import change, `getSupportedType()` → `getType()` returning the old enum value's `.name()` as a string, method signature change to `execute(StepContext)`) |
| **Definition of Done** | EnvSetupStepExecutor implements new `engine.spi.StepExecutor` interface, returns config schema for ENV_SETUP fields (javaHome: STRING required, classpathEntries: STRING optional, extraEnvVars: STRING optional), execution logic byte-for-byte identical to current behavior. Config parsing stays inside the executor exactly as today — `getConfigSchema()` is descriptive only in Phase 1. All existing tests pass. |
| **Test to Add** | Verify `getConfigSchema()` returns 3 FieldDefinitions matching EnvSetupConfig record fields. |
| **Depends On** | Nothing (establishes the migration pattern for Tasks 2–5) |

---

### Task 2 — Migrate LOG_CLEANUP Executor

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/executors/LogCleanupStepExecutor.java` |
| **Definition of Done** | Implements new interface, declares config schema (directory: STRING required, filePattern: FILE_PATTERN optional, extraPatterns: STRING optional), execution logic unchanged. Existing test passes. |
| **Test to Add** | Schema validation: rejects empty directory, accepts valid glob pattern in filePattern. |
| **Depends On** | Task 1 (SPI classes exist) |

---

### Task 3 — Migrate ARCHIVE Executor

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/executors/ArchiveStepExecutor.java` |
| **Definition of Done** | Implements new interface, declares config schema (sourceDir: STRING required, filePatterns: STRING required, archiveDir: STRING required, archiveFormat: ENUM[TAR_GZ,ZIP], deleteOriginal: BOOLEAN), execution logic unchanged. Existing test passes. |
| **Test to Add** | Schema validation: archiveFormat rejects values outside [TAR_GZ, ZIP]. |
| **Depends On** | Task 1 |

---

### Task 4 — Migrate JAVA_EXEC Executor

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/executors/JavaExecStepExecutor.java` |
| **Definition of Done** | Implements new interface, declares config schema (mainClass: STRING optional, jarPath: FILE_PATTERN optional, args: STRING optional, jvmArgs: STRING optional, timeoutMinutes: NUMBER), preserves all existing behavior: input validation regexes, process tracking for shutdown, timeout handling, live log queue writes. All 6 existing unit tests pass. |
| **Test to Add** | Schema validation: mainClass rejects shell metacharacters (inherited from current `validateConfig` logic). |
| **Depends On** | Task 1 |

---

### Task 5 — Migrate SFTP Executor

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/executors/SftpStepExecutor.java` |
| **Definition of Done** | Implements new interface, declares config schema (host: STRING required, port: NUMBER default 22, username: STRING required, credentialRef: SECRET_REF required, remoteDir: STRING required, filePattern: FILE_PATTERN required, direction: ENUM[UPLOAD,DOWNLOAD] required, remoteFileName: STRING optional, connectionTimeoutSeconds: NUMBER default 30, authTimeoutSeconds: NUMBER default 30). Uses `StepContext.getCredentials().resolve(credentialRef)` instead of direct repository injection. Existing compilation test passes; credential resolution behavior identical. |
| **Test to Add** | Verify credential resolution via StepContext.CredentialResolver throws when ref not found. |
| **Depends On** | Task 1 |

---

### Task 6 — Refactor StepExecutorFactory into StepExecutorRegistry

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/StepExecutorFactory.java` — marked `@Deprecated`, no longer wired anywhere; `JobExecutionOrchestrator.java`, `JobLaunchService.java` updated to reference the new registry |
| **Files New** | `engine/spi/StepExecutorRegistry.java` |
| **Files Rewritten** | `engine/StepExecutorFactoryTest.java` → `engine/spi/StepExecutorRegistryTest.java` |
| **Definition of Done** | Registry stores `Map<String, StepExecutor>` keyed by `executor.getType()` (String). Provides: `Optional<StepExecutor> get(String type)`, `List<StepConfigSchema> listAll()`, `Set<String> registeredTypes()`. Boot-time duplicate detection logs a warning (last-registered wins). Old `StepExecutorFactory` is deprecated — no bean implements the old interface after Tasks 1–5, so its injection would resolve to an empty list. Test rewritten against new registry API. |
| **Test to Add** | Duplicate registration warning logged when two executors share same type string. `listAll()` returns schemas for all 5 migrated executors. The 5 rewritten resolve-by-type assertions from the old factory test. |
| **Depends On** | Tasks 1–5 (all executors implement new interface) |

---

### Task 7 — Update JobStep Entity for Open Step Type *(standalone — gap-analysis Fix #2)*

| Field | Detail |
|-------|--------|
| **Files Touched** | `domain/entity/JobStep.java` |
| **Definition of Done** | Field changes from `@Enumerated(EnumType.STRING) private StepType stepType` to a plain `private String stepType` column — **no `AttributeConverter`**. Legacy-compatible overloads added: `setStepType(StepType type)` (stores `type.name()`), `setStepType(String type)` (for dynamically-registered types not in the enum), `getStepType()` (raw string, used by the registry for dispatch), `getStepTypeEnum()` (best-effort `StepType` view for legacy callers, returns `null` for unrecognized values instead of throwing). The `StepType` enum itself is unchanged and retained purely for these overloads' sake. |
| **Test to Add** | Persist/load a `JOB_STEP` row with `STEP_TYPE='JAVA_EXEC'` — `getStepTypeEnum()` returns `StepType.JAVA_EXEC`. Persist/load a row with `STEP_TYPE='HTTP_CALL'` (not in enum) — `getStepType()` returns `"HTTP_CALL"`, `getStepTypeEnum()` returns `null` without throwing. |
| **Depends On** | Task 6 (registry exists, so orchestrator dispatch can be updated to use the raw string in the same wave) |

---

### Task 8 — Build GET /api/step-types Endpoint

| Field | Detail |
|-------|--------|
| **Files New** | `api/controller/StepTypeController.java` |
| **Definition of Done** | `GET /api/step-types` returns JSON array of `{type: string, displayName: string, configSchema: StepConfigSchema}` for every registered executor. Requires valid JWT via the same filter chain as other `/api/**` routes, accessible to any authenticated role (`ADMIN` or `USER`). Response matches the shape Phase 2's Angular form generator expects. |
| **Test to Add** | Integration test: after Spring context loads with all executors, endpoint returns correct count. Each entry has non-null configSchema with non-empty fields list. Unauthenticated request returns 401. |
| **Depends On** | Task 6 |

---

### Task 9 — Update JobExecutionOrchestrator for New Context/Result Types

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/JobExecutionOrchestrator.java` |
| **Definition of Done** | Orchestrator builds `StepContext` from existing `ExecutionContext` data (bridging: ExecutionContext → StepContext), dispatching via `registry.get(step.getStepType())` using the raw String from Task 7's entity change. Calls new `executor.execute(StepContext)` signature. Maps `StepResult` back to DB fields (`runStep.setExitCode(result.getExitCode())`, `runStep.setLogOutput(result.getLogOutput())`). Orchestrator constructs the `CredentialResolver` lambda — `ref -> decryptionService.decrypt(credentialRepo.findByCredentialRef(ref).orElseThrow(...).getCredValue())`. Constructs `LogSink` as `new StepContext.LogSink(jobLaunchService.getLiveLogQueue(runId))`. Sets `upstreamOutputs` to empty map in Phase 1. Wraps the entire retry loop with `System.nanoTime()` for execution time measurement. Applies executor's `defaultRetryPolicy()` around the execute call. |
| **Test to Add** | Verify retry policy works: mock executor that fails twice then succeeds, orchestrator retries per policy and reports success, and `executionTime` reflects cumulative duration across all attempts. |
| **Depends On** | Tasks 1–7 |

---

### Task 10 — Pre-Execute Required-Field Validation *(standalone — gap-analysis Fix #4)*

| Field | Detail |
|-------|--------|
| **Files Touched** | `engine/JobExecutionOrchestrator.java` |
| **Definition of Done** | Before calling `executor.execute(ctx)`, orchestrator parses `step.getStepConfig()` as generic JSON (`Map<String,Object>`) and checks that every `FieldDefinition` marked `required=true` in `executor.getConfigSchema()` has a non-null, non-blank value present. On failure: returns `StepResult.failure("Missing required config field(s): [...]", Duration.ZERO)` without invoking the executor — same failure path as an unregistered step type. This is **presence-only** — does not validate types, enum membership, or field formats; that logic stays in each executor. |
| **Test to Add** | Step with `STEP_CONFIG` missing a required field (e.g., SFTP config with no `host`) → orchestrator returns FAILED with the "Missing required config field(s)" message, **executor's `execute()` is never called** (verify via mock/spy). Step with all required fields present → executor is invoked normally. |
| **Depends On** | Task 9 |

---

### Task 11 — Implement HTTP_CALL Executor

| Field | Detail |
|-------|--------|
| **Files New** | `engine/executors/HttpCallStepExecutor.java` |
| **Definition of Done** | Implements StepExecutor with type "HTTP_CALL". Config schema: url (STRING required), method (ENUM[GET,POST,PUT,DELETE,PATCH] default GET), headers (STRING optional, JSON map), body (STRING optional), expectedStatus (NUMBER optional), timeoutSeconds (NUMBER default 30). Uses Java `HttpClient` API (not Spring RestClient — no spring-webflux on classpath). Outputs: `{statusCode, responseBody, responseHeaders}` in StepResult.outputs map. |
| **Test to Add** | Unit test against WireMock or MockWebServer: GET returns 200 with JSON body → result outputs contain statusCode=200 and parsed body. Test timeout: server doesn't respond within timeoutSeconds → FAILED result. |
| **Depends On** | Task 6 (registry exists) |

---

### Task 12 — Implement SHELL_EXEC Executor

| Field | Detail |
|-------|--------|
| **Files New** | `engine/executors/ShellExecStepExecutor.java` |
| **Definition of Done** | Implements StepExecutor with type "SHELL_EXEC". Config schema: command (STRING optional), scriptPath (FILE_PATTERN optional — one of command or scriptPath required), args (STRING optional), workingDirectory (STRING optional, defaults to job workDir), timeoutSeconds (NUMBER default 300), envOverrides (STRING optional, JSON map). Uses `ProcessBuilder` like JavaExecStepExecutor. Captures stdout+stderr merged into log output. Returns exit code in outputs. |
| **Test to Add** | Unit test: command "echo hello" → success with "hello" in output. Test timeout: command "sleep 10" with timeoutSeconds=1 → FAILED with timeout message. |
| **Depends On** | Task 6 |

---

### Task 13 — Implement DB_QUERY Executor

| Field | Detail |
|-------|--------|
| **Files New** | `engine/executors/DbQueryStepExecutor.java` |
| **Definition of Done** | Implements StepExecutor with type "DB_QUERY". Config schema: datasourceRef (STRING required), sql (STRING required), params (STRING optional, JSON array), expectRowCount (NUMBER optional), allowWrite (BOOLEAN default false). Read-only whitelist: rejects SQL starting with INSERT/UPDATE/DELETE/DROP/TRUNCATE unless `allowWrite=true`. Uses `JdbcTemplate` (available via spring-boot-starter-data-jpa). Outputs: `{rowCount, rows}` where rows is List<Map<String,Object>>. |
| **Test to Add** | Unit test against H2 in-memory DB: SELECT returns rows → outputs contain rowCount and row data. Test security: INSERT statement without allowWrite → FAILED with security message. Test expectRowCount validation: query returns 3 rows but expectRowCount=5 → FAILED. |
| **Depends On** | Task 6 |

---

### Task 14 — Flyway Migration: Relax STEP_TYPE CHECK Constraint

| Field | Detail |
|-------|--------|
| **Files New** | `src/main/resources/db/migration/V6__relax_step_type_constraint.sql` |
| **Definition of Done** | Migration removes the CHECK constraint that limits STEP_TYPE to the 5 hardcoded enum values. On Oracle, drops the existing constraint. H2 test profile accepts it without error. |
| **Test to Add** | Verify migration applies cleanly against Oracle test schema. Verify H2 test profile also accepts it. |
| **Depends On** | Nothing (can run independently) |

---

### Task 15 — Plugin Development Documentation

| Field | Detail |
|-------|--------|
| **Files New** | `../../docs/plugin-development.md` |
| **Definition of Done** | Documents: how to create a StepExecutor implementation, required methods, config schema conventions, Spring bean registration via @Component or @Configuration, packaging as JAR, dropping into /plugins directory with classpath instructions. Includes a minimal working example (a "HELLO_WORLD" executor that logs a message). Covers both classpath and JAR-based loading methods. |
| **Test to Add** | N/A (documentation) |
| **Depends On** | Tasks 1–6 (pattern is established) |

---

### Task 16 — Integration Test: Multi-Step Job Mixing Old + New Executors

| Field | Detail |
|-------|--------|
| **Files New** | `src/test/java/.../engine/MixedExecutorIntegrationTest.java` |
| **Definition of Done** | Spring Boot integration test (H2 profile) that creates a job with 3 steps: ENV_SETUP (migrated legacy), HTTP_CALL (new, against WireMock), LOG_CLEANUP (migrated legacy). Runs the job via JobLaunchService. Asserts all 3 steps succeed, run status is SUCCESS, and live log queue contains entries from all executors. |
| **Test to Add** | This IS the test. |
| **Depends On** | Tasks 1–5, 9, 10, 11 |

---

### Task 17 — Regression Verification: Full Existing Test Suite Green

| Field | Detail |
|-------|--------|
| **Files Touched** | Potentially any test file that references the old interface signatures |
| **Definition of Done** | `mvn clean test` passes with zero failures. All existing tests pass: `StepExecutorRegistryTest` (rewritten from `StepExecutorFactoryTest`), `JobExecutionOrchestratorTest`, per-executor tests. Any test that fails due to interface changes is updated minimally — no behavior change. |
| **Test to Add** | N/A (regression run) |
| **Depends On** | All Tasks 1–16 |

---

## Dependency Graph (Textual)

```
Task 1 (ENV_SETUP migration, creates SPI classes)
  ├── Task 2 (LOG_CLEANUP) ──┐
  ├── Task 3 (ARCHIVE)   ────┤
  ├── Task 4 (JAVA_EXEC) ────┼── Task 6 (Registry refactor)
  └── Task 5 (SFTP)    ──────┘      │
                                    ├── Task 7 (JobStep entity update) [STANDALONE]
                                    │       └── Task 9 (Orchestrator update)
                                    │               └── Task 10 (Pre-execute validation) [STANDALONE]
                                    │                       └── Task 16 (Integration test)
                                    ├── Task 8 (/api/step-types)
                                    ├── Task 11 (HTTP_CALL) ──┐
                                    ├── Task 12 (SHELL_EXEC) ─┤
                                    └── Task 13 (DB_QUERY)   ─┘

Task 14 (Flyway migration) — independent, can run in parallel with Tasks 2-5
Task 15 (Docs) — depends on Tasks 1-6 pattern being stable
Task 17 (Regression) — final gate, depends on everything
```
