<!-- FILE: phase4-00-overview.md -->
# Phase 4 — Job Definition Portability: Overview

## Goal

Add export (JSON/YAML) and import of job definitions using stable names (not internal DB IDs) so definitions are portable across environments and instances, plus a version-history table with rollback.

An exported job definition can be committed to git, imported into a fresh instance (different DB, different credential IDs but same credential *names*), and run identically.

## Scope

### In Scope
- `GET /api/jobs/{id}/export?format=json|yaml` — serializes full job definition (metadata, steps with config, DAG dependencies, env vars, schedule) to portable JSON or YAML
- `POST /api/jobs/import` — deserializes a portable document, validates against registered step types and existing credentials, creates the job with conflict resolution modes (`error`, `update`, `skip`)
- `JOB_DEFINITION_VERSION` table — snapshot-based version history; every import or manual edit writes a new version row
- `GET /api/jobs/{id}/versions` — lists version snapshots for a job
- `POST /api/jobs/{id}/versions/{versionNumber}/rollback` — restores a previous version
- Version-on-write hook in `JobDefinitionService` — triggers on create, update, and import

### Out of Scope (Explicit Boundaries)
- **CLI tool** — Phase 4 only ensures export/import endpoints are CLI-friendly (stable JSON, standard bearer auth). The CLI itself belongs to [Phase 7](#). No new binary, no Picocli module.
- **Notification subscriptions** — Phase 5 feature. Export format reserves a `notifications` field but it will be null/empty until Phase 5 lands.
- **UI version history tab** — mentioned in the plan as a nice-to-have but deferred to a follow-up sprint. The API endpoints are sufficient for Phase 4 delivery.
- **Diff-based versioning** — snapshot approach is used (simpler, correct). Full JSON diff is a UI concern.

## Assumptions ([ASSUMED] markers)

1. **[ASSUMED]** Export format version (`format_version` field) starts at `"1.0"`. Breaking changes to the export schema increment the minor version; non-breaking additions don't.
2. **[ASSUMED]** The `stepConfig` JSON stored in `JOB_STEP.STEP_CONFIG` is well-formed JSON. If malformed entries exist, export will include them as-is (string passthrough) and import validation will catch structural issues per step type schema.
3. **[ASSUMED]** Team remapping on import: the importing user's active team is used by default. An optional `teamName` field in the import document allows targeting a specific team, validated against the importer's membership.
4. **[ASSUMED]** Credential references inside step config use the string field name `credentialRef`. This matches the SFTP executor's schema. Other executors that add secret fields should follow the same naming convention and mark them as `FieldType.SECRET_REF` in their schema.
5. **[ASSUMED]** Oracle database is the primary target. H2 is used for testing only. Flyway migrations use Oracle PL/SQL syntax.

## Effort Estimate

| Task Area | Stories | Complexity | Estimated Days |
|-----------|---------|------------|----------------|
| Export endpoint (JSON + YAML) | 1 | Medium | 2 |
| Import endpoint with validation | 2 | High | 3 |
| Version history table + hook | 1 | Low-Medium | 1.5 |
| Version listing + rollback endpoints | 1 | Medium | 2 |
| Integration tests (round-trip) | 1 | Medium | 1.5 |
| Edge case handling | — | — | 1 |
| **Total** | **6 stories** | — | **~11 days** |

## Table of Contents

1. [Code Review Findings](phase4-code-review-findings.md) — schema, credential pattern, endpoint shapes, PK strategy, Flyway versions
2. [Overview](phase4-00-overview.md) ← you are here
3. [Export/Import Format Design](phase4-01-export-import-format-design.md) — JSON schema, example document, name-vs-ID mapping
4. [Task Breakdown](phase4-02-task-breakdown.md) — PR-sized tasks with DoD and tests
5. [Migration Strategy](phase4-03-migration-strategy.md) — Flyway V10 SQL, rollback
6. [Edge Cases & Failure Modes](phase4-04-edge-cases-and-failure-modes.md) — scenario table
7. [Testing Plan](phase4-05-testing-plan.md) — unit, integration, round-trip regression
