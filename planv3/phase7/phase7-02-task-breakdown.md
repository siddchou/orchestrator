<!-- FILE: phase7-02-task-breakdown.md -->

# Phase 7.2 — Task Breakdown

## Task 1: Add springdoc-openapi dependency and verify auto-generation

| Field | Value |
|-------|-------|
| **Files Touched** | `pom.xml` |
| **Definition of Done** | `springdoc-openapi-starter-webmvc-ui` added to pom.xml; app starts without error; `/swagger-ui/index.html` loads and lists at least the AuthController endpoints |
| **Test to Add** | Integration test: `SwaggerUiIntegrationTest` — start embedded server, GET `/swagger-ui/index.html`, assert 200. GET `/v3/api-docs`, assert JSON contains `"paths"` key with non-empty map |
| **Depends On** | — |

## Task 2: Annotate controllers with `@Operation` / `@Parameter` for clarity

| Field | Value |
|-------|-------|
| **Files Touched** | All 11 controller files under `src/main/java/com/novakai/orchestrator/api/controller/` |
| **Definition of Done** | Every endpoint has an `@Operation(summary = "...")`. Endpoints with query params have `@Parameter(description = "...")`. Swagger UI renders readable summaries for all ~60 endpoints. Grouped into `@Tag(name = "...")` per domain (Auth, Jobs, Runs, Notifications, etc.) |
| **Test to Add** | Extend Task 1 test: assert `/v3/api-docs` contains a `summary` field for every path operation |
| **Depends On** | Task 1 |

## Task 3: Create orchestrator-cli Maven module skeleton

| Field | Value |
|-------|-------|
| **Files Touched** | `pom.xml` (add `<module>`), `orchestrator-cli/pom.xml`, `orchestrator-cli/src/main/java/com/novakai/orch/cli/OrchCli.java` |
| **Definition of Done** | New Maven module compiles. Parent pom includes `orchestrator-cli` in `<modules>`. Picocli dependency added. `mvn compile` succeeds at root. `java -jar orchestrator-cli/target/*.jar --help` prints usage |
| **Test to Add** | None (skeleton) |
| **Depends On** | — |

## Task 4: Implement CLI auth mixin and login command

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-cli/src/main/java/com/novakai/orch/cli/config/CliConfig.java`, `orchestrator-cli/src/main/java/com/novakai/orch/cli/auth/AuthCommand.java` |
| **Definition of Done** | `orch login -u admin -p changeme` calls POST `/api/auth/login`, extracts token, writes to `~/.orchestrator/token`. Token is read by subsequent commands. `ORCHESTRATOR_TOKEN` env var bypasses file cache. Expired token (401 response) triggers re-login prompt |
| **Test to Add** | Unit test: `AuthCommandTest` — mock `RestTemplate`, verify POST to `/api/auth/login` with correct body, assert token extracted from JSON response |
| **Depends On** | Task 3 |

## Task 5: Implement CLI jobs list / run commands

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-cli/src/main/java/com/novakai/orch/cli/jobs/JobsListCommand.java`, `JobsRunCommand.java` |
| **Definition of Done** | `orch jobs list` renders paginated table. `orch jobs run 1 --wait` triggers and polls until terminal status. `--json` flag outputs raw JSON. Job name (string) vs ID (numeric) auto-detected in `run` command |
| **Test to Add** | Unit tests: `JobsListCommandTest`, `JobsRunCommandTest` — mock API responses, verify table rendering and poll loop exit conditions |
| **Depends On** | Task 4 |

## Task 6: Implement CLI jobs export / import commands

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-cli/src/main/java/com/novakai/orch/cli/jobs/JobsExportCommand.java`, `JobsImportCommand.java` |
| **Definition of Done** | `orch jobs export 1 -f yaml -o job.yaml` writes file. `orch jobs import job.yaml` POSTs to `/api/jobs/import`. Format auto-detected from file extension if `--format` omitted |
| **Test to Add** | Unit test: `JobsExportImportTest` — mock GET/POST, verify content-type headers and file I/O |
| **Depends On** | Task 4 |

## Task 7: Implement CLI runs list / tail commands

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-cli/src/main/java/com/novakai/orch/cli/runs/RunsListCommand.java`, `RunsTailCommand.java` |
| **Definition of Done** | `orch runs list -j 1 --status SUCCESS` renders filtered table. `orch runs tail 42` connects to SSE endpoint, prints log lines with timestamps, exits on `done` event. Handles client disconnect and reconnect gracefully |
| **Test to Add** | Unit test: `RunsTailCommandTest` — use a local SSE emitter (or mock `RestTemplate` exchange returning `SseEmitter`) to verify line-by-line output. Integration test against embedded server with a fake run |
| **Depends On** | Task 4 |

