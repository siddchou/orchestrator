# Phase 4 UI — Implementation Status (Tasks 1–4)

**Date:** 2026-07-31
**Branch:** `plan3-phase4-ui`

## Completed Tasks

| Task | Summary | Commit | Tests |
|------|---------|--------|-------|
| **Task 1** | Add jsondiffpatch dependency and CSS | `[Task 1]` 38b5e7a | N/A (CSS only) |
| **Task 2** | Extract `downloadFile()` utility + refactor KeyDialog | `[Task 2]` d5de297 | 2 tests in `file-utils.spec.ts` |
| **Task 3** | Export/Import/Version methods on JobService + models | `[Task 3]` 53a91b9 | 8 tests added to `job.service.spec.ts` |
| **Task 4** | Create JsonDiffService for JSON diff comparison | `[Task 4]` f71396e | 7 tests in `json-diff.service.spec.ts` |

## Files Touched

### New files (5)
- `orchestrator-ui/src/assets/styles/jsondiffpatch-overrides.scss` — Dark mode CSS overrides for jsondiffpatch diff output
- `orchestrator-ui/src/app/core/utils/file-utils.ts` — `downloadFile()` utility function
- `orchestrator-ui/src/app/core/utils/file-utils.spec.ts` — Unit tests for downloadFile
- `orchestrator-ui/src/app/core/services/json-diff.service.ts` — JsonDiffService with `compare(left, right)` method
- `orchestrator-ui/src/app/core/services/json-diff.service.spec.ts` — Unit tests for JsonDiffService

### Modified files (5)
- `orchestrator-ui/package.json`, `package-lock.json` — Added `jsondiffpatch@0.7.6` dependency
- `orchestrator-ui/src/styles.scss` — Added jsondiffpatch CSS imports + dark mode overrides import
- `orchestrator-ui/src/app/core/models/job.model.ts` — Added `JobVersionSummary`, `JobImportRequest`, `ImportConflictMode` types
- `orchestrator-ui/src/app/core/services/job.service.ts` — Added 5 methods: `exportJob()`, `importJob()`, `listVersions()`, `getVersionSnapshot()`, `rollbackToVersion()`
- `orchestrator-ui/src/app/core/services/job.service.spec.ts` — Added test suites for export/import/version methods
- `orchestrator-ui/src/app/features/credentials/key-dialog.component.ts` — Refactored `downloadPrivateKey()` to use shared `downloadFile()` utility

## Build / Test Status

- **Build:** ✅ Passes (warnings: Sass @import deprecation, bundle budget +56 kB — both pre-existing)
- **Tests:** ✅ 211 passed | 3 failed (pre-existing `team.service.spec.ts` jsdom HTTP mocking issue)
- **New tests:** All 17 new tests pass

## Divergences from Plan

### jsondiffpatch API adaptation
**Plan assumed:** Separate `jsondiffpatch-formatters` package with `formatters.html.convert(delta, 0, options)` API.
**Reality:** Formatters are bundled in `jsondiffpatch@0.7.6`. The HTML formatter is imported as `import { format } from 'jsondiffpatch/formatters/html'` and returns `string | undefined` directly — no `.convert()` chain needed. The `format(delta, left?)` function optionally takes the original "left" object for better context display.

**Impact:** JsonDiffService implementation uses the simpler bundled API. No functional difference — the service still produces HTML diff strings from two JSON objects. Tasks 5+ that consume JsonDiffService are unaffected since the public interface (`compare(left, right): string | undefined`) matches the plan.

### CSS import path
**Plan assumed:** `node_modules/jsondiffpatch/lib/formatters/styles/html.css`
**Reality:** Package exports map requires `jsondiffpatch/formatters/styles/html.css` (resolved through package exports).

## Remaining Tasks

Tasks 5–15 are not yet implemented. They depend on the foundation built by Tasks 1–4:

- **Task 5+** — Version history UI components (depends on Task 3 models/service)
- **Task 6+** — Export/Import dialog (depends on Task 2 download utility, Task 3 service methods)
- **Task 7+** — Diff viewer component (depends on Task 4 JsonDiffService)
- **Tasks 8–15** — Integration into job detail page, routing, etc.

## Notes for Next Implementer

1. The `downloadFile()` utility accepts both `Blob` and `string` content — useful for export jobs that return blobs from the API.
2. `JsonDiffService.compare()` returns `undefined` when objects are identical — components should check for this before rendering diff HTML.
3. Dark mode overrides for jsondiffpatch use semi-transparent green/red backgrounds (`rgba(34, 197, 94, 0.25)` / `rgba(239, 68, 68, 0.25)`) to maintain readability on dark surfaces.
4. The pre-existing test failures in `team.service.spec.ts` (AggregateError from jsdom HTTP mocking) are unrelated to this work and should be addressed separately.
