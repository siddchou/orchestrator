# Phase 1 — Testing Plan

## Unit Tests by New Class

### StepExecutor (interface)

| Test Case | Expected Result |
|-----------|----------------|
| Default `defaultRetryPolicy()` returns `RetryPolicy.none()` | maxAttempts == 0, delayBetweenAttempts == null |
| Subclass override of `defaultRetryPolicy()` returns custom policy | Policy values match what subclass declares |

### StepConfigSchema + FieldDefinition

| Test Case | Expected Result |
|-----------|----------------|
| Create schema with valid ENUM field (enumValues provided) | No exception, schema builds successfully |
| Create ENUM field without enumValues | `IllegalArgumentException` thrown |
| Create non-ENUM field with enumValues populated | `IllegalArgumentException` thrown |
| Schema with 0 fields | Builds successfully (valid for no-config executors) |
| Schema serialization to JSON and back | Round-trips all field values identically |

### StepResult (v2)

| Test Case | Expected Result |
|-----------|----------------|
| `success()` factory → `isSuccess()` returns true | true |
| `failure()` factory → `isSuccess()` returns false | false |
| `getExitCode()` on success with no exitCode in outputs | Returns 0 |
| `getExitCode()` on failure with no exitCode in outputs | Returns -1 |
| `getExitCode()` with exitCode=42 in outputs map | Returns 42 |
| `getLogOutput()` returns message content | Non-empty string matching message parameter |
| SKIPPED status → `isSuccess()` returns false | true (SKIPPED is not SUCCESS) |

### RetryPolicy

| Test Case | Expected Result |
|-----------|----------------|
| `none()` → `hasRetries()` | false |
| `fixed(3, 1s)` → `retries()` | 2 (3 total attempts = 2 retries after first) |
| `fixed(1, 1s)` → `retries()` | 0 (single attempt, no retry) |

### StepContext

| Test Case | Expected Result |
|-----------|----------------|
| Builder builds context with all fields populated | All getters return expected values |
| `getLiveLogQueue()` returns the queue from LogSink | Same instance reference |
| `logSink.log(line)` adds to queue when queue is not null | Queue contains the line |
| `logSink.log(line)` is no-op when queue is null | No exception, queue unchanged (null-safe) |
| `setJavaHome()` mutates javaHome field | Subsequent `getJavaHome()` returns new value |
| `envVars` is mutable copy — mutating it doesn't affect builder's original map | Isolation confirmed |
| `cancelRequested` volatile flag works across threads | Thread A sets true, Thread B reads true within 100ms |

### StepExecutorRegistry

| Test Case | Expected Result |
|-----------|----------------|
| Register 5 executors with distinct types → `get(type)` returns correct executor | All 5 resolve correctly |
| Register two executors with same type string → warning logged, last wins | Log contains warning with both bean names; get() returns second executor |
| `get()` for unregistered type → returns `Optional.empty()` | Empty optional, no exception |
| `listAll()` returns schema for every registered executor | List size == number of registered executors |
| `registeredTypes()` returns all type strings | Set contains exactly the registered type strings |
| Concurrent reads from 20 threads (simulating concurrent job runs) | No exceptions, all reads return correct executor |

---

## Unit Tests by Migrated Executor

### EnvSetupStepExecutor (migrated)

| Test Case | Expected Result |
|-----------|----------------|
| `getConfigSchema()` returns schema with javaHome (required STRING), classpathEntries (optional), extraEnvVars (optional) | 3 fields, correct types and required flags |
| Valid config → sets javaHome and classpath on context | `ctx.getJavaHome()` and `ctx.getClasspath()` updated |
| Invalid JAVA_HOME path → returns FAILED result | `!result.isSuccess()`, message mentions "does not exist" |
| Existing test file behavior preserved | `EnvSetupStepExecutorTest` passes unchanged |

### LogCleanupStepExecutor (migrated)

| Test Case | Expected Result |
|-----------|----------------|
| `getConfigSchema()` returns directory, filePattern, extraPatterns fields | Correct types: STRING, FILE_PATTERN, STRING |
| Valid config deletes matching files | Files deleted, result success with count |
| Empty patterns → FAILED result | Validation error message |
| Existing test file behavior preserved | `LogCleanupStepExecutorTest` passes unchanged |

### ArchiveStepExecutor (migrated)