## Task 8: Implement CLI notifications list command

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-cli/src/main/java/com/novakai/orch/cli/notifications/NotificationsListCommand.java` |
| **Definition of Done** | `orch notifications list -j 1` renders subscription table. Clear error when non-ADMIN token used |
| **Test to Add** | Unit test: mock API response, verify table output and 403 handling |
| **Depends On** | Task 4 |

## Task 9: Create MkDocs site structure and `mkdocs.yml`

| Field | Value |
|-------|-------|
| **Files Touched** | `mkdocs.yml`, `docs-site/` (new directory with page stubs) |
| **Definition of Done** | `mkdocs serve` starts locally. Site nav matches the structure in `phase7-03-docs-site-structure.md`. All pages render without 404 cross-links. Material for MkDocs theme configured |
| **Test to Add** | None (manual verification via `mkdocs build` + checking output) |
| **Depends On** | — |

## Task 10: Migrate existing doc content into MkDocs pages

| Field | Value |
|-------|-------|
| **Files Touched** | All pages under `docs-site/`, original docs (`README.md`, `SETUP_GUIDE.md`, `USER_GUIDE.md`, `GUIDE.md`) updated with "moved to docs-site" note |
| **Definition of Done** | Content from the four overlapping docs is deduplicated and placed into the appropriate MkDocs pages. No unique information is lost. Original files either deleted or replaced with a one-line redirect note pointing to the docs site |
| **Test to Add** | None (editorial) |
| **Depends On** | Task 9 |

## Task 11: Create GitHub Actions CI workflow

| Field | Value |
|-------|-------|
| **Files Touched** | `.github/workflows/ci.yml` |
| **Definition of Done** | Workflow triggers on push to `master` and all PRs. Three jobs: (1) `backend-test` — `mvn clean verify`, (2) `frontend-test` — `ng test --watch=false --browsers=ChromeHeadlessCI`, (3) `cli-build` — compile `orchestrator-cli`. All must pass for the check to green |
| **Test to Add** | Deliberately failing test (see Task 14) |
| **Depends On** | — |

## Task 12: Create CONTRIBUTING.md and issue templates

| Field | Value |
|-------|-------|
| **Files Touched** | `CONTRIBUTING.md`, `.github/ISSUE_TEMPLATE/bug_report.md`, `.github/ISSUE_TEMPLATE/feature_request.md` |
| **Definition of Done** | `CONTRIBUTING.md` covers: build instructions, how to add a step type (links to docs-site/plugin-development), test running, PR checklist. Bug report template has fields for steps to reproduce, expected/actual behavior, environment. Feature request has use case and acceptance criteria |
| **Test to Add** | None |
| **Depends On** | Task 10 (references the new docs site) |

## Task 13: Add `@Schema` annotations to key DTOs for OpenAPI clarity

| Field | Value |
|-------|-------|
| **Files Touched** | `ApiResponse.java`, `JobDefinitionRequest.java`, `JobDefinitionResponse.java`, `JobRunSummary.java`, `JobRunDetail.java`, `AuthResponse.java` |
| **Definition of Done** | Key DTOs have `@Schema(description = "...")` on the class level and `@Schema(example = "...")` on key fields. Swagger UI "Schemas" tab shows readable descriptions, not just field names |
| **Test to Add** | Extend Task 1 test: assert `/v3/api-docs/components/schemas` contains description text for `JobDefinitionResponse` |
| **Depends On** | Task 2 |

## Task 14: CI smoke test — verify pipeline actually fails on broken code

| Field | Value |
|-------|-------|
| **Files Touched** | `.github/workflows/ci.yml`, temporary test file |
| **Definition of Done** | Push a commit with a deliberately failing unit test (e.g., `assertTrue(false)`). Confirm CI run fails. Revert the test. Confirm CI passes. Document result in Task 14 checklist item |
| **Test to Add** | The deliberate failure itself, then reverted |
| **Depends On** | Task 11 |

## Task 15: End-to-end CLI integration test against embedded server

| Field | Value |
|-------|-------|
| **Files Touched** | `orchestrator-cli/src/test/java/com/novakai/orch/cli/EndToEndCliTest.java` |
| **Definition of Done** | Test spins up `@SpringBootTest(webEnvironment = RANDOM_PORT)`, runs CLI commands via `CommandLine.call()`, verifies: login succeeds, jobs list returns seed data, jobs run triggers a run, runs tail captures log output. All against H2 test profile |
| **Test to Add** | The integration test itself |
| **Depends On** | Task 8 (all CLI commands implemented) |

## Dependency Graph

```
Task 1 → Task 2 → Task 13
                  ↘
Task 3 → Task 4 → Task 5 → Task 7
                 ↗       ↘
              Task 6      Task 8 → Task 15

Task 9 → Task 10 → Task 12

Task 11 → Task 14
```
