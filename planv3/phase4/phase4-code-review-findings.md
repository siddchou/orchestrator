<!-- FILE: phase4-code-review-findings.md -->
# Phase 4 — Code Review Findings

## 1. JOB_DEFINITION Schema (post Phases 1 & 3)

**Source:** `../../src/main/resources/db/migration/V1__create_job_definition.sql`, V5, V7

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| JOB_ID | NUMBER GENERATED ALWAYS AS IDENTITY | PK | Oracle identity column |
| JOB_NAME | VARCHAR2(200) | NOT NULL | UNIQUE constraint `UQ_JOB_NAME` |
| DESCRIPTION | VARCHAR2(1000) | nullable | Free-text description |
| WORKING_DIR | VARCHAR2(500) | NOT NULL | Filesystem working directory for job steps |
| JAVA_HOME | VARCHAR2(500) | nullable | Added in V5 |
| CLASSPATH | CLOB | nullable | JSON array of classpath entries, added in V5 |
| ENABLED | CHAR(1) | NOT NULL DEFAULT 'Y' | CHECK (ENABLED IN ('Y','N')) |
| CREATED_AT | TIMESTAMP | NOT NULL DEFAULT SYSTIMESTAMP | |
| UPDATED_AT | TIMESTAMP | NOT NULL DEFAULT SYSTIMESTAMP | Updated via @PreUpdate in entity |
| TEAM_ID | NUMBER | NOT NULL FK→TEAM | Added in V7, backfilled to 'Default' team |

**Entity:** `JobDefinition.java` — uses `GenerationType.IDENTITY`, Lombok `@Data/@Builder`. Has `@OneToMany` cascade relationships for steps, envVars, schedule. Team is `@ManyToOne EAGER`.

## 2. JOB_STEP Schema (post Phases 1 & 3)

**Source:** V1, V6 (relaxed constraint), V8/V9 (DAG dependencies)

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| STEP_ID | NUMBER GENERATED ALWAYS AS IDENTITY | PK | Oracle identity column |
| JOB_ID | NUMBER | NOT NULL FK→JOB_DEFINITION ON DELETE CASCADE | |
| STEP_NAME | VARCHAR2(200) | NOT NULL | Human-readable step name |
| STEP_ORDER | NUMBER | NOT NULL | Retained for backward compat; UNIQUE(JOB_ID, STEP_ORDER) |
| STEP_TYPE | VARCHAR2(50) | NOT NULL | **No CHECK constraint** (removed in V6). Validated by registry at runtime. |
| STEP_CONFIG | CLOB | nullable | JSON string — step-specific configuration |
| CONTINUE_ON_FAILURE | CHAR(1) | NOT NULL DEFAULT 'N' | CHECK ('Y','N') |
| ENABLED | CHAR(1) | NOT NULL DEFAULT 'Y' | CHECK ('Y','N') |

**Entity:** `JobStep.java` — custom getters/setters (no Lombok @Data). `stepType` is a String field with dual setter: `setStepType(StepType)` for legacy enum compat, `setStepType(String)` for dynamic types.

## 3. JOB_STEP_DEPENDENCY Schema (Phase 3)

**Source:** V8, V9 (backfill from stepOrder)

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| DEPENDENCY_ID | NUMBER GENERATED ALWAYS AS IDENTITY | PK | |
| STEP_ID | NUMBER | NOT NULL FK→JOB_STEP ON DELETE CASCADE | The dependent step |
| DEPENDS_ON_STEP_ID | NUMBER | NOT NULL FK→JOB_STEP | The prerequisite step |
| EDGE_CONDITION | VARCHAR2(20) | DEFAULT 'ON_SUCCESS' | CHECK ('ON_SUCCESS','ON_FAILURE','ALWAYS') |

**Entity:** `JobStepDependency.java` — uses `@Enumerated(EnumType.STRING)` for EdgeCondition. Both step references are `@ManyToOne LAZY`. UNIQUE(STEP_ID, DEPENDS_ON_STEP_ID).

## 4. Credential Reference Pattern (CONFIRMED)

Credentials are referenced **by name only** inside step config JSON — never inlined with resolved values.

