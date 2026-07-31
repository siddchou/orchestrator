<!-- FILE: phase4-02-task-breakdown.md -->
# Phase 4 — Task Breakdown

## Task 1: Add jackson-dataformat-yaml dependency

**Files Touched:** `../../pom.xml`

**Definition of Done:**
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` added to pom.xml
- Project compiles without issues

**Test to Add:** None (dependency addition only — verified by downstream tasks)

**Depends On:** Nothing

---

## Task 2: Create export DTOs

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/dto/JobExport.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ExportStep.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ExportDependency.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ExportEnvVar.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ExportSchedule.java` (new)

**Definition of Done:**
- Record classes matching the JSON schema in file 3
- No DB IDs in any export DTO field
- `format_version`, `exported_at`, `exported_from` are present on `JobExport`
- Step dependencies reference steps by name, not ID

**Test to Add:** None (DTOs are data carriers — tested via serialization in Task 3)

**Depends On:** Task 1

---

## Task 3: Create import DTOs

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/dto/JobImportRequest.java` (new) — envelope with mode + definition
- `src/main/java/com/novakai/orchestrator/api/dto/ImportStepDefinition.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ImportDependencyDefinition.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ImportEnvVarDefinition.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/ImportScheduleDefinition.java` (new)

**Definition of Done:**
- Import DTOs mirror export format but with validation annotations (`@NotBlank`, `@NotNull`, `@Size`)
- Envelope DTO has `mode` field (enum: ERROR, UPDATE, SKIP) and `definition` field
- Jakarta validation annotations on required fields

**Test to Add:** Validation unit test — malformed import request rejected with 400

**Depends On:** Task 1

---

## Task 4: Create export serializer service

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobExportImportService.java` (new)
- `../../src/main/java/com/novakai/orchestrator/api/mapper/JobDefinitionMapper.java` (modify — add toExport method)

**Definition of Done:**
- `exportJob(Long jobId)` method: loads JobDefinition with steps, deps, envVars, schedule → maps to `JobExport` DTO
- Team name resolved from FK
- StepOrder omitted from export; dependencies use step names
- `serializeToJson(JobExport)` and `serializeToYaml(JobExport)` methods using Jackson ObjectMappers
- Export returns a `JobExportResponse` record containing `{format, content: String}`

**Test to Add:** Unit test — given a JobDefinition with 3 steps and 2 dependencies, export produces correct JSON with no DB IDs

**Depends On:** Task 2

---

## Task 5: Create import validator service

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobImportValidator.java` (new)
- `src/main/java/com/novakai/orchestrator/engine/StepExecutorRegistry.java` (read — for step type validation)

**Definition of Done:**
- `validate(JobImportRequest)` method checks:
  1. `format_version` is supported (≤ "1.0")
  2. `jobName` is non-blank, ≤ 200 chars
  3. `workingDir` is non-blank
  4. Each step's `stepType` exists in the StepExecutorRegistry
  5. Each step's `stepConfig` parses as valid JSON and passes the step type's `StepConfigSchema` validation
  6. Credential references (fields with `FieldType.SECRET_REF`) resolve to existing credentials by name
  7. Dependency edges reference step names that exist in the steps array
  8. No self-referential dependencies
  9. DAG has no cycles (reuse Kahn's algorithm from JobDefinitionService)
  10. Step names are unique within the job
- Returns a list of `ImportValidationError` records (field path + message)

**Test to Add:** Unit tests for each validation rule (10 test methods, one per rule)

**Depends On:** Task 3

---

## Task 6: Implement import executor service

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobExportImportService.java` (modify — add import method)
- `../../src/main/java/com/novakai/orchestrator/api/service/JobDefinitionService.java` (modify — version hook integration point)

**Definition of Done:**
- `importJob(JobImportRequest, String username)` method:
  1. Validates via JobImportValidator
  2. Resolves conflict by mode (error/update/skip)
  3. In UPDATE mode: saves current state as a version snapshot before overwriting
  4. Creates/updates JobDefinition entity
  5. Deletes existing steps, envVars, schedule in UPDATE mode
  6. Creates new steps with sequential stepOrder assignment
  7. Resolves step names to newly created step IDs for dependency creation
  8. Creates dependencies from name-based references
  9. Creates env vars (skips global vars if non-ADMIN user, logs warning)
  10. Creates schedule (always disabled on import)
  11. Sets `enabled` to false by default on new jobs
  12. Saves version snapshot via version service (Task 8)
- Returns `JobDefinitionResponse` for the imported job

**Test to Add:** Integration test — import a valid job document, verify all entities created correctly with no DB ID leaks

**Depends On:** Task 5, Task 8

---

## Task 7: Add export endpoint to controller

**Files Touched:**
- `../../src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java` (modify)

**Definition of Done:**
- `GET /api/jobs/{id}/export?format=json|yaml` endpoint added
- Query parameter `format` defaults to `json`, accepts `json` and `yaml` (case-insensitive)
- Returns `ApiResponse<JobExportResponse>` with content-type `application/json` or `text/yaml`
- Uses bearer auth (same as other job endpoints — no extra config needed)

**Test to Add:** Integration test — create a job, export as JSON and YAML, verify both formats parse correctly

**Depends On:** Task 4

---

## Task 8: Create version history table + entity + repository

**Files Touched:**
- `src/main/resources/db/migration/V10__create_job_definition_version.sql` (new)
- `src/main/java/com/novakai/orchestrator/domain/entity/JobDefinitionVersion.java` (new)
- `src/main/java/com/novakai/orchestrator/repository/JobDefinitionVersionRepository.java` (new)

