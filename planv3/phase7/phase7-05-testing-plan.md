<!-- FILE: phase7-05-testing-plan.md -->

# Phase 7.5 — Testing Plan

## CLI Unit Tests (Mocked API Responses)

Each CLI command is tested in isolation with a mocked `RestTemplate`. The mock returns pre-built JSON strings matching the server's `ApiResponse<T>` envelope.

| Test Class | Method | Mock Response | Assertion |
|------------|--------|---------------|-----------|
| `AuthCommandTest` | `test_login_success` | 200 `{ "success": true, "data": { "token": "abc123", "role": "ADMIN", "passwordExpired": false } }` | Token written to temp file. Exit code 0. Stdout contains token. |
| `AuthCommandTest` | `test_login_bad_credentials` | 401 `{ "success": false, "error": "Invalid username or password" }` | Exit code 1. Error printed to stderr. No file written. |
| `AuthCommandTest` | `test_token_from_env_var` | (no login call) | When `ORCHESTRATOR_TOKEN` is set, CLI skips file read and uses env var directly. |
| `JobsListCommandTest` | `test_list_default_page` | 200 `{ "success": true, "data": { "content": [...], "totalPages": 3 } }` | Table has correct column count. Pagination footer shows "Page 1/3". |
| `JobsListCommandTest` | `test_list_empty_result` | 200 `{ "success": true, "data": { "content": [], "totalPages": 0 } }` | Prints "No jobs found." Exit code 0. |
| `JobsListCommandTest` | `test_list_json_output` | Same as default page | With `--json`, stdout is valid JSON array (no table formatting). |
| `JobsRunCommandTest` | `test_run_by_id` | 201 `{ "success": true, "data": { "runId": 42 } }` | POST to `/api/jobs/5/run`. Exit code 0. Prints run ID. |
| `JobsRunCommandTest` | `test_run_by_name` | 201 same envelope | POST to `/api/jobs/name/DailyETL/run`. URL routing correct for string arg. |
| `JobsRunCommandTest` | `test_run_with_params` | 201 | Request body contains `{ "parameters": { "key1": "val1" } }`. |
| `JobsRunCommandTest` | `test_run_wait_success` | Sequence: 201 → GET 200 (RUNNING) ×3 → GET 200 (SUCCESS) | Polls every 2s. Final exit code 0. Total output includes status transitions. |
| `JobsRunCommandTest` | `test_run_wait_failed` | Sequence: 201 → GET 200 (FAILED) | Exit code 1. Prints failure message. |
| `JobsExportCommandTest` | `test_export_json_to_file` | 200 raw JSON string body | File written with correct content. Content-Disposition header not used (CLI uses direct REST call). |
| `JobsExportCommandTest` | `test_export_yaml` | 200 YAML string body | Query param `format=yaml` present in request URL. |
| `JobsImportCommandTest` | `test_import_json_file` | 201 `{ "success": true, "data": { ... } }` | File content POSTed to `/api/jobs/import`. Content-Type: application/json. |
| `JobsImportCommandTest` | `test_import_validation_error` | 400 `{ "success": false, "error": "Import validation failed: ..." }` | Exit code 1. Error message formatted (not raw JSON). |
| `RunsListCommandTest` | `test_list_with_filters` | 200 filtered page | Query params `jobId=5&status=SUCCESS&from=2026-01-01` present in request URL. |
| `RunsTailCommandTest` | `test_tail_streams_lines` | SSE events: `data: line1\ndata: line2\nevent: done\ndata: RUN_COMPLETE` | Each line printed with timestamp prefix. Exits cleanly on `done`. |
| `RunsTailCommandTest` | `test_tail_connection_error` | `ResourceAccessException` on first exchange | Prints `[connection lost]`, retries 3 times, exits code 3. |
| `NotificationsListCommandTest` | `test_list_for_job` | 200 subscription array | GET to `/api/notifications/subscriptions/job/5`. Table renders channel types. |
| `NotificationsListCommandTest` | `test_list_forbidden` | 403 | Prints "ADMIN role required" to stderr. Exit code 1. |

