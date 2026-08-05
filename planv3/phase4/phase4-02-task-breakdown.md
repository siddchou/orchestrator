<!-- FILE: phase4-02-task-breakdown.md -->
# Phase 4 — Task Breakdown

**Status:** All tasks **completed**. This document records what was implemented, where, and how it maps to the original task breakdown.

---

## Task 1: Add jackson-dataformat-yaml dependency ✅

**Files Touched:** `pom.xml` (line 91)

**As Built:**
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` added to pom.xml
- Project compiles without issues

**Test to Add:** None (dependency addition only — verified by downstream tasks)

**Depends On:** Nothing

---

## Task 2: Create export DTOs ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/dto/JobExport.java` — record with `formatVersion`, `exportedAt`, `exportedFrom`, `jobId`, `jobName`, `description`, `workingDir`, `javaHome`, `classpathEntries`, `enabled`, `teamName`, `steps`, `dependencies`, `envVars`, `schedule`, `metadata`. Uses `@JsonInclude(NON_NULL)`.
- `src/main/java/com/novakai/orchestrator/api/dto/ExportStep.java` — record with `stepName`, `stepOrder`, `stepType`, `stepConfig` (Object), `continueOnFailure`, `enabled`
- `src/main/java/com/novakai/orchestrator/api/dto/ExportDependency.java` — record with `stepName`, `dependsOnStepName`, `edgeCondition`
- `src/main/java/com/novakai/orchestrator/api/dto/ExportEnvVar.java` — record with `key`, `value`, `isGlobal`
- `src/main/java/com/novakai/orchestrator/api/dto/ExportSchedule.java` — record with `cronExpression`, `enabled`

**Deviation from plan:**
- `jobId` is included (plan said to omit)
- `stepOrder` is retained (plan said to omit post-DAG)
- `stepConfig` is `Object` not `String` (parsed JSON tree, not opaque string)
- `metadata` field added for user-defined key-value pairs

**Test to Add:** None (DTOs are data carriers — tested via serialization in Task 4)

**Depends On:** Task 1

---

## Task 3: Create import DTOs ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/dto/JobImportRequest.java` — flat record with `mode`, `formatVersion`, `jobId`, `jobName`, `description`, `workingDir`, `javaHome`, `classpathEntries`, `enabled`, `teamName`, `steps`, `dependencies`, `envVars`, `schedule`, `metadata`. Has inner `Mode` enum (ERROR, UPDATE, SKIP) and `modeEnum()` helper. Uses `@JsonInclude(NON_NULL)` and `@NotBlank` on mode.
- `src/main/java/com/novakai/orchestrator/api/dto/ImportStepDefinition.java` — mirrors ExportStep with wrapper types (`Integer`, `Boolean`) for nullable fields. Uses `@JsonInclude(USE_DEFAULTS)`.
- `src/main/java/com/novakai/orchestrator/api/dto/ImportDependencyDefinition.java`
- `src/main/java/com/novakai/orchestrator/api/dto/ImportEnvVarDefinition.java`
- `src/main/java/com/novakai/orchestrator/api/dto/ImportScheduleDefinition.java`

**Deviation from plan:** Import request is flat (no nested `{mode, definition}` envelope). All job fields sit at the top level alongside `mode`.

**Test to Add:** Validation unit test — malformed import request rejected with 400

**Depends On:** Task 1

---

## Task 4: Create export serializer service ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobExportImportService.java` — contains both export and import logic in one service. Key methods:
  - `exportToJson(Long jobId)` — loads job, calls `buildExport()`, serializes with indented ObjectMapper
  - `exportToYaml(Long jobId)` — same but with YAMLMapper
  - `buildExport()` — maps JobDefinition entity + steps + deps + envVars + schedule to JobExport DTO. Resolves team name from FK. Parses stepConfig CLOB into ObjectNode for nested representation.

**Deviation from plan:** No separate `JobDefinitionMapper` class — mapping is done inline in `buildExport()`. Step config is parsed (not passed through as string).

**Test to Add:** Unit test — given a JobDefinition with 3 steps and 2 dependencies, export produces correct JSON with no DB IDs in step references

**Depends On:** Task 2

---

## Task 5: Create import validator service ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobExportImportService.java` — validation is a method within the export/import service, not a separate bean. Key method:
  - `validateImport(JobImportRequest request, Boolean jobExists)` — returns `List<String>` of error messages. Checks:
    1. `formatVersion` is supported
    2. `jobName` is non-blank
    3. Each step's `stepType` exists in the StepExecutorRegistry
    4. Credential references resolve to existing credentials by name
    5. Dependency edges reference step names that exist in the steps array
    6. No self-referential dependencies
    7. DAG has no cycles (DFS-based cycle detection)
    8. Cron expression validation for schedule

