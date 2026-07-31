# Orchestrator — Detailed Implementation Plan (planv3)

Companion to `planv3-generic-use-enhancements.md`. That doc gives the phase order and rationale; this one goes down to file paths, schemas, interfaces, and task-level checklists so each phase can be handed to an agent or worked module-by-module, matching the style of `../plan` and `../planv2`.

Package root assumed: `com.novakai.orchestrator` (adjust if it differs from your current source layout).

---

## PHASE 1 — Pluggable Step-Type Architecture

### 1.1 New package: `engine.spi`

**File: `engine/spi/StepExecutor.java`**
```java
public interface StepExecutor {
    String getType();                          // e.g. "HTTP_CALL"
    StepConfigSchema getConfigSchema();         // drives UI form generation (Phase 2)
    StepResult execute(StepContext ctx) throws StepExecutionException;
    default RetryPolicy defaultRetryPolicy() { return RetryPolicy.none(); }
}
```

**File: `engine/spi/StepContext.java`**
- Fields: `runId`, `stepId`, `Map<String,Object> resolvedParams`, `CredentialResolver credentials`, `LogSink logSink`, `Path workDir`, `Map<String,StepOutput> upstreamOutputs` (placeholder for Phase 3 templating).

**File: `engine/spi/StepResult.java`**
- `StepStatus status` (SUCCESS/FAILED/SKIPPED), `Map<String,Object> outputs`, `String message`, `Duration executionTime`.

**File: `engine/spi/StepConfigSchema.java`**
- List of `FieldDefinition(name, label, type[STRING|NUMBER|BOOLEAN|ENUM|SECRET_REF|FILE_PATTERN], required, defaultValue, enumValues, helpText)`.
- This is the contract Phase 2's Angular form generator consumes — get this right first, it's shared surface area.

### 1.2 Registry

