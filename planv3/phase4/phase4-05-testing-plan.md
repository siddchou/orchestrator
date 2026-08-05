<!-- FILE: phase4-05-testing-plan.md -->
# Phase 4 — Testing Plan

## Current Test Coverage

**One integration test class exists:** `JobExportImportRoundTripTest` (547 lines, 11 tests)

All tests use a full Spring Boot context with H2 database (`@SpringBootTest`, `@ActiveProfiles("test")`). No unit tests exist for the export/import service or version service.

### Implemented Integration Tests

| # | Test Method | What It Covers | Status |
|---|------------|----------------|--------|
| 1 | `fullRoundTrip_export_delete_import` | Creates job with steps + dependency + schedule → exports JSON → deletes job → re-imports via `JobImportRequest(mode=ERROR)` → re-exports to verify step names preserved | ✅ Complete |
| 2 | `exportYaml_format_parses_correctly` | Exports as YAML, verifies response contains expected values | ✅ Complete (read-only; no YAML import test) |
| 3 | `importUpdateMode_createsVersion` | Creates job → imports with mode=UPDATE (different step) → verifies version count increased and job fields updated | ✅ Complete |
| 4 | `importErrorMode_onExistingJob_rejects` | Creates job → attempts import with mode=ERROR → asserts non-201 response | ✅ Complete |
| 5 | `versionListing_returnsVersions` | Creates job with steps → GET /versions → verifies response contains version data | ✅ Complete |
| 6 | `rollbackToVersion_restoresState` | Creates job → adds step (2 steps) → rolls back to v1 → verifies new version created for rollback action | ⚠️ Partial — does not verify restored state matches v1 snapshot, only that a new version was created |
| 7 | `getVersion_returnsVersionJson` | GET /versions/{n} → verifies JSON contains job name | ✅ Complete (shallow) |
| 8 | `importValidation_rejectsUnknownStepType` | Import with `NONEXISTENT_TYPE` step type → asserts rejection | ✅ Complete |
| 9 | `importValidation_rejectsCircularDeps` | Creates A→B→C→A cycle in dependencies → asserts rejection | ✅ Complete |
| 10 | `importValidation_rejectsInvalidCron` | Import with `"not-a-valid-cron"` schedule → asserts rejection | ✅ Complete |
| 11 | `exportImport_preservesEnvVars` | Creates job → exports → deletes → re-imports with env vars (including global) → re-exports and verifies both env vars present | ✅ Complete |

## Missing Tests

### Critical Gaps

| # | Missing Test | Why It Matters | Priority |
|---|-------------|----------------|----------|
| M1 | **Rollback state verification** — `rollbackToVersion_restoresState` does not assert that the job's steps match the rolled-back version. It only checks that a new version row was created. | Rollback could silently fail to restore state, leaving the job in an inconsistent state. | High |
| M2 | **Rollback with unregistered step type** — Scenario 7 from edge cases: rollback reuses import path which validates step types. No test exercises this gap. | Will cause production failures when rolling back to versions with removed custom step types. | Critical |
| M3 | **SKIP mode import** — No test for `mode=SKIP` behavior (should not modify existing job). | Import mode is untested; could silently overwrite data. | High |
| M4 | **YAML round-trip** — YAML export exists but no YAML import path or round-trip test. The import endpoint only accepts JSON. | If YAML import was planned, it's incomplete. If not planned, the YAML export test is read-only verification. | Medium |

### Unit Test Gaps (no unit tests exist)

| # | Missing Unit Tests | Scope |
|---|--------------------|-------|
| M5 | **Export serialization** — `buildExport()` isolation: null schedule handling, empty steps list, stepConfig parsing edge cases | 4-5 tests |
| M6 | **Import validation rules** — Each validator rule as an individual unit test (empty job name, duplicate step names, self-referential dependency, missing credential ref) | 8-10 tests |
| M7 | **Version service** — `saveVersion`, `listVersions`, `getVersion` (not-found case), `deleteVersionsForJob` | 5-6 tests |
| M8 | **Malformed stepConfig on export** — What happens when the CLOB contains invalid JSON? Export should fail gracefully or fall back to string. | 1-2 tests |

### Edge Case Gaps

| # | Missing Test | Scenario Reference |
|---|-------------|-------------------|
| M9 | **Empty steps array import** | Edge case scenario 12 — validator should reject, but no test confirms this |
| M10 | **Concurrent import of same job name** | Edge case scenario 15 — requires two threads importing simultaneously |
| M11 | **Export with null stepConfig** | Does export produce `"stepConfig": null` or omit the key? |
| M12 | **Import with no schedule** | Verify missing schedule field results in no schedule being created (not a broken default) |
| M13 | **Future format version rejection** | Import with `formatVersion: "99.0"` should be rejected |
| M14 | **Export non-existent job ID** | GET /jobs/999/export → should return 404 |

## Recommended Test Additions (Prioritized)

### Immediate (before merging Phase 4)

```java
// In JobExportImportRoundTripTest.java:

@Test
void rollbackToVersion_verifiesRestoredState() {
    // Create job with step "original" → save version number
    // Add step "new-step" → verify 2 steps
    // Rollback to v1
    // GET /jobs/{id} and assert exactly 1 step named "original"
}

@Test
void importSkipMode_doesNotModifyExistingJob() {
    // Create job with known state
    // Import same jobName with mode=SKIP and different content
    // Assert job state unchanged
}

@Test
void importValidation_rejectsEmptySteps() {
    // Import request with steps: [] → assert rejection
}

@Test
void importValidation_rejectsFutureFormatVersion() {
    // formatVersion: "99.0" → assert rejection
}

@Test
void exportNonExistentJob_returns404() {
    // GET /jobs/999/export → assert 404
}
```

### Short-Term (next sprint)

- Extract `validateImport()` logic into a testable unit and add per-rule unit tests (M6)
- Add malformed stepConfig export test (M8)
- Add rollback state verification to existing rollback test (M1)

## Test Fixtures

**No fixture files exist yet.** The current test class builds all data programmatically via API calls. Recommended fixtures for future use:

```
src/test/resources/fixtures/phase4/
├── valid_job_export.json          — Complete job with steps, deps, envVars, schedule
├── minimal_job_export.json        — Job with 1 step, no optional fields
├── unknown_step_type.json         — Step references non-existent type
├── cyclic_dependencies.json       — A→B→C→A cycle
├── duplicate_step_names.json      — Two steps named "build"
├── empty_steps.json               — steps: []
├── future_format_version.json     — formatVersion: "99.0"
└── invalid_cron_schedule.json     — schedule with bad cron expression
```

These fixtures would enable unit tests for `JobExportImportService.validateImport()` without requiring a full Spring context.

## Coverage Summary

| Category | Planned | Implemented | Gap |
|----------|---------|-------------|-----|
| Integration — export/import round-trip | 4 | 3 (no YAML import) | -1 |
| Integration — validation rejection | 5 | 4 (missing empty steps, future version, SKIP mode) | -3 |
| Integration — version CRUD | 3 | 2 (rollback state verification incomplete) | -1 |
| Unit — serialization | 5 | 0 | -5 |
| Unit — validation rules | 10 | 0 | -10 |
| Unit — version service | 5 | 0 | -5 |
| **Total** | **27** | **9** | **-18** |

## Exit Criteria

Phase 4 testing is complete when:
- [ ] All critical gaps (M1, M2, M3) are addressed
- [ ] Rollback test verifies restored state matches target version snapshot
- [ ] SKIP mode import is tested
- [ ] Empty steps and future format version rejections are tested
- [ ] At least one unit test class exists for `validateImport()` rules (isolated from Spring context)