**Deviation from plan:** Validation is embedded in `JobExportImportService` rather than a separate `JobImportValidator` bean. Uses DFS for cycle detection (plan mentioned Kahn's algorithm). Returns string error messages, not structured `ImportValidationError` records.

**Test to Add:** Unit tests for each validation rule

**Depends On:** Task 3

---

## Task 6: Implement import executor service ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobExportImportService.java` — `importJob(JobImportRequest request, Long teamId)` method:
  1. Resolves conflict by mode (error/update/skip) via `resolveConflict()`
  2. In UPDATE mode: deletes existing steps, deps, envVars, schedule before recreating
  3. Creates/updates JobDefinition entity
  4. Creates new steps with sequential stepOrder assignment
  5. Resolves step names to newly created step IDs for dependency creation
  6. Creates dependencies from name-based references
  7. Creates env vars
  8. Creates schedule (always disabled on import)

**Test to Add:** Integration test — import a valid job document, verify all entities created correctly with no DB ID leaks

**Depends On:** Task 5, Task 8

---

## Task 7: Add export endpoint to controller ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java` (line 121)

**As Built:**
- `GET /api/jobs/{id}/export?format=json|yaml` endpoint added
- Query parameter `format` defaults to `json`, accepts `json` and `yaml` (case-insensitive)
- Returns `ApiResponse<String>` — raw serialized content (not wrapped in a DTO)

**Deviation from plan:** Returns `ApiResponse<String>` with raw content, not `ApiResponse<JobExportResponse>` with `{format, content}` structure. Content-Type is always application/json regardless of format parameter — the format is embedded in the body, not signaled via header.

**Test to Add:** Integration test — create a job, export as JSON and YAML, verify both formats parse correctly

**Depends On:** Task 4

---

## Task 8: Create version history table + entity + repository ✅

**Files Touched:**
- `src/main/resources/db/migration/V10__add_job_definition_version.sql` — creates JOB_DEFINITION_VERSION table with VERSION_ID (identity PK), JOB_ID FK, VERSION_NUMBER, EXPORT_JSON CLOB, VERSION_LABEL, CREATED_AT, CREATED_BY. Unique index on (JOB_ID, VERSION_NUMBER).
- `src/main/java/com/novakai/orchestrator/domain/entity/JobDefinitionVersion.java` — JPA entity with Lombok `@Data/@Builder`. Fields: versionId, jobId, versionNumber, exportJson, versionLabel, createdAt, createdBy.
- `src/main/java/com/novakai/orchestrator/repository/JobDefinitionVersionRepository.java` — extends JpaRepository with `findByJobIdOrderByVersionNumberDesc()`, `findByJobIdAndVersionNumber()`, `findTopByJobIdOrderByVersionNumberDesc()`, `countByJobId()`.

**Deviation from plan:** Column named `EXPORT_JSON` (not `EXPORT_CONTENT`). Field named `versionLabel` (not `changeDescription`). No `CHANGE_DESC` column.

**Test to Add:** None (schema tested via integration tests)

**Depends On:** Nothing (can run in parallel with Tasks 4-7)

---

## Task 9: Create version service ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobVersionService.java` (146 lines) — methods:
  - `saveVersion(Long jobId, String changedBy)` — exports current job state to JSON, auto-increments version number per job, stores as version row. Includes truncation at ~1M chars for CLOB safety.
  - `listVersions(Long jobId)` — returns versions ordered by versionNumber DESC
  - `getVersion(Long jobId, Integer versionNumber)` — throws if not found
  - `getLatestVersion(Long jobId)` — throws if no versions exist
  - `exportVersion(Long jobId, Integer versionNumber)` — returns stored JSON string
  - `rollbackToVersion(Long jobId, Integer versionNumber, Long teamId, String changedBy)` — parses stored export JSON into JobImportRequest with UPDATE mode, re-imports, creates new version row for rollback action
  - `deleteVersionsForJob(Long jobId)` — called on job delete

**Deviation from plan:** Rollback reuses the import path (parses stored JSON → JobImportRequest → importJob) rather than having a dedicated restore method. Version label stores the `changedBy` username, not a separate change description.

**Test to Add:** Unit test — save version, list versions, verify ordering and content

**Depends On:** Task 4 (needs export serializer), Task 8 (needs entity/repo)

---

## Task 10: Wire version hook into JobDefinitionService ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobDefinitionService.java` — 15 call sites where `versionService.saveVersion(jobId, username)` is called after mutating operations.

**As Built:** Version hooks fire after: create job, update job, delete job (deletes versions), add step, update step, delete step, set dependencies, add env var, delete env var, create schedule, update schedule, delete schedule, enable/disable schedule, import, rollback.

**Test to Add:** Integration test — create a job, verify v1 exists. Update a step, verify v2 exists with incremented number.

**Depends On:** Task 9

---

## Task 11: Add import endpoint to controller ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java` (line 133)

**As Built:**
- `POST /api/jobs/import` endpoint added
- Accepts `@Valid JobImportRequest` as JSON body
- Checks for existing job by name before calling service
- In UPDATE mode, saves pre-update version snapshot
- Returns `ApiResponse<JobDefinitionResponse>` on success

**Test to Add:** Integration test — export a job, modify nothing, re-import. Verify identical structure.

**Depends On:** Task 6

---

## Task 12: Add version listing and rollback endpoints ✅

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java` (lines 278, 289, 294)
- `src/main/java/com/novakai/orchestrator/api/dto/JobVersionSummary.java` — record with `versionNumber`, `versionLabel`, `changedAt`, `changedBy`

**As Built:**
- `GET /api/jobs/{id}/versions` → `ApiResponse<List<JobVersionSummary>>`
- `GET /api/jobs/{id}/versions/{versionNumber}` → `ApiResponse<String>` — raw export JSON
- `POST /api/jobs/{id}/versions/{versionNumber}/rollback` → `ApiResponse<JobDefinitionResponse>`

**Deviation from plan:** Version detail endpoint returns raw JSON string, not a structured DTO. Rollback requires `@AuthenticationPrincipal UserDetails` for the `changedBy` field.

**Test to Add:** Integration test — create job (v1), update step (v2), rollback to v1, verify step matches v1 state and v3 was created for the rollback.

**Depends On:** Task 9, Task 10

---

## Task 13: Round-trip integration tests ✅

**Files Touched:**
- `src/test/java/com/novakai/orchestrator/api/service/JobExportImportRoundTripTest.java` (547 lines, 11 tests)

**Tests Implemented:**
1. Full round-trip: export JSON → delete job → import JSON → verify structure
2. YAML export round-trip
3. UPDATE mode versioning — import updates existing job, creates new version
4. ERROR mode rejection — importing duplicate job name without update mode returns 409
5. Version listing — verifies versions are created and ordered correctly
6. Rollback — rollback to previous version restores state
7. Get specific version — retrieves stored export JSON for a given version number
8. Unknown step type rejection — import with unregistered step type fails validation
9. Circular dependency detection — DFS-based cycle detection rejects cyclic imports
10. Invalid cron expression rejection — schedule validation catches bad cron expressions
11. Environment variable preservation — round-trip preserves env var key/value/isGlobal

**Test to Add:** These ARE the tests.

**Depends On:** Task 7, Task 11, Task 12

---

## Task 14: Edge case handling and polish ✅ (partial)

**Files Touched:** Multiple (see edge cases document for scenarios)

**Implemented:**
- Import of job with step type not registered → clear error listing unknown types ✅
- Import with cycle in dependencies → rejected via DFS ✅
- Export of job mid-run → works (reads definition, not run state) ✅
- Malformed JSON in stepConfig during export → **will fail** (stepConfig is parsed as ObjectNode, not passed through as string) ⚠️
- Import with duplicate step names within same job → rejected ✅

**Not explicitly handled:**
- Rollback to version referencing removed step type → reuses import path, so would fail if step type is unregistered. Plan said to skip validation for rollback. ⚠️
- Global env var import by non-ADMIN user → needs verification in current code ⚠️
- Concurrent import of same job name → relies on UNIQUE constraint + transaction isolation ⚠️

**Depends On:** All previous tasks

---

## Task Dependency Graph

```
Task 1 (yaml dep) ✅
    └── Task 2 (export DTOs) ✅ ──┐
                                   ├── Task 4 (export service) ✅ ──┐
                                   │                                 ├── Task 9 (version service) ✅ ──┬── Task 10 (wire hook) ✅
Task 3 (import DTOs) ✅ ──────────┘                                 │                                │
                                                                    ├── Task 7 (export endpoint) ✅  │
                                                                    │                                │
Task 5 (validator) ✅ ────────┐                                     │                                │
                              ├── Task 6 (import executor) ✅ ──────┘                                │
                                                     │                                                │
                                                     ├── Task 11 (import endpoint) ✅                 │
                                                                                                      │
Task 8 (version table) ✅ ───────────────────────────┼───────────────────────────────────────────────┘
                                                     │
                                                     └── Task 12 (version endpoints) ✅

Task 13 (round-trip tests) ✅ depends on: Task 7, Task 11, Task 12
Task 14 (edge cases) ⚠️ partial depends on: All previous tasks
```