| Test Case | Expected Result |
|-----------|----------------|
| `getConfigSchema()` returns sourceDir, filePatterns, archiveDir, archiveFormat (ENUM), deleteOriginal (BOOLEAN) | 5 fields with correct types |
| Creates ZIP archive of matching files | Archive exists, contains expected files |
| Creates TAR_GZ archive | Same for tar.gz format |
| Existing test file behavior preserved | `ArchiveStepExecutorTest` passes unchanged |

### JavaExecStepExecutor (migrated)

| Test Case | Expected Result |
|-----------|----------------|
| `getConfigSchema()` returns mainClass, jarPath, args, jvmArgs, timeoutMinutes fields | 5 fields with correct types |
| All 6 existing tests pass | Identical assertions succeed |
| Null config → FAILED result | Same error message as pre-migration |
| Live log queue receives output lines | Queue contains "Executing:" prefix line |

### SftpStepExecutor (migrated)

| Test Case | Expected Result |
|-----------|----------------|
| `getConfigSchema()` returns all 10 fields with correct types including SECRET_REF for credentialRef | Schema matches SftpConfig record structure |
| Credential resolution via StepContext.CredentialResolver works | Same decrypted value as pre-migration direct repo access |
| Missing credential ref → FAILED result | Error message identifies missing ref |
| Compilation test passes | `SftpStepExecutorTest` compiles (API exploration only) |

---

## Unit Tests by New Executor

### HttpCallStepExecutor

| Test Case | Expected Result |
|-----------|----------------|
| GET request to WireMock endpoint returning 200 + JSON body | SUCCESS, outputs contain statusCode=200 and responseBody |
| POST with body → server receives correct payload | WireMock verifies request body matches config body |
| expectedStatus=200 but server returns 404 | FAILED, message mentions status mismatch |
| Timeout: WireMock delays beyond timeoutSeconds | FAILED, message mentions timeout |
| Custom headers passed to server | WireMock verifies header presence |
| `getConfigSchema()` returns all fields | url (STRING required), method (ENUM default GET), headers/body (STRING optional), expectedStatus/timeoutSeconds (NUMBER) |

### ShellExecStepExecutor

| Test Case | Expected Result |
|-----------|----------------|
| Simple command "echo hello" → SUCCESS | Output contains "hello", exitCode=0 |
| Command with non-zero exit "exit 42" | FAILED, exitCode=42 in outputs |
| Timeout: "sleep 10" with timeoutSeconds=1 | FAILED, timeout message |
| envOverrides passed to process environment | Child process sees overridden env var |
| scriptPath + args executes script file | Script output captured in result |

### DbQueryStepExecutor

| Test Case | Expected Result |
|-----------|----------------|
| SELECT on H2 test DB returns rows | SUCCESS, outputs contain rowCount and row data as List<Map> |
| INSERT without allowWrite → FAILED | Security error message |
| INSERT with allowWrite=true → executes | Row inserted, rowCount=1 |
| expectRowCount mismatch (query returns 3, expectRowCount=5) | FAILED, validation error |
| Parameterized query with params bound correctly | Correct rows returned |

---

## Integration Tests

### Registry + Dispatch Flow

| Test | Setup | Expected Result |
|------|-------|----------------|
| **Spring context loads all executors** | `@SpringBootTest` with test profile, H2 DB | `StepExecutorRegistry` bean exists, `listAll()` returns schemas for all 5 migrated + 3 new = 8 executors |
| **End-to-end job: ENV_SETUP → JAVA_EXEC** | Job definition with 2 steps, run via `JobLaunchService.launch()` | Run status SUCCESS, both step entries in JOB_RUN_STEP show SUCCESS |
| **Mixed executor job: ENV_SETUP → HTTP_CALL → LOG_CLEANUP** | Job with legacy + new executors, WireMock for HTTP | All 3 steps succeed, live log queue contains entries from all three |
| **Step type not registered** | Job references step type "NONEXISTENT" | Step marked FAILED with clear error message; run status reflects failure per continueOnFailure setting |
| **Step with missing required config field** *(new — Task 10)* | Job with a step whose `STEP_CONFIG` omits a required field (e.g. SFTP config missing `host`) | Orchestrator returns FAILED with a "Missing required config field(s)" message; executor's `execute()` is **never invoked** (verified via mock/spy) |
| **Retry policy exercised** | Mock executor that fails twice then succeeds, retry policy = fixed(3, 100ms) | Orchestrator retries 2 times, final result is SUCCESS, executionTime includes retry overhead |

---

## Regression Checklist

