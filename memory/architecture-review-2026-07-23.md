---
name: architecture-review-2026-07-23
description: Solution architect review of the orchestrator codebase - findings on layering, concurrency, security, and operational concerns
metadata:
  type: project
---

# Architecture Review — Orchestrator (Java 21 / Spring Boot)

## Executive Summary

The codebase is a well-structured job orchestration platform with clean layer separation. The core domain (job definitions, steps, runs, scheduling) is sound, but several **critical operational and architectural risks** exist around concurrency management, graceful shutdown ordering, security, and the in-memory state model. These issues are partially addressed by recent commits but have gaps that need closing before production deployment.

## Architecture Overview

```
orchestrator/
├── src/main/java/com/novakai/orchestrator/
│   ├── api/                  # REST layer (controllers + DTOs)
│   │   ├── controller/        — 8 controllers, role-based authz via SecurityConfig
│   │   ├── dto/              — Immutable records for request/response payloads
│   │   ├── mapper/           — Manual entity↔DTO mapping
│   │   └── service/          # Business logic layer (JobDefinitionService, JobRunQueryService)
│   ├── domain/               # Domain model and enums
│   │   ├── config/           — Step-specific config records (parsed from JSON in DB columns)
│   │   ├── entity/           — JPA entities with Lombok @Data/@Builder
│   │   └── enums/            — StepType, RunStatus, TriggerType, CredentialType
│   ├── engine/               # Core orchestration engine
│   │   ├── JobLaunchService    — Manages async execution, futures, contexts, log queues
│   │   ├── JobExecutionOrchestrator  — Sequential step executor with continueOnFailure logic
│   │   ├── JobSchedulerService — Cron-based scheduling via Spring TaskScheduler
│   │   ├── StepExecutor (interface) + factory + 5 implementations
│   │   └── config/AsyncConfig  — ThreadPoolTaskExecutor + TaskScheduler beans, @PreDestroy hooks
│   ├── repository/          # Spring Data JPA repositories
│   ├── security/            # JWT auth, RBAC, audit logging via AOP aspect
│   ├── cli/                # CLI mode runner (separate Spring profile)
│   └── OrchestratorApplication.java  — Main entry point
└── src/main/resources/db/migration/V1-V5__*.sql  # Flyway migrations for Oracle

Frontend: orchestrator-ui/ (Angular, built into static resources via frontend-maven-plugin)
```

## Key Strengths

- **Clean layered architecture**: API → Service → Engine → Repository, with clear separation of concerns. Controllers are thin; business logic lives in services and the engine package.
- **Strategy pattern for step execution**: `StepExecutor` interface + factory provides clean extensibility. Adding a new step type is straightforward (implement interface, add to enum).
- **Security-by-default RBAC**: Role hierarchy (VIEWER < OPERATOR < ADMIN) with method-level and URL-level authorization. Sensitive operations require ADMIN role.
- **Graceful shutdown infrastructure exists**: `@PreDestroy` hooks on `JobLaunchService`, `JavaExecStepExecutor`, `SftpStepExecutor`, `AsyncConfig`. Process tracking for child processes and SFTP clients.
- **Input validation on dangerous paths**: `JavaExecStepExecutor` validates class names, jar paths (directory traversal prevention), and JVM args against a whitelist of safe characters. SFTP uses glob patterns with file existence checks.
- **MDC-based request tracing**: Task decorator propagates MDC context to async threads for log correlation by runId/jobId.

## Critical Issues & Recommendations

### 1. In-Memory State Model — No Clustering Support (CRITICAL)

**Finding:** `JobLaunchService` stores all runtime state in memory:
- `ConcurrentHashMap<Long, Future<?>> activeFutures`
- `ConcurrentHashMap<Long, ExecutionContext> activeContexts`  
- `ConcurrentHashMap<Long, BlockingQueue<String>> liveLogQueues`

This means **cancel operations only work within the same JVM instance**. If running multiple instances behind a load balancer (or if Kubernetes restarts a pod), cancel requests sent to a different node will fail silently. The `@PreDestroy` shutdown hooks on executors also won't coordinate across instances.

**Why it matters:** This is a job orchestration system — cancellation and monitoring are core operations that must work reliably regardless of which instance handles the request.

**Recommendation:** Move active run state (futures, contexts, log queues) to Redis or a database-backed store. At minimum, use `runId` as the key in a shared cache so any node can signal cancellation. For SSE log streaming, consider a message broker pattern instead of per-instance blocking queues.

### 2. Shutdown Ordering Is Undefined (HIGH RISK)

