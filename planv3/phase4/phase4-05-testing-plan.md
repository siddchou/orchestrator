<!-- FILE: phase4-05-testing-plan.md -->
# Phase 4 — Testing Plan

## Unit Tests

### Serialization (JobExportImportService)

| Test | Input | Expected Output |
|------|-------|-----------------|
| Serialize JobDefinition to JSON | Job with 3 steps, 2 deps, 1 envVar, no schedule | Valid JSON string matching schema. No DB IDs present. `format_version: "1.0"`. Step dependencies use step names. Schedule key omitted (null). |
| Serialize JobDefinition to YAML | Same as above | Valid YAML string. When parsed back with Jackson YAML mapper, produces equivalent object graph. |
| Deserialize JSON export | JSON from first test | `JobExport` record with all fields populated. No NPE on null schedule. |
| Deserialize YAML export | YAML from second test | `JobExport` record matching JSON deserialization result. |
| Serialize empty stepConfig | Step with `stepConfig: null` | Export contains `"stepConfig": null` (not omitted, since it's a meaningful field). |

### Import validation (JobImportValidator)

| Test | Input | Expected Output |
|------|-------|-----------------|
| Valid import document | Well-formed job with registered step types | Empty error list. |
| Unknown step type | `stepType: "nonexistent_type"` | Error on steps[0].stepType: "No executor registered for 'nonexistent_type'" |
| Missing job name | `jobName: ""` | Error on definition.jobName: "Job name is required" |
| Job name too long | `jobName: <201 chars>` | Error on definition.jobName: "Job name must be 200 characters or fewer" |
| Duplicate step names | Two steps named "build" | Error: "Duplicate step name: 'build'" |
| Cycle in dependencies | A→B→C→A edges | Error: "Cycle detected in step dependencies" |
| Self-referential dependency | Step "X" depends on "X" | Error: "Step 'X' cannot depend on itself" |
| Dependency references missing step | Edge points to non-existent step name | Error: "Dependency references unknown step 'compile'" |
| Missing credential reference | Step config has `credentialRef` for non-existent credential | Error listing unresolved credential names |
| Empty steps array | `steps: []` | Error: "Job must have at least one step" |
| Future format version | `format_version: "2.0"` | Error: "Unsupported format version '2.0'" |
| Invalid JSON in stepConfig | `stepConfig: "{bad json}"` — wait, this is allowed per edge case 4 | **No error** — stepConfig is treated as opaque string during import validation. Validation of step config structure is deferred to the step type's schema check only if parseable. |

### Version service (JobVersionService)

| Test | Input | Expected Output |
|------|-------|-----------------|
| Save first version | New job ID, no prior versions | VERSION_NUMBER = 1 row created. EXPORT_CONTENT contains valid JSON snapshot. |
| Save second version | Same job ID, existing v1 | VERSION_NUMBER = 2 row created. Monotonically increasing. |
| List versions | Job with 3 versions | Returns [v3, v2, v1] (descending order). |
| Rollback to v1 | Job at v3, rollback to v1 | Job state matches v1 snapshot. New version row created (v4) for the rollback action. |
| Rollback non-existent version | Request v99 on job with only 2 versions | Returns error or throws `JobVersionNotFoundException`. |

## Integration Tests

### Export endpoint (GET /api/jobs/{id}/export)

| Test | Setup | Expected |
|------|-------|----------|
| Export as JSON (default) | Job with steps, deps, envVars | 200 OK. Content-Type: application/json. Body parses to valid `JobExport`. No DB IDs in output. |
| Export as YAML | Same job | 200 OK. Content-Type: text/yaml. Body parses to equivalent object graph. |
| Export non-existent job | Invalid job ID | 404 Not Found. |
| Export with format=xml (invalid) | `?format=xml` | 400 Bad Request with "Unsupported format 'xml'. Supported: json, yaml". |

### Import endpoint (POST /api/jobs/import)

| Test | Setup | Expected |
|------|-------|----------|
| Import new job (mode=error) | Document with unique job name | 201 Created. Job exists in DB with correct steps, deps, envVars. Enabled=false. Schedule disabled. Version v1 created. |
| Import existing job (mode=update) | Document matches existing job name | 200 OK. Job updated. Old state saved as version before overwrite. New version created. Steps replaced atomically. |
| Import existing job (mode=skip) | Document matches existing job name, mode=skip | 200 OK with skip message. No changes to DB. Response body contains existing job's info. |
| Import existing job (no mode specified) | Default mode is ERROR | 409 Conflict. Message instructs user to use mode=update. |
| Import with validation errors | Document with unknown step type | 400 Bad Request. Error list in response body. No partial state written. |

### Version endpoints

| Test | Setup | Expected |
|------|-------|----------|
| List versions on new job | Job just created, no updates | Returns [v1] — one version from creation hook. |
| List versions after update | Job updated 3 times | Returns [v4, v3, v2, v1]. Each has distinct CHANGED_AT timestamp. |
| Rollback to previous version | Job at v3, rollback to v2 | Job state matches v2 snapshot. v4 created for the rollback action. CHANGED_BY set to requesting user. |

## Round-Trip Regression Test (Exit Criteria)

This is the definitive test that validates Phase 4's exit criteria: **"Exported JSON can be committed to git and imported into a fresh instance."**

### Test procedure

```
Given a running orchestrator instance with H2 test database:

1. CREATE — Create a job via API:
   - POST /api/jobs → {jobName: "round_trip_test", workingDir: "/opt/job"}
   - PUT /api/jobs/{id}/steps → add 3 steps (different types)
   - PUT /api/jobs/{id}/dependencies → add dependency edges between steps
   - PUT /api/jobs/{id}/env-vars → add 2 env vars
   - PUT /api/jobs/{id}/schedule → set a cron schedule

2. RUN ORIGINAL — Trigger the job and capture execution result:
   - POST /api/runs/{jobId}/start
   - Wait for completion
   - Record step execution order, statuses, durations

3. EXPORT — Export as JSON:
   - GET /api/jobs/{id}/export?format=json → save content to file

4. DELETE — Delete the job:
   - DELETE /api/jobs/{id}
   - Verify job no longer exists (GET returns 404)

5. IMPORT — Import from exported JSON:
   - POST /api/jobs/import with saved content
   - Capture new job ID from response

6. VERIFY STRUCTURE — Compare imported job against export:
   - GET /api/jobs/{newId} → compare steps count, types, names
   - Verify dependency edges match (by step name)
   - Verify env vars match (name/value pairs)
   - Verify schedule matches (cron expression, timezone)

7. RUN IMPORTED — Trigger the imported job:
   - POST /api/runs/{newId}/start
   - Wait for completion
   - Record step execution order, statuses, durations

8. ASSERT IDENTICAL RESULTS:
   - Step execution order matches original run
   - All steps complete successfully (same exit codes)
   - No unexpected errors in step logs
```

### Same test with YAML format

Repeat steps 3-8 using `?format=yaml` for export and `Content-Type: text/yaml` for import.

### Cross-instance simulation

To simulate the "fresh instance" scenario without a second server:

1. Export job A as JSON to file
2. Truncate all job-related tables (JOB_DEFINITION_VERSION, JOB_STEP_DEPENDENCY, JOB_ENV_VAR, JOB_SCHEDULE, JOB_STEP, JOB_DEFINITION)
3. Import from saved JSON file
4. Verify structure matches export

This simulates the git-commit → fresh-import workflow by clearing state between export and import.

## Test coverage summary

| Category | Count | Scope |
|----------|-------|-------|
| Unit — serialization | 5 | Export/import format conversion |
| Unit — validation | 12 | Each validation rule in isolation |
| Unit — version service | 5 | Save, list, rollback CRUD |
| Integration — export endpoint | 4 | HTTP contract + content negotiation |
| Integration — import endpoint | 5 | All conflict modes + atomicity |
| Integration — version endpoints | 3 | Listing + rollback |
| Round-trip regression | 2 | JSON and YAML full lifecycle |
| **Total** | **36** | |

## Test data fixtures

Store reusable export documents in `src/test/resources/fixtures/`:

```
fixtures/
├── valid_job_export.json       — Complete job with all sections
├── valid_job_export.yaml       — Same content in YAML
├── minimal_job_export.json     — Job with 1 step, no deps/envVars/schedule
├── unknown_step_type.json      — Step references non-existent type
├── cyclic_dependencies.json    — A→B→C→A cycle
├── duplicate_step_names.json   — Two steps named "build"
└── future_format_version.json  — format_version: "99.0"
```

These fixtures are used by both unit tests (validation) and integration tests (import endpoint).