- `JobCredential` entity has `credentialRef` (VARCHAR2(100), UNIQUE) as the stable name
- In `SftpStepExecutor.java:97`: `ctx.getCredentials().resolve(config.credentialRef())` — resolves at runtime via `CredentialResolver` functional interface
- The `CredentialResolver` in `JobExecutionOrchestrator.java:216` looks up by `credentialRef` string, then decrypts the stored value
- Step config schema defines fields as `FieldType.SECRET_REF` (e.g., SFTP's `credentialRef` field at line 68)

**Conclusion:** Export can safely serialize stepConfig JSON as-is. The `credentialRef` field inside it is just a string name like `"prod-sftp-key"`. No secret values are embedded in STEP_CONFIG.

## 5. Existing Job CRUD Endpoints

**Controller:** `JobDefinitionController.java` at `/api/jobs`

| Method | Path | Request DTO | Response DTO | Notes |
|--------|------|-------------|--------------|-------|
| GET | `/api/jobs` | page, size, search, X-Team-Id header | `ApiResponse<Page<JobDefinitionResponse>>` | Team-scoped listing |
| POST | `/api/jobs` | `JobDefinitionRequest` | `ApiResponse<JobDefinitionResponse>` (201) | Creates job only; steps added separately |
| GET | `/api/jobs/{id}` | — | `ApiResponse<JobDefinitionResponse>` | Full job with steps, envVars, schedule |
| PUT | `/api/jobs/{id}` | `JobDefinitionRequest` | `ApiResponse<JobDefinitionResponse>` | Updates job metadata only |
| DELETE | `/api/jobs/{id}` | — | void (204) | Cascade deletes steps, envVars, schedule |
| POST | `/api/jobs/{id}/enable` | — | Toggle enabled flag | |
| POST | `/api/jobs/{id}/steps` | `JobStepRequest` | `ApiResponse<JobStepResponse>` (201) | Add step |
| PUT | `/api/jobs/{id}/steps/{stepId}` | `JobStepRequest` | Update step | |
| DELETE | `/api/jobs/{id}/steps/{stepId}` | — | void (204) | Delete step + renumber |
| PUT | `/api/jobs/{id}/steps/reorder` | `StepReorderRequest` | Reorder steps | |
| GET/PUT | `/api/jobs/{id}/steps/{stepId}/dependencies` | `List<StepDependencyRequest>` / `List<StepDependencyResponse>` | DAG edges | |
| CRUD | `/api/jobs/{id}/env-vars` | `EnvVarRequest` / `EnvVarResponse` | Environment variables | |
| CRUD | `/api/jobs/{id}/schedule` | `JobScheduleRequest` / `JobScheduleResponse` | Cron schedule | |

**Key DTOs:**

- **JobDefinitionRequest:** `{jobName, description, workingDir, javaHome, classpathEntries: List<String>}` — no steps, no envVars, no schedule (those are sub-resources)
- **JobDefinitionResponse:** `{jobId, jobName, description, workingDir, javaHome, classpathEntries, enabled, createdAt, updatedAt, steps: [JobStepResponse], envVars: [EnvVarResponse], schedule: JobScheduleResponse}` — full nested representation

**Export format should be a superset of `JobDefinitionResponse` plus dependencies, minus internal DB IDs.**

## 6. YAML Library Presence

**pom.xml check:** No `jackson-dataformat-yaml` dependency present. Only `jackson-databind` is explicitly declared (Spring Boot starter-web also pulls in jackson-core and jackson-annotations).

**Action needed:** Add `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` for YAML export support. This brings in SnakeYAML as a transitive dependency.

## 7. Primary Key Generation Strategy

All tables use **Oracle identity columns**: `NUMBER GENERATED ALWAYS AS IDENTITY`. JPA mapping: `@GeneratedValue(strategy = GenerationType.IDENTITY)`.

**Implication for import:** On import, we must NOT set PK values. Let the DB generate new IDs. The export format should omit internal IDs entirely and use stable names (jobName, stepName) as identifiers.

## 8. Flyway Migration Listing

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__create_job_definition.sql` | JOB_DEFINITION, JOB_STEP, JOB_ENV_VAR |
| V2 | `V2__create_job_run.sql` | JOB_RUN, JOB_RUN_STEP |
| V3 | `V3__create_schedule_and_credential.sql` | JOB_SCHEDULE, JOB_CREDENTIAL, AUDIT_LOG |
| V4 | `V4__create_app_user.sql` | APP_USER + seed data |
| V5 | `V5__add_env_setup_to_job_definition.sql` | JAVA_HOME, CLASSPATH columns |
| V6 | `V6__relax_step_type_constraint.sql` | Removes STEP_TYPE CHECK constraint |
| V7 | `V7__add_multi_tenancy.sql` | TEAM, USER_TEAM tables + FK to JOB_DEFINITION |
| V8 | `V8__add_step_dependencies.sql` | JOB_STEP_DEPENDENCY table |
| V9 | `V9__backfill_step_dependencies.sql` | Backfills deps from stepOrder |

**Next free version: V10**

## 9. Additional Findings

- **JOB_ENV_VAR:** Stored in separate table, not nested in JobDefinitionRequest (added via sub-resource endpoint). Has `varName`, `varValue`, `isGlobal`, `jobId` FK
- **JobSchedule:** One-to-one with job. Has `cronExpression`, `enabled`, `nextFireTime`, timestamps
- **Team:** Jobs belong to a Team (V7 multi-tenancy). Export should include team name for context but import should allow team remapping
- **ApiResponse wrapper:** All endpoints return `ApiResponse<T>` — export/import should follow this pattern
- **Security:** Job endpoints require authentication (`@AuthenticationPrincipal UserDetails`). Credential endpoints require ADMIN role. Import endpoint should likely require ADMIN or at least team membership validation

## 10. [NOT FOUND IN REPO] Items

- **Phase 1 plan file** — not found as a separate document; Phase 1 changes (open step-type registry) are already implemented in the codebase
- **Phase 3 plan file** — not found as a separate document; Phase 3 changes (DAG model) are already implemented
- **Notification subscriptions** — referenced in plan ("notification subscriptions once Phase 5 exists") but no notification infrastructure exists yet. Export format should预留 a field for future notifications
