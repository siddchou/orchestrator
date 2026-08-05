<!-- FILE: phase4-code-review-findings.md -->
# Phase 4 — Code Review Findings

**Date:** 2026-07-31
**Status:** Phase 4 is **already fully implemented**. This document records the current state of the codebase as discovered via Graphify and source code review.

---

## 1. Schema: JOB_DEFINITION, JOB_STEP, JOB_STEP_DEPENDENCY

### JOB_DEFINITION

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| JOB_ID | NUMBER GENERATED ALWAYS AS IDENTITY | PK | Identity column (Oracle) |
| JOB_NAME | VARCHAR2(200) | UNIQUE NOT NULL | `UQ_JOB_NAME` constraint |
| DESCRIPTION | VARCHAR2(1000) | nullable | Free-text description |
| WORKING_DIR | VARCHAR2(500) | NOT NULL | Working directory for steps |
| JAVA_HOME | VARCHAR2(500) | nullable | Added in V5 |
| CLASSPATH | CLOB | nullable | JSON array of classpath entries (V5) |
| ENABLED | CHAR(1) | DEFAULT 'Y', CHECK ('Y','N') | Boolean flag |
| TEAM_ID | NUMBER | FK → teams.TEAM_ID, NOT NULL | EAGER-loaded `@ManyToOne` (V7) |
| CREATED_AT | TIMESTAMP | DEFAULT SYSTIMESTAMP | Auto-set on insert |
| UPDATED_AT | TIMESTAMP | DEFAULT SYSTIMESTAMP | Updated via `@PreUpdate` |

**Entity:** `JobDefinition.java` — uses Lombok `@Data`, JPA `@Entity`. Has `@OneToMany(cascade=ALL, orphanRemoval=true)` for steps and envVars.

### JOB_STEP

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| STEP_ID | NUMBER GENERATED ALWAYS AS IDENTITY | PK | Identity column |
| JOB_ID | NUMBER | FK → job_definition.JOB_ID, ON DELETE CASCADE | |
| STEP_NAME | VARCHAR2(200) | NOT NULL | Human-readable step name |
| STEP_ORDER | NUMBER | NOT NULL | Retained for backward compat; UNIQUE(JOB_ID, STEP_ORDER) |
| STEP_TYPE | VARCHAR2(50) | NOT NULL | No CHECK constraint (removed V6). Validated by registry at runtime. |
| STEP_CONFIG | CLOB | nullable | JSON configuration blob |
| CONTINUE_ON_FAILURE | CHAR(1) | DEFAULT 'N', CHECK ('Y','N') | |
| ENABLED | CHAR(1) | DEFAULT 'Y', CHECK ('Y','N') | |

**Entity:** `JobStep.java` — custom getters/setters (no Lombok `@Data`). Has dual setter: `setStepType(StepType)` for legacy enum compatibility and `setStepType(String)` for dynamic registry types.

### JOB_STEP_DEPENDENCY

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| DEPENDENCY_ID | NUMBER GENERATED ALWAYS AS IDENTITY | PK | Identity column |
| STEP_ID | NUMBER | FK → job_step.STEP_ID, ON DELETE CASCADE | The dependent step |
| DEPENDS_ON_STEP_ID | NUMBER | FK → job_step.STEP_ID | The prerequisite step |
| EDGE_CONDITION | VARCHAR2(20) | DEFAULT 'ON_SUCCESS', CHECK ('ON_SUCCESS','ON_FAILURE','ALWAYS') | |

Unique constraint: `(STEP_ID, DEPENDS_ON_STEP_ID)`.

**Entity:** `JobStepDependency.java` — uses `@Enumerated(EnumType.STRING)` for EdgeCondition. Both step references are `@ManyToOne LAZY`.

---

## 2. Credential Reference Pattern

Credentials are **referenced by name only**, never inlined with resolved values.