**Finding:** Multiple `@PreDestroy` methods exist but Spring does **not guarantee execution order**:
- `JobLaunchService.shutdown()` — cancels futures, marks contexts cancelled
- `JavaExecStepExecutor.shutdown()` — destroys running child processes  
- `SftpStepExecutor.shutdown()` — stops SFTP clients
- `AsyncConfig.shutdown()` — logs message only (doesn't shut down the executor)

The `ThreadPoolTaskExecutor` is never explicitly shut down. While Spring may handle this, the ordering relative to the service-level `@PreDestroy` methods that cancel futures is undefined. A race condition exists where:
1. Executor threads are killed before `JobLaunchService` can signal cancellation
2. Child processes are destroyed while still writing log output

**Recommendation:** Use `@Order` annotations or implement `DisposableBean`/`SmartLifecycle` to enforce shutdown sequence: (1) cancel all futures, (2) wait for graceful completion with timeout, (3) forcefully terminate child processes and SFTP clients. Explicitly call `executor.shutdown()` / `awaitTermination()`.

### 3. SSE Log Streaming Uses Virtual Threads Without Backpressure Control (MEDIUM)

**Finding:** `LogStreamController.streamLog` spawns a virtual thread per client connection that polls the queue every second. The `SseEmitter` has `Long.MAX_VALUE` timeout — no idle timeout or heartbeat mechanism. If clients disconnect without proper cleanup, threads accumulate.

Additionally, there's no backpressure: if log output rate exceeds what SSE can deliver (slow network), lines buffer in memory indefinitely via the unbounded `LinkedBlockingQueue`.

**Recommendation:** Set a reasonable emitter timeout (e.g., 10 minutes) with periodic heartbeats (`SseEmitter.event().name("ping")`). Consider using Spring's built-in backpressure support or limiting queue size. Clean up emitters on client disconnect explicitly.

### 4. JWT Secret Management — Default Key in Source (HIGH SECURITY RISK)

**Finding:** `application.yml` contains:
```yaml
jwt-secret: ${JWT_SECRET:default-secret-key-must-be-at-least-32-changes!!}
```

The default secret is hardcoded as a fallback. If the environment variable isn't set, JWT tokens are signed with this well-known key — an attacker who knows this string can forge admin tokens. The comment says "default" but there's no enforcement that `JWT_SECRET` must be provided in production.

**Recommendation:** Fail fast at startup if `JWT_SECRET` is not set or equals the default value (in non-dev profiles). Use a `@ConfigurationProperties` validation with `@Assert`. Consider integrating with a secrets manager for key rotation.

### 5. Step Config Stored as Unstructured JSON in CLOB Columns (TECHNICAL DEBT)

**Finding:** `JobStep.stepConfig` is a `@Lob String` containing arbitrary JSON parsed into different record types per step type (`JavaExecConfig`, `SftpConfig`, etc.). This creates several issues:
- No schema validation at the database level
- Migration of config fields requires application-level changes, not SQL migrations
- Querying step configurations (e.g., "find all SFTP steps pointing to host X") is impossible via SQL

**Recommendation:** Either move to structured columns per step type or use a proper JSON column with database-native JSON functions for querying. At minimum, add validation that the config matches the expected schema before persisting. Consider migrating to a JSONB-style approach if Oracle supports it (12c+ `JSON` type).

### 6. Missing Idempotency on Run Trigger (MEDIUM)

**Finding:** The `/api/jobs/{id}/run` endpoint creates a new `JobRun` record each time it's called, even with rapid retries from clients. While `JobAlreadyRunningException` prevents concurrent execution of the same job, there's no mechanism to detect duplicate triggers for the same logical request (e.g., client retry after timeout).

**Recommendation:** Accept an optional idempotency key in the trigger API header. Store `(jobId, triggeredBy, timestamp-window)` as a cache key and return the existing run if a recent one exists. This prevents accidental duplicate executions during network issues.

### 7. Thread Pool Configuration May Be Too Small (MEDIUM)

**Finding:** `ThreadPoolTaskExecutor` is configured with:
- Core pool size: 10
- Max pool size: 20  
- Queue capacity: 50
- RejectedExecutionHandler: CallerRunsPolicy

With a queue of only 50 and the caller-runs fallback, under burst load this can cause request threads to block (degrading API responsiveness) or tasks to be rejected. For an orchestration system where each job runs in its own thread, 10 concurrent jobs is quite limiting if individual steps spawn subprocesses that hold threads during `process.waitFor()`.

**Recommendation:** Make pool sizes configurable via properties with sensible defaults based on CPU count. Consider using a separate executor for short-lived tasks vs. long-running job execution. Monitor queue depth and active thread metrics in production to tune sizing.

### 8. No Rate Limiting or Circuit Breaker (MEDIUM)

**Finding:** The API has no rate limiting, circuit breakers, or bulkhead patterns. External systems (SFTP endpoints, Java subprocesses) are called without resilience patterns. A slow SFTP server could tie up executor threads indefinitely until the timeout expires.

**Recommendation:** Integrate Resilience4j for:
- Rate limiting on high-risk endpoints (`POST /run`, credential operations)
- Circuit breaker around SFTP connections and external process execution
- Time limiter to enforce timeouts even if `process.waitFor()` doesn't respond to interrupts

### 9. Audit Trail Gap — Missing Job Definition CRUD Events (LOW)

**Finding:** The `@Auditable` annotation is used on:
- `TRIGGER_RUN`, `CANCEL_RUN` in `JobExecutionController`
- `CREATE_JOB`, `DELETE_JOB` in `JobDefinitionService`

But missing from audit trail:
- `UPDATE_JOB` (job definition updates)
- Step CRUD operations (add/update/delete/reorder steps)
- Schedule enable/disable/toggle operations
- Credential creation/deletion (sensitive!)

**Recommendation:** Add `@Auditable` to all mutating endpoints. Especially important for credential operations — any key generation or deletion should be auditable with who did it and when. Consider including the entity ID in a structured way rather than relying on argument extraction heuristics.

### 10. Database Migration Gap: V3 Missing (INFORMATIONAL)

**Finding:** Flyway migrations exist as V1, V2, V4, V5 but **V3 is missing**. This could mean either it was never created or was deleted. If this app has been deployed and migrated, the absence of V3 might indicate a skipped version that could cause issues if someone tries to repair/migrate from scratch.

**Recommendation:** Verify migration history in the database (`SELECT * FROM flyway_schema_history`). If V3 was intentionally removed (e.g., its changes were folded into another version), add a `V3__noop.sql` with just a comment explaining why, or renumber subsequent migrations if the app hasn't been deployed to production yet.

## Architectural Decisions Recorded in Code

| Decision | Where | Quality |
|----------|-------|---------|
| Strategy pattern for step execution via interface + factory | `StepExecutor`, `StepExecutorFactory` | Good — extensible |
| In-memory state for active runs | `JobLaunchService` | Risky at scale (see #1) |
| Role-based access control with 3 tiers | `SecurityConfig` | Good baseline, could use ABAC for finer granularity |
| `@PreDestroy` hooks on executors + service | Multiple classes | Good intent, poor ordering guarantees (see #2) |
| Input validation/sanitization on JavaExec args & paths | `JavaExecStepExecutor` | Strong — prevents command injection |

## Layering Assessment

```
┌─────────────────────────────────────────┐
│   API Layer  (Controllers + DTOs)        │ ← Thin, good
├─────────────────────────────────────────┤
│ Service Layer  (JobDefinitionService,    │ ← Good separation of CRUD vs query
│                  JobRunQueryService)      │
├─────────────────────────────────────────┤
│ Engine Layer  (JobLaunchService,         │ ← Heavy but well-organized; could
│                Orchestrator,              │   benefit from extracting run state
│                StepExecutor strategy)     │   to a dedicated service
├─────────────────────────────────────────┤
│ Repository Layer  (Spring Data JPA)      │ ← Standard, good
└─────────────────────────────────────────┘
```

The layering is clean — no upward dependencies. Controllers don't touch repositories; services are the composition root for domain logic. The `engine` package correctly contains orchestration-specific concerns separate from CRUD business logic in the `api/service` layer.

## Operational Concerns Summary

| Concern | Status | Notes |
|---------|--------|-------|
| Graceful shutdown | Partial | Hooks exist, ordering undefined |
| Health checks | Present | `/api/system/health`, actuator probes enabled |
| Metrics | Configured | Prometheus endpoint exposed |
| Log correlation | Good | MDC propagation to async threads |
| Process cleanup on crash | Weak | No watchdog for orphaned child processes if JVM crashes without SIGTERM |
| Horizontal scaling | Not supported | All runtime state is in-memory per instance |

## Summary of Recommendations (Priority Order)

1. **Immediate**: Remove default JWT secret fallback — fail fast at startup if not configured in prod profiles
2. **High priority**: Move active run state to Redis for horizontal scalability and reliable cancellation across instances  
3. **High priority**: Fix shutdown ordering with explicit lifecycle management (`SmartLifecycle` or ordered `@PreDestroy`)
4. **Medium**: Add idempotency keys to job trigger API
5. **Medium**: Configure SSE emitter timeout + backpressure on log streaming queues
6. **Low**: Complete audit coverage for all mutating operations, especially credential CRUD
7. **Technical debt**: Evaluate structured storage for step config JSON instead of CLOB columns

## Overall Assessment: B-/C+

The architecture demonstrates solid Spring Boot patterns and clean layering. The core orchestration model is sound. However, the in-memory state design fundamentally limits horizontal scalability — a critical gap for an orchestration system that may need to scale across multiple nodes. The shutdown coordination issues present real risks of orphaned processes under container restarts (common in Kubernetes). With targeted fixes on items #1-#3 above, this would be a solid B-grade architecture suitable for production with caveats around single-instance deployment.