**File: `engine/spi/StepExecutorRegistry.java`**
- Spring `@Component`, constructor-injects `List<StepExecutor>` (Spring auto-collects all beans implementing the interface — simpler than raw `ServiceLoader` inside a Spring context, and still supports external plugin jars if they're on the classpath and Spring-annotated or manually registered via a `@Configuration`).
- Exposes `Optional<StepExecutor> get(String type)` and `List<StepConfigSchema> listAll()`.
- Boot-time validation: log a warning (don't fail startup) if two executors register the same `getType()`.

### 1.3 Migrate existing executors (no behavior change)

Move/adapt, one PR each, with existing tests kept green before touching the next:
1. `JavaExecStepExecutor implements StepExecutor` — wraps current `JAVA_EXEC` logic.
2. `SftpStepExecutor implements StepExecutor` — wraps current SFTP logic (this already has `SftpStepExecutorTest`, reuse it as regression baseline).
3. `LogCleanupStepExecutor`
4. `ArchiveStepExecutor`
5. `EnvSetupStepExecutor`

Each migration PR: extract config parsing into `getConfigSchema()`, keep execution logic byte-for-byte identical, run full existing test suite.

### 1.4 New executors (prove extensibility works end-to-end)

**`HttpCallStepExecutor`**
- Config: `url`, `method` (GET/POST/PUT/DELETE/PATCH), `headers` (map), `body` (string, supports templating later), `expectedStatus` (int or range), `timeoutSeconds`.
- Uses Spring's `RestClient`/`WebClient` (whichever the project already depends on — check `../pom.xml`).
- Output: `{statusCode, responseBody, responseHeaders}` stored in `StepResult.outputs`.

**`ShellExecStepExecutor`**
- Config: `command` (string) OR `scriptPath` + `args`, `workingDirectory`, `timeoutSeconds`, `envOverrides` (map).
- Uses `ProcessBuilder` (matches your existing decision from the batch migration — reuse that pattern/class if one already exists in `engine`).

**`DbQueryStepExecutor`**
- Config: `datasourceRef` (named datasource from config, not raw credentials inline), `sql` (parameterized), `params` (list), `expectRowCount` (optional validation).
- **Security note:** whitelist to `SELECT`/read-only by default; require an explicit `allowWrite: true` flag for INSERT/UPDATE/DELETE, logged prominently.

### 1.5 Step-type discovery endpoint

**File: `api/StepTypeController.java`**
- `GET /api/step-types` → `List<StepTypeDescriptor>` = `{type, displayName, configSchema}` for every registered `StepExecutor`. This is what Phase 2's palette calls.

### 1.6 DB changes
- No schema change strictly required for 1.1–1.5 (step configs are likely already stored as a JSON/CLOB column per step — confirm against `JOB_STEP` table). If steps currently have rigid per-type columns instead of a generic config blob, this phase's real migration is:
  - `JOB_STEP.config_json CLOB` (new column)
  - Data migration script mapping old per-type columns → JSON, keep old columns read-only for one release as a rollback safety net, drop in a later cleanup migration.

### 1.7 Plugin loading (external jars, v1 — keep simple)
- Document (`../docs/plugin-development.md`): a plugin is a jar with a Spring `@Configuration` class registering one or more `StepExecutor` beans; dropped into `/plugins`, added to classpath via `-cp` or a `spring.factories`-style loader; full dynamic (hot-reload) classloading is explicitly out of scope for v1 — flag as a future phase if needed.

### Task checklist — Phase 1
- [ ] `StepExecutor`, `StepContext`, `StepResult`, `StepConfigSchema` interfaces
- [ ] `StepExecutorRegistry`
- [ ] Migrate 5 existing executors (5 PRs, tests green each time)
- [ ] `HTTP_CALL`, `SHELL_EXEC`, `DB_QUERY` executors + unit tests
- [ ] `GET /api/step-types` endpoint + integration test
- [ ] `config_json` migration on `JOB_STEP` (Flyway script) + data backfill
- [ ] `../docs/plugin-development.md`
- [ ] Regression: full existing job-run test suite passes unchanged

### Testing strategy
- Unit test per executor (happy path, timeout, invalid config).
- One integration test running a multi-step job mixing an old executor (SFTP) and a new one (HTTP_CALL) in the same run, confirming registry dispatch works.

**Exit criteria:** New step type = one class + one bean registration. Zero changes required in `engine.core`, `api`, or DB schema (beyond the generic config column already in place).

---

## PHASE 2 — UI/UX Genericization

### 2.1 Dynamic step configuration forms

**New Angular module:** `orchestrator-ui/src/app/features/step-config/`
- `dynamic-field.component.ts` — renders one `FieldDefinition` (switch on `type`: text input, number input, checkbox, select, secret-picker, file-pattern input).
- `dynamic-step-form.component.ts` — takes a `StepConfigSchema` (fetched from `GET /api/step-types`) + current step's `config_json`, builds a reactive `FormGroup` dynamically via `FormBuilder`.
- Replace the current per-type hardcoded form components (e.g. `sftp-step-form.component.ts`, `archive-step-form.component.ts` if they exist) — keep them only if you want type-specific UX polish beyond the generic renderer; otherwise retire them.

### 2.2 Step palette

**Component:** `job-editor/step-palette.component.ts`
- Calls `GET /api/step-types` on load, renders a list/grid of available step types with icon + description.
- Drag-and-drop via Angular CDK (`DragDropModule`) into the job's step list, or simpler v1: click-to-add.

### 2.3 (Deferred to post-Phase-3) DAG canvas
- Placeholder route `/jobs/:id/canvas` stubbed now, implemented after Phase 3.1 ships. Note in code as `// TODO(phase3): replace linear step list with DAG canvas once dependsOn model exists`.

### 2.4 Multi-tenancy

**Backend:**
- New table `TEAM (id, name, created_at)`.
- `JOB_DEFINITION.team_id` FK (nullable initially for backward compat with existing single-tenant jobs — default to a "Default Team" row created in the migration).
- `USER_TEAM (user_id, team_id, role)` join table — role can differ from your existing global RBAC (e.g. team-level ADMIN vs org-level ADMIN); scope this down if your current RBAC is sufficient (e.g. just filter by team without team-level roles for v1).
- Update `JOB_DEFINITION` repository queries to filter by `team_id` from the authenticated principal's active team (store active team in JWT claim or session).

**Frontend:**
- Team switcher dropdown in the top nav (`app-shell/team-switcher.component.ts`).
- All `/api/jobs`, `/api/runs` calls pass a `teamId` (or it's inferred server-side from the JWT — prefer server-side to avoid trusting client-supplied tenant IDs).

### 2.5 Polish
- Dark mode: Angular Material already supports this — add a theme toggle using CSS custom properties / Material's `M3` theming if not already on M3; persist preference in `localStorage`.
- Run timeline: `run-timeline.component.ts` on `/runs/:id`, horizontal bar per step showing start/end/duration, color-coded by status — data already available from `JOB_RUN_STEP` table (start_time, end_time, status).

### Task checklist — Phase 2
- [ ] `DynamicFieldComponent` + `DynamicStepFormComponent`
- [ ] `StepPaletteComponent` wired to `GET /api/step-types`
- [ ] `TEAM` + `USER_TEAM` tables, Flyway migration, default-team backfill
- [ ] Team-scoped repository queries (jobs, runs)
- [ ] Team switcher UI component
- [ ] Dark mode toggle
- [ ] Run timeline component
- [ ] E2E test: create a job using only the dynamic form + palette (no hardcoded form), run it, confirm success

**Exit criteria:** A step type registered in Phase 1 is immediately usable end-to-end in the UI with zero Angular changes. Two teams can each see only their own jobs.

---

## PHASE 3 — Workflow Flexibility (DAG, Branching, Templating)

### 3.1 Dependency model

**Schema change:**
```sql
ALTER TABLE JOB_STEP ADD COLUMN depends_on CLOB; -- JSON array of step IDs, empty = root step
ALTER TABLE JOB_STEP ADD COLUMN edge_condition VARCHAR(20) DEFAULT 'ON_SUCCESS';
  -- values: ON_SUCCESS | ON_FAILURE | ALWAYS
```
(If depends_on needs to vary per-edge rather than per-step — i.e. different condition per incoming edge — use a join table instead: `JOB_STEP_DEPENDENCY(step_id, depends_on_step_id, condition)`. Recommended over the CLOB approach if any step can have multiple upstream dependencies with different conditions.)

**Engine change:** `engine/DagExecutionEngine.java` (replaces or extends the current sequential `JobExecutionEngine`)
- Build a DAG from `JOB_STEP_DEPENDENCY` at run start.
- Topological execution: steps with satisfied dependencies (and matching edge condition against upstream result) become eligible; run eligible steps concurrently via a bounded `ExecutorService` (thread pool size configurable, default = available processors).
- A step with `ON_FAILURE` edges only runs if its dependency failed; `ALWAYS` runs regardless — this is how cleanup/notification steps get modeled.

### 3.2 Conditional execution
- UI: when adding a dependency edge (Phase 2b canvas), prompt for condition (default ON_SUCCESS).
- Engine: skip status (`SKIPPED`) recorded for steps whose dependency conditions weren't met, visible in run details/timeline.

### 3.3 Parameter templating

**New module:** `engine/template/ParamResolver.java`
- Supports `${job.param.X}` (resolved from run-time parameter map), `${step.<stepId>.output.X}` (resolved from `StepResult.outputs` of a completed upstream step), `${env.X}` (system/global env vars, existing `SYSTEM_ENV_VAR` table).
- Apply resolution to all string fields in a step's config just before `execute()` is called — keep resolution logic centralized here so every executor benefits without executor-specific changes.
- Use a simple regex-based resolver first (`\$\{([^}]+)\}`); don't over-engineer with a full expression language in v1.

**API change:** `POST /api/jobs/{id}/run` request body gains `"parameters": { "env": "staging", "date": "2026-07-25" }`.

### 3.4 Sub-workflow composition (stretch — only if time allows)
- New step type `SUB_JOB` (implemented via the Phase 1 SPI!) with config `{ jobDefinitionId, parameterMapping }`. Executing it triggers a nested run and blocks on its completion. Reuses everything from Phase 1 — good proof that the SPI investment paid off.

### Task checklist — Phase 3
- [ ] `JOB_STEP_DEPENDENCY` table + migration (backfill: convert existing linear `step_order` into a simple chain of dependencies so old jobs keep working unchanged)
- [ ] `DagExecutionEngine` with topological + concurrent execution
- [ ] Edge condition evaluation (`ON_SUCCESS` / `ON_FAILURE` / `ALWAYS`) + `SKIPPED` status
- [ ] `ParamResolver` + wiring into step execution pipeline
- [ ] `POST /api/jobs/{id}/run` accepts run-time parameters
- [ ] (Stretch) `SUB_JOB` step type
- [ ] Regression: existing linear jobs (backfilled as simple chains) still execute identically
- [ ] New test: diamond-shaped DAG (A→B, A→C, B+C→D) executes B and C concurrently, D waits for both

**Exit criteria:** A job can have two branches that run in parallel, a cleanup step that always runs, and be triggered twice with different parameters producing different (correct) outputs — without editing the job definition between runs.

---

## PHASE 4 — Job Definition Portability

### 4.1 Export
**Endpoint:** `GET /api/jobs/{id}/export?format=json|yaml`
- Serializes: job metadata, steps (with `config_json`, now safe since secrets are stored as **references** not values — confirm `JOB_CREDENTIAL` is referenced by ID/name, never inlined), dependencies, schedule, notification subscriptions (once Phase 5 exists — add this field then).
- Explicitly exclude: resolved secret values, internal DB IDs (use stable names instead, e.g. step `name` not `id`, so the export is portable across environments/DB instances).

### 4.2 Import
**Endpoint:** `POST /api/jobs/import` (multipart or raw body, `format=json|yaml`)
- Validate every step's `type` against `StepExecutorRegistry` (Phase 1) — reject with a clear error listing unknown types.
- Validate credential references exist by name in the target environment (don't silently create blank credentials).
- Conflict resolution param: `mode=create|overwrite|new_version` — default `create` if no job with that name exists, else require explicit `mode`.

### 4.3 Versioning
**Schema:**
```sql
CREATE TABLE JOB_DEFINITION_VERSION (
  id BIGINT PRIMARY KEY,
  job_definition_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  definition_snapshot CLOB NOT NULL, -- full JSON snapshot
  created_by VARCHAR(100),
  created_at TIMESTAMP,
  change_note VARCHAR(500)
);
```
- Every import or manual edit writes a new version row (snapshot-based, simplest to implement correctly vs. a diff-based approach).
- `GET /api/jobs/{id}/versions`, `POST /api/jobs/{id}/versions/{versionNumber}/rollback`.
- UI: version history tab on `/jobs/:id` with a "View diff" (simple JSON diff, e.g. `jsondiffpatch` on the frontend) and "Rollback" button.

### 4.4 CLI hook (minimal, full CLI in Phase 7)
- Not a new tool yet — just make sure `export`/`import` are trivially curl-able (stable JSON, no session-only tokens required beyond standard JWT bearer auth) so Phase 7's CLI has nothing extra to build here.

### Task checklist — Phase 4
- [ ] `GET /api/jobs/{id}/export` (JSON + YAML)
- [ ] `POST /api/jobs/import` with validation + conflict modes
- [ ] `JOB_DEFINITION_VERSION` table + migration
- [ ] Version-on-write hook (both manual edits and imports)
- [ ] `GET /api/jobs/{id}/versions`, rollback endpoint
- [ ] UI: version history tab + diff view
- [ ] Round-trip test: export → delete → import → run → identical result

**Exit criteria:** Exported job JSON/YAML can be committed to git, imported into a fresh instance (different DB, different credential IDs but same credential *names*), and runs identically.

---

## PHASE 5 — Notifications

### 5.1 SPI (mirrors Phase 1's pattern deliberately)
**File: `notification/spi/NotificationChannel.java`**
```java
public interface NotificationChannel {
    String getType(); // EMAIL | SLACK_WEBHOOK | GENERIC_WEBHOOK
    void send(NotificationEvent event, ChannelConfig config) throws NotificationException;
}
```
- `EmailNotificationChannel` (uses Spring Mail / existing SMTP config if present).
- `SlackWebhookChannel` (POST to incoming webhook URL, formatted Block Kit message).
- `GenericWebhookChannel` (POST arbitrary JSON payload to a configured URL, for custom integrations).

### 5.2 Schema
```sql
CREATE TABLE NOTIFICATION_SUBSCRIPTION (
  id BIGINT PRIMARY KEY,
  job_definition_id BIGINT NOT NULL,
  channel_type VARCHAR(30) NOT NULL,
  channel_config CLOB NOT NULL, -- JSON: webhook URL, email list, etc.
  events VARCHAR(100) NOT NULL -- comma-separated: RUN_SUCCESS,RUN_FAILURE,RUN_STARTED
);

CREATE TABLE NOTIFICATION_DELIVERY_LOG (
  id BIGINT PRIMARY KEY,
  subscription_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  status VARCHAR(20), -- SENT | FAILED | RETRYING
  attempt_count INT,
  last_error VARCHAR(1000),
  sent_at TIMESTAMP
);
```

### 5.3 Trigger wiring
- Hook into the existing run-completion event (wherever `JOB_RUN.status` transitions to `SUCCESS`/`FAILURE` — likely already an event/listener in `engine` given SSE streaming exists). Add a `NotificationDispatcher` listener alongside the SSE broadcaster.
- Async dispatch via `@Async` or a lightweight in-process queue — don't block run completion on notification delivery.
- Retry: 3 attempts with exponential backoff (1s/5s/25s) on webhook failure, logged to `NOTIFICATION_DELIVERY_LOG`.

### 5.4 UI
- New tab on `/jobs/:id`: "Notifications" — uses the **same dynamic form pattern from Phase 2.1** (channel config schema works exactly like step config schema — reuse `DynamicStepFormComponent` generically, rename to `DynamicConfigFormComponent` if helpful).
- Delivery log view (last 20 attempts per subscription, status + error).

### Task checklist — Phase 5
- [ ] `NotificationChannel` SPI + registry (reuse Phase 1's registry pattern)
- [ ] Email, Slack, generic webhook implementations
- [ ] `NOTIFICATION_SUBSCRIPTION` + `NOTIFICATION_DELIVERY_LOG` tables
- [ ] Dispatcher wired to run-completion events, async + retry
- [ ] `/api/jobs/{id}/notifications` CRUD endpoints
- [ ] UI notifications tab + delivery log
- [ ] Test: forced webhook failure triggers 3 retries then marks FAILED in delivery log

**Exit criteria:** A failing run posts to a Slack webhook within a few seconds; delivery attempts and failures are visible without checking application logs.

---

## PHASE 6 — Observability

*(No hard dependency on other phases — can be pulled forward and worked in parallel by a second contributor at any point.)*

### 6.1 Metrics
- Add `micrometer-registry-prometheus` dependency.
- Custom meters in `engine`:
  - `orchestrator.run.duration` (Timer, tagged by `jobName`, `status`)
  - `orchestrator.step.duration` (Timer, tagged by `stepType`, `status`) — ties directly to Phase 1's executor abstraction, tag by `getType()`.
  - `orchestrator.run.count` (Counter, tagged by `status`)
- Expose via existing Spring Actuator (`management.endpoints.web.exposure.include=prometheus,health`).

### 6.2 Structured logging
- Add `logstash-logback-encoder` dependency, switch `logback-spring.xml` to JSON encoder for a new appender (keep human-readable console appender for local dev, JSON for a file/stdout appender used in prod).
- MDC fields set at run/step start: `runId`, `jobId`, `stepId`, `stepType` — clear at completion.

### 6.3 Tracing
- Add `opentelemetry-spring-boot-starter` (auto-instruments Spring MVC/WebClient); manually create a span around each step execution in `DagExecutionEngine` tagged with `stepType`, `stepId`.
- Configure OTLP exporter endpoint via property (`otel.exporter.otlp.endpoint`), default to no-op if unset so this doesn't force an infra dependency on users who don't want tracing.

### 6.4 Dashboards
- `docs/observability/grafana-dashboard.json` — starter dashboard: run throughput, failure rate %, p50/p95 step duration by type, active runs gauge.
- `docs/observability/README.md` — how to point Prometheus/Grafana at the app.

### Task checklist — Phase 6
- [ ] Micrometer + Prometheus dependency, custom meters
- [ ] JSON logging appender + MDC context propagation
- [ ] OTel spans around run/step execution
- [ ] Sample Grafana dashboard JSON + setup doc
- [ ] Verify metrics survive under the Phase 3 concurrent-step execution model (tags don't collide across parallel steps)

**Exit criteria:** `/actuator/prometheus` exposes step-level latency by type; a sample Grafana dashboard renders correctly against it.

---

## PHASE 7 — API & Docs

### 7.1 OpenAPI
- Add `springdoc-openapi-starter-webmvc-ui`; annotate controllers with `@Operation`/`@Schema` where not already inferable; verify `/swagger-ui.html` renders all endpoints including the new ones from Phases 1, 4, 5.

### 7.2 Docs site
- Consolidate `../GUIDE.md`, `../SETUP_GUIDE.md`, `../USER_GUIDE.md` into a structured MkDocs (simplest, Python-based, low overhead) site under `../docs`:
  - `docs/getting-started.md`
  - `docs/step-types.md` (auto-list from registry + manual descriptions)
  - `../docs/plugin-development.md` (from Phase 1.7)
  - `docs/workflows.md` (DAG/branching/templating from Phase 3)
  - `docs/import-export.md` (Phase 4)
  - `docs/notifications.md` (Phase 5)
  - `docs/observability.md` (Phase 6)
  - `docs/api-reference.md` (links to Swagger UI)

### 7.3 CLI
**New module:** `orchestrator-cli/` (separate small Java/Picocli app, or a simple bash+curl wrapper if you want to keep it dependency-light)
- Commands: `jobs list`, `jobs run <id> [--param k=v]`, `jobs export <id>`, `jobs import <file>`, `runs tail <runId>` (consumes the existing SSE endpoint), `notifications list <jobId>`.
- Auth via the same JWT the UI uses (prompt for credentials or accept a token via env var `ORCHESTRATOR_TOKEN`).

### 7.4 Contribution readiness
- `CONTRIBUTING.md`: build instructions, how to add a step type (points at `../docs/plugin-development.md`), test running instructions, PR checklist.
- `.github/ISSUE_TEMPLATE/bug_report.md`, `feature_request.md`.
- `.github/workflows/ci.yml`: on push/PR — `mvn clean verify`, `ng test` for the Angular app, fail fast on either.

### Task checklist — Phase 7
- [ ] springdoc-openapi integrated, Swagger UI verified against all endpoints from Phases 1–6
- [ ] MkDocs site consolidating existing + new docs
- [ ] `orchestrator-cli` with the 5 commands listed above
- [ ] `CONTRIBUTING.md` + issue templates
- [ ] GitHub Actions CI pipeline (backend + frontend)

**Exit criteria:** `git clone` → documented setup → running instance → add a custom step type using only published docs, no source reading required.

---

## Cross-Phase Notes

- **Backward compatibility:** every schema change above should ship with a Flyway migration that backfills existing data into the new model (linear steps → simple dependency chains in Phase 3; old per-type columns → `config_json` in Phase 1) so existing jobs from your current batch-migration use case keep running unmodified throughout.
- **Testing discipline:** each phase's checklist ends with at least one regression test against the *previous* phase's exit criteria — this prevents the common failure mode of a later phase silently breaking an earlier one's guarantee (e.g., Phase 3's concurrency breaking Phase 1's executor thread-safety assumptions).
- **Suggested branch strategy:** one long-lived branch per phase (`feature/planv3-phase1-step-spi`, etc.), merged to `master` only when its exit criteria is demonstrably met — mirrors your existing phased approach in `../plan` and `../planv2`.