- `JobCredential` entity has `credentialRef` (VARCHAR2(100), UNIQUE) as the stable name
- In step executors: `ctx.getCredentials().resolve(config.credentialRef())` — resolves at runtime via `CredentialResolver` functional interface
- The `CredentialResolver` in `JobExecutionOrchestrator.java:216` looks up by `credentialRef` string, then decrypts the stored value
- Step config schema defines fields as `FieldType.SECRET_REF` (e.g., SFTP's `credentialRef` field)

**Conclusion:** Export can safely serialize stepConfig JSON as-is. The `credentialRef` field inside it is just a string name like `"prod-sftp-key"`. No secret values are embedded in STEP_CONFIG.

---

## 3. Job CRUD REST Endpoints

All endpoints are in `JobDefinitionController.java` at `/api/jobs`:

| Method | Path | Handler | Description |
|--------|------|---------|-------------|
| GET | `/` | `listJobs()` | Paginated job list (team-scoped) |
| POST | `/` | `createJob()` | Create new job |
| GET | `/{id}` | `getJob()` | Get job by ID |
| PUT | `/{id}` | `updateJob()` | Update job metadata |
| DELETE | `/{id}` | `deleteJob()` | Delete job (cascade) |
| POST | `/{id}/enable` | `enableJob()` | Toggle enabled flag |
| **GET** | `/{id}/export` | `exportJson()` | **Export job as JSON/YAML** (line 121) |
| **POST** | `/import` | `importJob()` | **Import job from JSON** (line 133) |
| POST | `/{id}/steps` | `addStep()` | Add step to job |
| PUT | `/{id}/steps/{stepId}` | `updateStep()` | Update step |
| DELETE | `/{id}/steps/{stepId}` | `deleteStep()` | Delete step + renumber |
| PUT | `/{id}/steps/reorder` | `reorderSteps()` | Reorder steps (legacy) |
| GET/PUT | `/{id}/steps/{stepId}/dependencies` | DAG edges | Step dependencies |
| CRUD | `/{id}/env-vars` | Environment variables | |
| CRUD | `/{id}/schedule` | Schedule config | |
| **GET** | `/{id}/versions` | `listVersions()` | **List version history** (line 278) |
| **GET** | `/{id}/versions/{ver}` | `getVersion()` | **Get specific version JSON** (line 289) |
| **POST** | `/{id}/versions/{ver}/rollback` | `rollbackToVersion()` | **Rollback to version** (line 294) |

Bold entries are Phase 4-specific endpoints. All return `ApiResponse<T>` wrapper.

---

## 4. YAML Serialization Library

**Present.** `jackson-dataformat-yaml` is in `pom.xml` at line 91. The `JobExportImportService` uses both JSON and YAML serialization:
- `exportToJson()` — uses `ObjectMapper` with `SerializationFeature.INDENT_OUTPUT`
- `exportToYaml()` — uses `YAMLMapper` (subclassed from ObjectMapper)

---

## 5. Primary Key Generation Strategy

**`GenerationType.IDENTITY`** across all entities. Oracle uses `NUMBER GENERATED ALWAYS AS IDENTITY`. This means:
- IDs are assigned by the database on insert
- Imported jobs that specify a `jobId` must have it overridden (new ID assigned on target)
- Export format includes `jobId` for reference, but import ignores it and assigns a new one

---

## 6. Flyway Migration Versions

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_job_definition.sql` | JOB_DEFINITION, JOB_STEP, JOB_ENV_VAR |
| V2 | `V2__create_job_run.sql` | JOB_RUN, JOB_RUN_STEP |
| V3 | `V3__create_schedule_and_credential.sql` | JOB_SCHEDULE, JOB_CREDENTIAL, AUDIT_LOG |
| V4 | `V4__create_app_user.sql` | APP_USER + seed data |
| V5 | `V5__add_env_setup_to_job_definition.sql` | JAVA_HOME, CLASSPATH columns |
| V6 | `V6__relax_step_type_constraint.sql` | Removes STEP_TYPE CHECK constraint |
| V7 | `V7__add_multi_tenancy.sql` | TEAM, USER_TEAM tables + FK to JOB_DEFINITION |
| V8 | `V8__add_step_dependencies.sql` | JOB_STEP_DEPENDENCY table |
| V9 | `V9__backfill_step_dependencies.sql` | Backfills deps from stepOrder |
| **V10** | `V10__add_job_definition_version.sql` | **JOB_DEFINITION_VERSION table (Phase 4)** |

**Next free version: V11**

---

## 7. Implementation Status Summary

| Planned Artifact | Status | Location |
|-----------------|--------|----------|
| Export DTOs (ExportStep, ExportDependency, etc.) | ✅ Implemented | `api/dto/` |
| Import DTOs (ImportStepDefinition, etc.) | ✅ Implemented | `api/dto/` |
| JobExportImportService | ✅ Implemented (~590 lines) | `api/service/JobExportImportService.java` |
| Export endpoint GET /{id}/export | ✅ Implemented | `JobDefinitionController:121` |
| Import endpoint POST /import | ✅ Implemented | `JobDefinitionController:133` |
| JobDefinitionVersion entity | ✅ Implemented | `domain/entity/JobDefinitionVersion.java` |
| JobDefinitionVersionRepository | ✅ Implemented | `repository/` |
| JobVersionService | ✅ Implemented (146 lines) | `api/service/JobVersionService.java` |
| Version listing endpoint | ✅ Implemented | `JobDefinitionController:278` |
| Version rollback endpoint | ✅ Implemented | `JobDefinitionController:294` |
| V10 migration | ✅ Implemented | `db/migration/V10__add_job_definition_version.sql` |
| Version hook in JobDefinitionService | ✅ Wired (15 call sites) | `JobDefinitionService.java` |
| jackson-dataformat-yaml dependency | ✅ Added | `pom.xml:91` |
| Round-trip integration tests | ✅ 11 tests | `JobExportImportRoundTripTest.java` |

---

## 8. Discrepancies Between Plan and Implementation

| Planned | Actual | Impact |
|---------|--------|--------|
| Import request envelope with nested `{mode, definition}` structure | Import request is flat — `JobImportRequest` has `mode`, `jobName`, `steps`, etc. as top-level fields (no nested `definition`) | **Minor** — simpler to use from CLI, but differs from planned format |
| Export omits `jobId` entirely | Export includes `jobId` in the output for provenance | **Low** — import ignores it, so portability is preserved |
| Import validates credentials against target instance | Validator checks credential refs exist, but implementation detail may differ slightly | Needs verification against actual validator code |
| Schedule always disabled on import | Implemented as planned | ✅ Matches |
| `notifications` field reserved in export | Not present in current DTOs | **Low** — Phase 5 hasn't started; can be added then |
| Planned: `JobImportValidator` as separate service | Validation logic is embedded in `JobExportImportService.validateImport()` | **Architectural difference** — validation is a method, not a standalone bean |
| Planned: `JobDefinitionMapper` with `toExport` method | Mapping done inline in `JobExportImportService.buildExport()` | **Minor** — no separate mapper class |

---

## 9. Additional Findings

- **JOB_ENV_VAR:** Stored in separate table. Has `varName`, `varValue`, `isGlobal`, `jobId` FK
- **JobSchedule:** One-to-one with job. Has `cronExpression`, `enabled`, `nextFireTime`, timestamps
- **Team:** Jobs belong to a Team (V7 multi-tenancy). Export includes team name for context; import allows team remapping via `teamName` field
- **Security:** Job endpoints require authentication (`@AuthenticationPrincipal UserDetails`)
- **Version service uses truncation:** `JobVersionService.truncateExportJson()` caps at ~1M UTF-8 chars to prevent CLOB overflow
