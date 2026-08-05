<!-- FILE: phase5-code-review-findings.md -->
# Phase 5 — Code Review Findings

## 1. Run-completion status transition points

**Two locations set terminal run status:**

### JobExecutionOrchestrator.execute() (linear execution)
- **File:** `src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java`
- **Lines 87-98:** The `finally` block sets `CANCELLED`, `PARTIAL`, or `SUCCESS` and calls `runRepo.save(run)`
- Line 94: `run.setStatus(anyStepFailed ? RunStatus.PARTIAL : RunStatus.SUCCESS)`
- Line 91: `run.setStatus(RunStatus.CANCELLED)`

### JobExecutionOrchestrator.executeSingleStep() (single-step execution)
- **File:** Same file, lines 112-123
- Line 119: `run.setStatus(stepFailed ? RunStatus.FAILED : RunStatus.SUCCESS)`

### DagExecutionEngine.completeRun() (DAG execution)
- **File:** `src/main/java/com/novakai/orchestrator/engine/DagExecutionEngine.java`
- **Lines 480-514:** Sets terminal status based on step results: `CANCELLED`, `FAILED`, `PARTIAL`, or `SUCCESS`
- Line 513: `runRepo.save(run)`

**Conclusion:** There is no existing event/listener mechanism for run completion. Status transitions are direct field updates followed by JPA save. Phase 5 must introduce the first custom Spring event in this codebase.

## 2. ApplicationEventPublisher / pub/sub mechanism

- **No custom events exist.** The codebase uses `@EventListener(ApplicationReadyEvent.class)` in 4 places (TestDataInitializer, OrchestratorApplication, JobSchedulerService, StartupMaintenanceService, PluginScanner) — all for Spring's built-in lifecycle event only.
- **No `ApplicationEventPublisher` bean is injected anywhere** for custom domain events.
- **Phase 5 will introduce the first custom Spring ApplicationEvent.**

## 3. SMTP / mail configuration

- **`spring-boot-starter-mail` is NOT in pom.xml.** The dependency list has web, data-jpa, security, validation, actuator — no mail starter.
- **No `spring.mail.*` properties found** in any application properties/YAML file.
- **Phase 5 must add the dependency and SMTP configuration properties.**

## 4. Async execution configuration

- **File:** `src/main/java/com/novakai/orchestrator/engine/config/AsyncConfig.java`
- **Bean:** `jobTaskExecutor` — `ThreadPoolTaskExecutor`, core=10, max=20, queue=50, CallerRunsPolicy rejection
- **Bean:** `taskScheduler` — `ThreadPoolTaskScheduler`, pool-size=5, for cron scheduling
- **MDC task decorator** copies logging context into async threads
- **Phase 5 should add a dedicated notification dispatch executor** (e.g., `notificationExecutor`) rather than sharing the job execution pool — notifications are lightweight HTTP calls that shouldn't compete with step execution threads.

## 5. Flyway migration versions

Existing migrations in `src/main/resources/db/migration/`:
| Version | File |
|---------|------|
| V1 | V1__create_job_definition.sql |
| V2 | V2__create_job_run.sql |
| V3 | V3__create_schedule_and_credential.sql |
| V4 | V4__create_app_user.sql |
| V5 | V5__add_env_setup_to_job_definition.sql |
| V6 | V6__relax_step_type_constraint.sql |
| V7 | V7__add_multi_tenancy.sql |
| V8 | V8__add_step_dependencies.sql |
| V9 | V9__backfill_step_dependencies.sql |
| V10 | V10__add_job_definition_version.sql |
| V11 | V11__add_job_step_name_unique_constraint.sql |

**Next free version: V12**

## 6. StepExecutorRegistry SPI pattern (to mirror)

- **File:** `src/main/java/com/novakai/orchestrator/engine/spi/StepExecutorRegistry.java`
- Constructor takes `List<StepExecutor>` from Spring, calls `register()` for each
- `register()`: Uses `ConcurrentHashMap<String, StepExecutor>`. On duplicate type, logs a warning and lets the new one replace the old (log-and-continue)
- `get(String type)`: Returns `Optional<StepExecutor>`, logs debug on miss
- `listAll()` returns schemas; `registeredTypes()` returns key set

## 7. RunStatus enum values

- **File:** `src/main/java/com/novakai/orchestrator/domain/enums/RunStatus.java`
- Values: `PENDING, RUNNING, SUCCESS, FAILED, PARTIAL, CANCELLED, SKIPPED`

## [NOT FOUND IN REPO] Items

- **SSE broadcaster for run completion** — The plan mentions "given SSE streaming exists" but no SSE endpoint was found in the codebase. There's a `liveLogQueues` ConcurrentHashMap in JobLaunchService that provides live logs per run, but no Server-Sent Events controller endpoint. [Assumed: SSE is planned or the plan reference was aspirational.]
- **Existing notification-related UI components** — None exist yet. The notifications tab will be new.