**Definition of Done:**
- Flyway migration creates JOB_DEFINITION_VERSION table (see file 5 for exact SQL)
- Entity maps to the table with JPA annotations
- Repository extends JpaRepository with `findByJobIdOrderByVersionNumberDesc()` and `findByJobIdAndVersionNumber()`

**Test to Add:** None (schema tested via integration tests in Task 12)

**Depends On:** Nothing (can run in parallel with Tasks 4-7)

---

## Task 9: Create version service

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobVersionService.java` (new)

**Definition of Done:**
- `saveVersion(Long jobId, String exportedJson, String changedBy)` — serializes current job state to export format and stores as a version row
- `listVersions(Long jobId)` — returns paginated list of versions ordered by versionNumber DESC
- `rollback(Long jobId, Long versionNumber)` — loads the version snapshot, parses it, and applies it as an import (reusing Task 6's logic but without validation since it was validated when saved)
- Version on create: after job creation in JobDefinitionService.createJob(), call saveVersion()
- Version on update: after job metadata update in JobDefinitionService.updateJob(), call saveVersion()

**Test to Add:** Unit test — save version, list versions, verify ordering and content

**Depends On:** Task 4 (needs export serializer), Task 8 (needs entity/repo)

---

## Task 10: Wire version hook into JobDefinitionService

**Files Touched:**
- `../../src/main/java/com/novakai/orchestrator/api/service/JobDefinitionService.java` (modify)

**Definition of Done:**
- Inject `JobVersionService` into `JobDefinitionService`
- After `createJob()` saves the job, call `versionService.saveVersion(jobId, username)`
- After `updateJob()` saves changes, call `versionService.saveVersion(jobId, "system")`
- After `addStep()`, `updateStep()`, `deleteStep()`, call version service (steps are part of the job definition)
- After `setDependencies()`, call version service
- After `addEnvVar()`, `deleteEnvVar()`, call version service
- After schedule CRUD, call version service

**Test to Add:** Integration test — create a job, verify v1 exists. Update a step, verify v2 exists with incremented number.

**Depends On:** Task 9

---

## Task 11: Add import endpoint to controller

**Files Touched:**
- `../../src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java` (modify)

**Definition of Done:**
- `POST /api/jobs/import` endpoint added
- Accepts `JobImportRequest` as JSON body
- Returns `ApiResponse<JobDefinitionResponse>` on success, or `ApiResponse<Void>` with 200 and skip message in SKIP mode
- Content-type: `application/json` only (YAML import via `Content-Type: text/yaml` is a nice-to-have stretch goal)

**Test to Add:** Integration test — export a job, modify nothing, re-import. Verify identical structure.

**Depends On:** Task 6

---

## Task 12: Add version listing and rollback endpoints

**Files Touched:**
- `../../src/main/java/com/novakai/orchestrator/api/controller/JobDefinitionController.java` (modify)
- `src/main/java/com/novakai/orchestrator/api/dto/JobVersionResponse.java` (new)

**Definition of Done:**
- `GET /api/jobs/{id}/versions` → `ApiResponse<List<JobVersionResponse>>` — versionNumber, changedAt, changedBy, changeDescription
- `POST /api/jobs/{id}/versions/{versionNumber}/rollback` → `ApiResponse<JobDefinitionResponse>` — restores the job to that version's state, creates a new version row for the rollback action
- Rollback validates that the requested version number exists

**Test to Add:** Integration test — create job (v1), update step (v2), rollback to v1, verify step matches v1 state and v3 was created for the rollback.

**Depends On:** Task 9, Task 10

---

## Task 13: Round-trip integration tests

**Files Touched:**
- `src/test/java/com/novakai/orchestrator/api/service/JobExportImportRoundTripTest.java` (new)

**Definition of Done:**
- Test 1: Create job with steps, deps, envVars, schedule → Export JSON → Delete job → Import JSON → Run job → Verify identical step execution results
- Test 2: Same as Test 1 but with YAML format
- Test 3: Import with mode=update on existing job → verify version history shows both states
- Test 4: Import referencing non-existent credential → verify rejected with specific error

**Test to Add:** These ARE the tests.

**Depends On:** Task 7, Task 11, Task 12

---

## Task 14: Edge case handling and polish

**Files Touched:** Multiple (see file 6 for scenarios)

**Definition of Done:**
- Import of job with step type not registered → clear error listing unknown types
- Import with cycle in dependencies → rejected (Kahn's algorithm catches this in validator)
- Export of job mid-run → works (reads definition, not run state)
- Rollback to version referencing removed step type → logged warning but version restored (step type validation skipped for rollback since it was valid when saved)
- Malformed JSON in stepConfig during export → passed through as-is string
- Import with duplicate step names within same job → rejected

**Test to Add:** One test per edge case scenario from file 6.

**Depends On:** All previous tasks

---

## Task Dependency Graph

```
Task 1 (yaml dep)
    └── Task 2 (export DTOs) ──┐
                                ├── Task 4 (export service) ──┐
                                │                              ├── Task 9 (version service) ──┬── Task 10 (wire hook)
Task 3 (import DTOs) ──────────┘                              │                             │
                                                             ├── Task 7 (export endpoint)  │
                                                             │                             │
Task 5 (validator) ────────┐                                 │                             │
                           ├── Task 6 (import executor) ─────┘                             │
                                                  │                                        │
                                                  ├── Task 11 (import endpoint)            │
                                                                                           │
Task 8 (version table) ───────────────────────────┼───────────────────────────────────────┘
                                                  │
                                                  └── Task 12 (version endpoints)

Task 13 (round-trip tests) depends on: Task 7, Task 11, Task 12
Task 14 (edge cases) depends on: All previous tasks
```