> One line per existing step type. Each confirms identical post-migration behavior by running the pre-existing test file.

| Step Type | Existing Test File | Pass Criterion |
|-----------|-------------------|----------------|
| ENV_SETUP | `engine/executors/EnvSetupStepExecutorTest.java` | All tests pass with same assertions — javaHome validation, classpath setup, env var merge |
| LOG_CLEANUP | `engine/executors/LogCleanupStepExecutorTest.java` | All tests pass — file pattern matching, deletion count, directory validation |
| JAVA_EXEC | `engine/executors/JavaExecStepExecutorTest.java` (6 tests) | All 6 tests pass: null config failure, main class exec, JVM args, classpath separator, null jvmArgs handling, live log queue |
| ARCHIVE | `engine/executors/ArchiveStepExecutorTest.java` | All tests pass — ZIP and TAR_GZ creation, file pattern matching, deleteOriginal behavior |
| SFTP | `engine/executors/SftpStepExecutorTest.java` (compilation only) | Compiles without errors — API exploration test still references SftpClient correctly |
| Factory dispatch | `engine/StepExecutorFactoryTest.java` → **rewritten** as `engine/spi/StepExecutorRegistryTest.java` (see gap-analysis Fix #5 — the old factory is deprecated but not kept functional, since no bean implements the old interface once Tasks 1–5 land) | Same 5 assertions (one per legacy step type resolving to the correct executor class), now expressed against `StepExecutorRegistry.get(String)` instead of the deprecated `StepExecutorFactory.resolve(StepType)` |
| Orchestrator flow | `engine/JobExecutionOrchestratorTest.java` (3 tests: single step success, full job, cancelled run) | All 3 pass — sequential execution, cancel handling, and status transitions work identically |

## Completion Status

**Date:** 2026-07-26
**Result:** BUILD SUCCESS — 253 tests passing (was 228, +25 new tests)

### Newly Implemented Tests

| Test File | New Tests Added | What It Covers |
|-----------|----------------|----------------|
| `engine/spi/RetryPolicyTest.java` | 3 | `RetryPolicy.none()` has no retries, `.fixed(3, 1s)` gives 2 retries, `.fixed(1, 1s)` gives 0 retries |
| `engine/spi/FieldDefinitionTest.java` | 5 | ENUM field validation (with/without enumValues), non-ENUM with enumValues throws, empty schema valid, STRING without enumValues |
| `engine/spi/StepContextTest.java` | 8 | Builder builds all fields, live log queue reference, LogSink.log() adds to queue, null queue no-op, blank line ignored, setJavaHome mutates, env vars isolated from builder, cancel flag volatile across threads |
| `engine/spi/StepExecutorRegistryUnitTest.java` | 6 | Distinct types all resolve, duplicate type warns (Logback ListAppender) and last wins, unregistered returns empty, listAll returns schemas, registeredTypes returns all, concurrent reads from 20 threads × 50 reads |
| `engine/spi/StepResultTest.java` | +1 | SKIPPED status is not success (`isSuccess()` returns false) |
| `engine/MixedExecutorIntegrationTest.java` | +2 | Unregistered step type fails gracefully (PARTIAL), missing required config field fails validation (PARTIAL) |

### Why PARTIAL and Not FAILED?
The two new integration tests initially asserted `RunStatus.FAILED`. The orchestrator returns `RunStatus.PARTIAL` when any step fails — the `continueOnFailure=N` flag only controls whether remaining steps execute, not the final status. This is consistent with the existing `execute_continue_on_failure_skips_failed_step` test. Assertions corrected to `PARTIAL`.

### Bugs Fixed During Testing
- **Duplicate imports in StepContext.java** — `Logger` and `LoggerFactory` were imported twice (lines 3-4 and 10-11). Removed duplicates.
- **Wrong Level class in StepExecutorRegistryUnitTest** — Used `org.slf4j.event.Level` instead of `ch.qos.logback.classic.Level` for Logback's `logger.setLevel()`. Fixed import.

### Remaining Gaps (Low Priority)
The following test categories from the plan are covered by existing tests or deferred:
- **Memory leak / resource cleanup** — No explicit test for long-running registry with executor churn; acceptable for phase 1 since registry uses ConcurrentHashMap with no eviction needed for the expected scale (<50 executors).
- **Serialization round-trip** — StepResult's `toOutput()` / `fromError()` paths exercise partial serialization; a full JSON round-trip test is deferred to phase 2 (API layer).
