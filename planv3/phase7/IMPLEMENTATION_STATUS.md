# Phase 7 — Implementation Status

**Date:** 2026-08-04
**Branch:** `plan3-phase7`

---

## Audit Table (Step 1 Verdicts → End-of-Session Status)

| Task # | Description | Verdict at Start | Status at End | Note |
|--------|-------------|------------------|---------------|------|
| **Task 1** | Add springdoc-openapi dependency and verify auto-generation | DONE — VERIFIED | Unchanged | 5 integration tests pass |
| **Task 2** | Annotate controllers with `@Operation` / `@Parameter` for clarity | DONE — VERIFIED | Unchanged | All ~60 endpoints annotated, summaries verified by test |
| **Task 3** | Create orchestrator-cli Maven module skeleton | DONE — VERIFIED | Unchanged | Picocli v4.7.6, parent POM multi-module build |
| **Task 4** | Implement CLI auth mixin and login command | DONE — VERIFIED | Unchanged | AuthCommandTest passes (3 tests) |
| **Task 5** | Implement CLI jobs list / run commands | IMPLEMENTED BUT UNTESTED | Completed | Fixed Mockito generic type compilation errors in JobsListCommandTest, JobsRunCommandTest. All tests pass. |
| **Task 6** | Implement CLI jobs export / import commands | IMPLEMENTED BUT UNTESTED | Completed | Fixed `HttpRequest.headerValue()` (doesn't exist) → replaced with `headers().firstValue()`. JobsExportImportTest passes (4 tests). |
| **Task 7** | Implement CLI runs list / tail commands | NOT IMPLEMENTED | Completed | Created RunsListCommand, RunsTailCommand, RunsGroupCommand. Registered in OrchCli. RunsTailCommandTest passes (1 test). |
| **Task 8** | Implement CLI notifications list command | IMPLEMENTED BUT UNTESTED | Completed | Added NotificationsListCommandTest (2 tests: table render + 403 handling). All pass. |
| **Task 9** | Create MkDocs site structure and `mkdocs.yml` | NOT IMPLEMENTED | Completed | Created mkdocs.yml with Material theme, all 26 pages per site map in docs-site/ |
| **Task 10** | Migrate existing doc content into MkDocs pages | NOT IMPLEMENTED | Partially Done | Added redirect note to README.md. Docs-site has stub pages only — full content migration from README.md, SETUP_GUIDE.md, USER_GUIDE.md, GUIDE.md requires editorial dedup (see Blocked Tasks). |
| **Task 11** | Create GitHub Actions CI workflow | NOT IMPLEMENTED | Completed | Created .github/workflows/ci.yml with backend-test, frontend-test (path-gated), cli-build jobs. Maven/Node caching configured. |
| **Task 12** | Create CONTRIBUTING.md and issue templates | NOT IMPLEMENTED | Completed | CONTRIBUTING.md + bug_report.md + feature_request.md created in .github/ISSUE_TEMPLATE/ |
| **Task 13** | Add `@Schema` annotations to key DTOs for OpenAPI clarity | NOT IMPLEMENTED | Completed | Added @Schema(description=...) and @Schema(example=...) to ApiResponse, AuthResponse, JobDefinitionRequest, JobDefinitionResponse, JobRunSummary, JobRunDetail. Swagger UI tests still pass (5/5). |
| **Task 14** | CI smoke test — verify pipeline actually fails on broken code | NOT IMPLEMENTED | Skipped (requires git push) | Requires pushing a deliberately failing test to GitHub and observing CI run. Cannot be done locally without remote access. |
| **Task 15** | End-to-end CLI integration test against embedded server | NOT IMPLEMENTED | Not Started | Depends on all CLI commands being implemented. Would require @SpringBootTest with RANDOM_PORT, running CLI via CommandLine.call(). Out of scope for this session. |

---

## What Was Built This Session

### CLI Test Fixes (Tasks 5-6)
- Fixed `JobsListCommandTest.java` — Mockito generic type mismatch (`HttpResponse<Object>` vs `HttpResponse<String>`) resolved with raw mock + `thenAnswer(i -> raw)` pattern
- Fixed `JobsRunCommandTest.java` — Same generic fix, added ArgumentCaptor for URL verification
- Fixed `JobsExportImportTest.java` — Replaced non-existent `HttpRequest.headerValue()` with `headers().firstValue()`. Generic type fix.

### CLI Runs Commands (Task 7)
- Created `RunsGroupCommand.java` — Picocli group command registering list and tail subcommands
- Created `RunsListCommand.java` — Lists job runs with filters (--job, --status, --from, --to), pagination, table/JSON output
- Created `RunsTailCommand.java` — Streams SSE logs from `/api/runs/{runId}/log-stream` with retry logic (3 retries, exponential backoff). Supports `--follow=false` for one-shot log fetch.
- Updated `OrchCli.java` — Added RunsGroupCommand to subcommand list

### CLI Tests (Tasks 7-8)
- Created `RunsTailCommandTest.java` — Tests non-follow mode with mocked HttpClient
- Created `NotificationsListCommandTest.java` — Tests table rendering + 403 forbidden handling

### @Schema Annotations (Task 13)
- Added `@Schema(description=...)` and `@Schema(example=...)` to:
  - `ApiResponse.java` — envelope description, status/error examples
  - `AuthResponse.java` — JWT token example, role description
  - `JobDefinitionRequest.java` — job name/working dir examples
  - `JobDefinitionResponse.java` — full job definition with steps/envVars/schedule descriptions
  - `JobRunSummary.java` — run status/trigger type examples
  - `JobRunDetail.java` — per-step execution detail description

### MkDocs Site (Task 9)
- Created `mkdocs.yml` — Material theme, full navigation tree matching site map, search/mkdocstrings plugins
- Created 26 stub pages in `docs-site/`: index.md, getting-started/* (3), user-guide/* (5), admin/* (4), features/* (5), developer/* (4), deployment/* (3), contribution/* (1)

### CI Pipeline (Task 11)
- Created `.github/workflows/ci.yml` — Three jobs: backend-test (`mvn clean verify`), frontend-test (path-gated on `orchestrator-ui/` changes, ChromeHeadlessCI), cli-build (compile orchestrator-cli via parent POM). Maven and npm caching configured.

### Contribution Docs (Task 12)
- Created `CONTRIBUTING.md` — Build instructions, step type plugin guide link, test commands, PR checklist, coding standards
- Created `.github/ISSUE_TEMPLATE/bug_report.md` — Steps to reproduce, expected/actual behavior, environment fields
- Created `.github/ISSUE_TEMPLATE/feature_request.md` — Use case, acceptance criteria, alternatives considered

### Doc Migration (Task 10)
- Added redirect note to `README.md` pointing to docs-site

---

## Blocked Tasks and Divergences

### Task 10 — Partially Done: Full Content Migration
The docs-site has stub pages with placeholder content. The actual migration of content from README.md, SETUP_GUIDE.md, USER_GUIDE.md, GUIDE.md requires editorial deduplication across four overlapping documents. This is a substantive writing task that cannot be automated without losing information. Per the divergence handling rules: "Do not lose content when consolidating existing docs into the new site."

**Recommendation:** Complete this as a separate session focused on reading each source file, extracting unique content, and populating the appropriate docs-site pages. Only then replace originals with redirect notes.

### Task 14 — Skipped: CI Smoke Test
Requires pushing to GitHub and observing CI run behavior. Cannot be done locally.

**Recommendation:** After merging this branch, push a test commit with `assertTrue(false)` to verify backend-test job fails, then revert.

### Task 15 — Not Started: End-to-End CLI Integration Test
Would require spinning up an embedded Spring Boot server and running CLI commands against it. Complex setup requiring H2 test profile, seed data, and SSE endpoint testing.

**Recommendation:** Implement in a follow-up session after all other Phase 7 tasks are merged.

---

## Verification Results

### Full Test Suite
```
Main module: 388 tests, 0 failures, 0 errors, 0 skipped
CLI module:   15 tests, 0 failures, 0 errors, 0 skipped
Total:       403 tests, ALL PASS
BUILD SUCCESS (mvn verify)
```

### Swagger UI Endpoint Coverage
- GET `/swagger-ui/index.html` → 200 OK
- GET `/v3/api-docs` → Contains all 9 controller path prefixes (/api/auth, /api/jobs, /api/runs, /api/notifications, /api/credentials, /api/audit, /api/teams, /api/step-types, /api/system)
- All operations have non-blank `summary` fields
- bearerAuth security scheme configured as HTTP Bearer/JWT

### CLI Commands Exercised
- `java -jar orchestrator-cli/target/*.jar --help` — Shows login, jobs, runs, notifications subcommands
- Jobs list/run/export/import — Unit tests pass with mocked HttpClient
- Runs tail — Non-follow mode test passes
- Notifications list — Table render + 403 handling tests pass

### MkDocs Site Structure
- `mkdocs.yml` exists at repo root with Material theme configuration
- All 26 pages from site map present in docs-site/
- Navigation tree matches phase7-03-docs-site-structure.md exactly

---

## Endpoint Inventory Drift (Step 0.4)
No drift detected. Current codebase has the same 11 controllers (~60 endpoints) as recorded in `phase7-code-review-findings.md`. All controller prefixes verified present in OpenAPI spec.

---

## Git Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | (prior) | `[Task 1] Add springdoc-openapi and Swagger UI integration test` |
| 2 | (prior) | `[Task 2] Annotate all controllers with OpenAPI @Operation, @Parameter, and @Tag` |
| 3 | (prior) | `[Task 3] Create orchestrator-cli Maven module skeleton` |
| 4 | (prior) | `[Task 4] Implement CLI auth mixin and login command` |
| 5-6 | TBD | Fix CLI test compilation errors (Tasks 5-6) |
| 7 | TBD | Implement CLI runs list/tail commands with SSE support (Task 7) |
| 8 | TBD | Add NotificationsListCommandTest (Task 8) |
| 9 | TBD | Create MkDocs site structure with 26 pages and mkdocs.yml (Task 9) |
| 10 | TBD | Add docs-site redirect note to README.md (Task 10 partial) |
| 11 | TBD | Create GitHub Actions CI workflow with backend/frontend/cli jobs (Task 11) |
| 12 | TBD | Create CONTRIBUTING.md and issue templates (Task 12) |
| 13 | TBD | Add @Schema annotations to key DTOs for OpenAPI clarity (Task 13) |

---

## Next Recommended Action

1. **Merge this branch** — All code changes compile, all 403 tests pass
2. **Complete Task 10** — Full doc content migration in a dedicated session
3. **Run Task 14** — CI smoke test after merge (push deliberately failing commit)
4. **Implement Task 15** — End-to-end CLI integration test against embedded server
