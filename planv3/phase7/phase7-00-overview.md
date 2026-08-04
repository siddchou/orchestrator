<!-- FILE: phase7-00-overview.md -->

# Phase 7 — API & Docs: Overview

## Scope

Phase 7 adds developer-facing tooling and documentation infrastructure around the API surface established by Phases 1–6. It does not introduce new backend functionality. Four deliverables:

1. **OpenAPI / Swagger UI** — Add `springdoc-openapi` to auto-generate an interactive API spec from the existing 11 controllers (~60 endpoints).
2. **MkDocs documentation site** — Consolidate four overlapping markdown docs (`README.md`, `SETUP_GUIDE.md`, `USER_GUIDE.md`, `GUIDE.md`) plus Phase 1–6 technical docs into a single navigable site.
3. **orchestrator-cli** — A Java/Picocli CLI that wraps the REST API for terminal-based job management, run monitoring, and notification administration.
4. **Contribution readiness** — GitHub Actions CI pipeline, `CONTRIBUTING.md`, issue templates.

## Assumptions

- **[ASSUMED]** Angular `ng test` executes without fatal errors under headless Chrome. If the test suite is incomplete or broken, the CI will run it with `--watch=false --browsers=ChromeHeadlessCI` and treat failures as warnings (non-blocking) until stabilized.
- **[ASSUMED]** The existing shell scripts (`scripts/auth.sh`, `scripts/run-job.bat`) are best-effort wrappers; the new CLI supersedes them. We will retain the scripts during Phase 7 and add deprecation notices, removing them only after the CLI is validated.
- **[ASSUMED]** No additional backend endpoints are needed beyond those documented in the code review findings. If gaps surface during CLI development (e.g., missing filter params), they should be raised as a separate phase.
- **[CONFIRMED]** Java 21, Spring Boot 4.1.0 — Picocli is fully compatible and receives Java 21 patterns/records support since v4.7.
- **[CONFIRMED]** No CI pipeline exists; `.github/workflows/` is empty.

## Table of Contents

| File | Purpose |
|------|---------|
| `phase7-code-review-findings.md` | Current endpoint inventory, dependency scan, doc audit |
| `phase7-00-overview.md` | This file — scope, assumptions, effort estimate |
| `phase7-01-cli-design.md` | CLI command reference, auth model, technology choice |
| `phase7-02-task-breakdown.md` | 15 PR-sized tasks with dependencies and DoD |
| `phase7-03-docs-site-structure.md` | MkDocs site map and doc migration matrix |
| `phase7-04-edge-cases-and-failure-modes.md` | Failure scenarios and required handling |
| `phase7-05-testing-plan.md` | Unit, integration, and CI smoke test plan |

## Effort Estimate

| Deliverable | Tasks | Estimated Effort | Risk |
|-------------|-------|-----------------|------|
| OpenAPI / Swagger UI | 2 | 2 hours | Low — springdoc is auto-configuring; main work is `@Operation` annotations |
| MkDocs site | 3 | 4 hours | Medium — content dedup across 4 overlapping docs requires editorial judgment |
| orchestrator-cli | 6 | 8–10 hours | Medium — SSE log tailing in a TTY context needs careful buffering; Picocli learning curve |
| CI + contribution scaffolding | 4 | 3 hours | Low — standard GitHub Actions templates |
| **Total** | **15** | **~17–21 hours** | |