## Swagger UI Integration Tests

| Test Class | Method | Description | Assertion |
|------------|--------|-------------|-----------|
| `SwaggerUiIntegrationTest` | `test_swagger_ui_renders` | Start embedded server (`@SpringBootTest(webEnvironment = RANDOM_PORT)`). GET `/swagger-ui/index.html`. | Status 200. Response contains `<title>Swagger UI</title>` or springdoc's equivalent title. |
| `SwaggerUiIntegrationTest` | `test_api_docs_json_valid` | GET `/v3/api-docs`. Parse as JSON. | Root object has `"openapi"` = "3.1.0" (or 3.0.x). `"paths"` key present with ≥50 entries (covers all ~60 endpoints). |
| `SwaggerUiIntegrationTest` | `test_all_controllers_present` | GET `/v3/api-docs`. Extract path prefixes. | Assert paths under `/api/auth/`, `/api/jobs`, `/api/runs`, `/api/notifications/`, `/api/credentials`, `/api/audit`, `/api/teams`, `/api/step-types`, `/api/system/` all present. |
| `SwaggerUiIntegrationTest` | `test_operations_have_summaries` | GET `/v3/api-docs`. Iterate every path → operation. | Every operation has a non-blank `"summary"` field. Count of operations with summary == total operation count. |
| `SwaggerUiIntegrationTest` | `test_schemas_have_descriptions` | GET `/v3/api-docs`. Check `components.schemas`. | `JobDefinitionResponse`, `AuthResponse`, `ApiResponse` schemas have non-blank `"description"` fields. |
| `SwaggerUiIntegrationTest` | `test_auth_security_scheme` | GET `/v3/api-docs`. | `components.securitySchemes` contains `bearerAuth` with `scheme: bearer` and `bearerFormat: JWT`. |

## CLI End-to-End Integration Test

| Test Class | Method | Description | Assertion |
|------------|--------|-------------|-----------|
| `EndToEndCliTest` | `test_full_workflow` | Starts embedded Spring Boot app (H2 test profile, seed users). Runs: `login -u admin -p changeme` → `jobs list` → `jobs run 1 --wait` → `runs tail {runId}`. | Login returns token. Jobs list contains at least one job. Run completes with terminal status. Tail captures ≥1 log line. All exit codes 0 (or expected). |

## CI Pipeline Smoke Test

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create branch `test/ci-smoke`. Add a test class `DeliberatelyFailingTest.java` with single method: `@Test void shouldFail() { assertTrue(false, "CI smoke test"); }` | Test compiles. |
| 2 | Push branch, create PR against `master`. | CI workflow triggers. |
| 3 | Observe CI run | `backend-test` job **fails** with exit code ≠0. The specific test `DeliberatelyFailingTest.shouldFail` is shown as red in the Maven output. |
| 4 | Delete the test class, push another commit. | CI workflow re-triggers. `backend-test` job **passes**. |
| 5 | Document result | Add a checklist mark to Task 14 confirming "CI correctly fails on broken tests." |

## Test Execution Matrix

| Test Type | Runner | Trigger | Duration (estimated) |
|-----------|--------|---------|---------------------|
| CLI unit tests | `mvn test` in `orchestrator-cli` module | Every PR, every push | < 30s |
| Swagger UI integration tests | `mvn verify` in main module (uses `@SpringBootTest`) | Every PR touching controller files or pom.xml | ~60s |
| CLI end-to-end test | `mvn verify` in `orchestrator-cli` module | Every push to `master`, nightly on other branches | ~90s |
| Angular tests (`ng test`) | GitHub Actions `frontend-test` job | PRs touching `orchestrator-ui/` files only (path-gated) | ~2min |
| CI smoke test | Manual (one-time) | Task 14 completion | N/A |
