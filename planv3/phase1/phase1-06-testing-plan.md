# Phase 1 — Testing Plan

## Unit Tests by New Class

### SPI Interfaces (7 test files)

| Test File | What It Covers | Assertions |
|-----------|---------------|------------|
| `StepExecutorRegistryTest.java` | Spring context loads all executor beans, registry resolves each type, `listAll()` returns schemas, unknown type returns empty Optional | 5 resolve assertions + listAll count + unknown type |
| `StepExecutorRegistryUnitTest.java` | Registry behavior without Spring: duplicate detection warning, constructor injection of executor list | Duplicate warning log, putIfAbsent behavior |
| `PluginScannerTest.java` | Scanner finds JARs in temp directory, loads via ServiceLoader, registers into registry, handles corrupt JAR gracefully | JAR discovery, loading success, error handling on bad JAR |
| `RetryPolicyTest.java` | `none()` returns maxAttempts=1, `fixed(n, d)` sets correctly, `retries()` calculation | Factory method output, retries() math |
| `FieldDefinitionTest.java` | ENUM type requires enumValues (constructor throws if null), other types don't require enumValues | Constructor validation, field accessors |
| `StepContextTest.java` | Builder constructs context with all fields, CredentialResolver functional interface works, LogSink.write delegates to queue | Builder output, resolver invocation, log sink delegation |
| `StepResultTest.java` | Factory methods produce correct records, backward-compat methods (`isSuccess`, `getExitCode`, `getLogOutput`) work | success()/failure() factory, compat method mapping |

### Executor Tests (8 test files + 2 helpers)

| Test File | What It Covers | Key Scenarios |
|-----------|---------------|---------------|
| `EnvSetupStepExecutorTest.java` | ENV_SETUP executor validates JAVA_HOME, sets context fields | Valid config → success; missing javaHome → failure |
| `LogCleanupStepExecutorTest.java` | LOG_CLEANUP executor deletes files matching pattern | Pattern match → files deleted; no matches → success with 0 count |
| `ArchiveStepExecutorTest.java` | ARCHIVE executor creates tar.gz/zip archives | Both formats, deleteOriginal flag, file pattern inclusion |
| `JavaExecStepExecutorTest.java` | JAVA_EXEC executor runs Java process, handles timeout | Successful execution, timeout kill, cancel check, 6 existing tests |
| `SftpStepExecutorTest.java` | SFTP executor compilation test (no SSH server in unit test) | Compilation verifies interface compliance; credential resolver wiring |
| `HttpCallStepExecutorTest.java` | HTTP_CALL executor makes HTTP requests | GET/POST methods, status code validation, timeout handling, response parsing |
| `ShellExecStepExecutorTest.java` | SHELL_EXEC executor runs shell commands | echo command success, timeout kill, exit code capture |
| `DbQueryStepExecutorTest.java` | DB_QUERY executor queries H2 in-memory DB | SELECT returns rows, INSERT without allowWrite → security failure, expectRowCount validation |

**Test helpers:**
- `SleepStepExecutor.java` — a slow executor for testing timeout and cancellation behavior
- `FailStepExecutor.java` — an executor that always fails, for testing retry logic

### Orchestrator Tests

| Test File | What It Covers | Key Scenarios |
|-----------|---------------|---------------|
| `JobExecutionOrchestratorTest.java` | Step execution flow with new registry dispatch | Registry resolution, StepContext building, StepResult mapping, retry policy application |
| `MixedExecutorIntegrationTest.java` | Multi-step job mixing legacy and new executors | ENV_SETUP → HTTP_CALL → LOG_CLEANUP chain, all succeed, run status SUCCESS |

## Integration Test Scenarios

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| Mixed executor job | Create job with ENV_SETUP + HTTP_CALL + LOG_CLEANUP steps; run via JobLaunchService | All 3 steps succeed, live log queue has entries from all executors |
| Plugin-loaded executor | Place test JAR in plugins directory; start app; verify type appears in registry | Plugin executor registered, `/api/step-types` includes it |
| Unknown step type execution | Create job with `STEP_TYPE='NONEXISTENT'`; run | Step fails with "No executor registered" message |
| Missing required config field | Create SFTP step with config missing `host`; run | Pre-execute validation catches it, executor never invoked |
| Credential resolution failure | Create SFTP step with invalid credentialRef; run | Step fails after retry exhaustion with credential not found error |
| Cancel during execution | Start job with SleepStepExecutor; cancel mid-execution | Step marked FAILED, process killed, no further steps execute (unless continueOnFailure) |

## Regression Checklist

- [ ] `mvn clean test` passes with zero failures
- [ ] All 5 legacy executor tests still pass after migration to new interface
- [ ] `StepExecutorRegistryTest` covers all registered types (should be 8: 5 legacy + 3 new)
- [ ] `JobExecutionOrchestratorTest` verifies registry dispatch, not factory dispatch
- [ ] No test references the old `engine.StepExecutor` interface directly
- [ ] H2 test profile accepts V6 migration without error
- [ ] `/api/step-types` endpoint returns correct count and schema structure
- [ ] Plugin scanner handles empty directory gracefully (no startup failure)

## Test Coverage Gaps (Known)

| Gap | Reason | Risk | Mitigation |
|-----|--------|------|------------|
| SFTP executor end-to-end test | Requires SSH server; unit test is compilation-only | Medium — execution path not exercised | Manual testing against real SFTP server recommended before production deploy |
| Plugin JAR with transitive dependencies | Test uses a simple JAR without external deps | Low — URLClassLoader delegates to parent for shared classes | Integration test with a real plugin JAR that has dependencies |
| CredentialDecryptionService key padding warning | Service logs warning but doesn't fail on short keys | Medium for production | Production deployment should enforce `ORCHESTRATOR_ENCRYPTION_KEY` env var |
| Concurrent step execution | Virtual threads enable parallelism, but tests run sequentially | Low — ConcurrentHashMap is thread-safe by design | Load test with concurrent job runs before production deploy |

## Test Count Summary

| Category | Count | Notes |
|----------|-------|-------|
| Total tests | 253 | Including 25 new for Phase 1 |
| SPI interface tests | ~40 | Across 7 test files |
| Executor unit tests | ~50 | Across 8 executor test files |
| Orchestrator tests | ~15 | Including mixed executor integration |
| Legacy tests (unchanged) | ~148 | Existing controller, service, repository tests |
