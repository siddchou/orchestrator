<!-- FILE: phase7-code-review-findings.md -->

# Phase 7 — Code Review Findings

## 1. Complete REST Endpoint Inventory

### AuthController (`/api/auth`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/login` | Public | Returns JWT token, role, password-expired flag |
| POST | `/api/auth/change-password` | Authenticated | Change current user password |
| GET | `/api/auth/me` | Authenticated | Get current user info and refresh token |

### JobDefinitionController (`/api/jobs`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/jobs` | VIEWER+ (paginated, searchable) | List jobs |
| POST | `/api/jobs` | ADMIN | Create job |
| GET | `/api/jobs/{id}` | VIEWER+ | Get job detail |
| PUT | `/api/jobs/{id}` | ADMIN | Update job |
| DELETE | `/api/jobs/{id}` | ADMIN | Delete job |
| POST | `/api/jobs/{id}/enable` | ADMIN | Toggle enabled |
| GET | `/api/jobs/{id}/export?format=json\|yaml` | VIEWER+ | Export job definition |
| POST | `/api/jobs/import` | ADMIN | Import job definition |
| POST | `/api/jobs/{id}/steps` | ADMIN | Add step |
| PUT | `/api/jobs/{id}/steps/{stepId}` | ADMIN | Update step |
| DELETE | `/api/jobs/{id}/steps/{stepId}` | ADMIN | Delete step |
| PUT | `/api/jobs/{id}/steps/reorder` | ADMIN | Reorder steps |
| GET | `/api/jobs/{id}/steps/{stepId}/dependencies` | VIEWER+ | Get step dependencies |
| PUT | `/api/jobs/{id}/steps/{stepId}/dependencies` | ADMIN | Set step dependencies |
| GET | `/api/jobs/{id}/env-vars` | VIEWER+ | List job env vars |
| POST | `/api/jobs/{id}/env-vars` | ADMIN | Add env var |
| DELETE | `/api/jobs/{id}/env-vars/{envId}` | ADMIN | Delete env var |
| GET | `/api/jobs/{id}/schedule` | VIEWER+ | Get schedule |
| POST | `/api/jobs/{id}/schedule` | ADMIN | Create schedule |
| PUT | `/api/jobs/{id}/schedule` | ADMIN | Update schedule |
| DELETE | `/api/jobs/{id}/schedule` | ADMIN | Delete schedule |
| POST | `/api/jobs/{id}/schedule/enable` | ADMIN | Enable schedule |
| POST | `/api/jobs/{id}/schedule/disable` | ADMIN | Disable schedule |
| GET | `/api/jobs/{id}/versions` | VIEWER+ | List versions |
| GET | `/api/jobs/{id}/versions/{versionNumber}` | VIEWER+ | Get version snapshot (JSON) |
| POST | `/api/jobs/{id}/versions/{versionNumber}/rollback` | ADMIN | Rollback to version |

### JobExecutionController (`/api`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/jobs/{id}/run` | OPERATOR+ | Trigger run by job ID |
| POST | `/api/jobs/name/{name}/run` | OPERATOR+ | Trigger run by job name |
| GET | `/api/runs` | VIEWER+ (paginated, filterable) | List runs |
| GET | `/api/runs/{runId}` | VIEWER+ | Get run detail with steps |
| GET | `/api/runs/{runId}/steps/{stepId}/log` | VIEWER+ | Get step log output |
| POST | `/api/runs/{runId}/cancel` | OPERATOR+ | Cancel running job |

### LogStreamController (`/api`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/runs/{runId}/log-stream` | VIEWER+ (SSE, text/event-stream) | Live log stream |

### StepExecutionController (`/api`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/steps/name/{stepName}/run` | OPERATOR+ | Run single step by name |

### SystemController (`/api`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/env-vars/global` | ADMIN | List global env vars |
| POST | `/api/env-vars/global` | ADMIN | Add global env var |
| DELETE | `/api/env-vars/global/{envId}` | ADMIN | Delete global env var |
| GET | `/api/system/health` | Public | Health check |
| GET | `/api/system/env-validate?javaHome=&workingDir=` | ADMIN | Validate environment paths |
| GET | `/api/system/cron-validate?expression=` | Public | Validate cron expression |

### StepTypeController (`/api`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/step-types` | Public | List registered step type schemas |

### NotificationController (`/api/notifications`, ADMIN only via `@PreAuthorize`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/notifications/subscriptions` | ADMIN | List all subscriptions |
| GET | `/api/notifications/subscriptions/{id}` | ADMIN | Get subscription |
| GET | `/api/notifications/subscriptions/job/{jobId}` | ADMIN | Subscriptions for job |
| POST | `/api/notifications/subscriptions` | ADMIN | Create subscription |
| PUT | `/api/notifications/subscriptions/{id}` | ADMIN | Update subscription |
| DELETE | `/api/notifications/subscriptions/{id}` | ADMIN | Delete subscription |
| PATCH | `/api/notifications/subscriptions/{id}/toggle` | ADMIN | Toggle active/inactive |
| GET | `/api/notifications/channels` | ADMIN | List channel schemas |
| GET | `/api/notifications/delivery-log?runId=&subscriptionId=` | ADMIN | Delivery log |

### AuditController (`/api/audit`, ADMIN only via `@PreAuthorize`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/audit` | ADMIN | List all audit log entries |

### CredentialController (`/api/credentials`, ADMIN only via `@PreAuthorize`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/credentials` | ADMIN | List credential summaries (no secrets) |
| POST | `/api/credentials` | ADMIN | Create encrypted credential |
| POST | `/api/credentials/generate-keys` | ADMIN | Generate SSH key pair |
| DELETE | `/api/credentials/{id}` | ADMIN | Delete credential |

### TeamController (`/api/teams`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/teams/my-teams` | Authenticated | List user's team memberships |
| POST | `/api/teams/active/{teamId}` | Authenticated | Set active team (validates membership) |
| GET | `/api/teams/active` | Authenticated | Get current active team |

**Total: ~60 endpoints across 11 controllers.**

## 2. springdoc-openapi / Swagger Dependency

**CONFIRMED ABSENT.** Grep for `springdoc|swagger` in `pom.xml` returned no matches. No OpenAPI library is currently on the classpath.

## 3. Existing Documentation Files

| File | Purpose | Content Summary |
|------|---------|-----------------|
| `README.md` | Project overview | Stack, quick start, project structure, step types, API summary (incomplete), deployment Dockerfile example |
| `SETUP_GUIDE.md` | Installation guide | System requirements, DB setup, env vars, build/run modes, dev mode, CLI scripts, seed users, troubleshooting |
| `USER_GUIDE.md` | End-user manual | Roles/permissions table, job CRUD walkthrough, run history, scheduling, global config, CLI usage, API reference (partial) |
| `GUIDE.md` | Combined setup+usage guide | Most comprehensive single doc. Prerequisites, build, dev mode, configuration, step types, full REST API reference, RBAC, DB schema, deployment (systemd), upgrade procedure, pre-deployment checklist |
| `HELP.md` | Spring Boot default stub | Generic Maven/Spring Boot links. No project-specific content. |
| `docs/plugin-development.md` | [NOT READ] Step type plugin guide | Referenced in planv3 as Phase 1.7 deliverable |
| `docs/observability/README.md` | [NOT READ] Observability setup | Phase 6 deliverable for metrics/tracing |
| `orchestrator-ui/README.md` | Angular frontend readme | Frontend-specific build/run instructions |
| `orchestrator-ui/BUG_REPORT.md` | UI bug report template | Angular testing bug tracking |
| `orchestrator-ui/IMPLEMENTATION_STATUS.md` | UI implementation tracker | Phase 5 progress tracking |
| `plan/README.md` + `plan/PHASE-*.md` | Original v1 plan | 7-phase plan (Foundation through Deploy) |
| `planv2/*.md` | Plan v2 | Refined phase plans with sub-phases |
| `planv3/*.md` | Plan v3 (current) | Detailed implementation plans per phase, task breakdowns, edge cases, testing |

**Content overlap:** `README.md`, `SETUP_GUIDE.md`, `USER_GUIDE.md`, and `GUIDE.md` have significant content duplication. The MkDocs site should consolidate these into a single source of truth.

## 4. Authentication Mechanism

Confirmed from `JwtService.java` and `AuthController.java`:

- **Issuance:** POST `/api/auth/login` with `{ username, password }`. Returns `{ token, role, passwordExpired }`
- **Algorithm:** HS256 (HMAC-SHA256)
- **Secret:** Config property `orchestrator.security.jwt-secret`, default: `default-secret-key-must-be-at-least-32-chars!!`
- **Expiry:** Config property `orchestrator.security.jwt-expiry-hours`, default: **8 hours**
- **Header format:** `Authorization: Bearer <token>` (standard Spring Security)
- **Claims:** `sub` = username, `role` = ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER
- **Token refresh:** GET `/api/auth/me` generates a new token

## 5. CI Pipeline

**CONFIRMED ABSENT.** `.github/workflows/` directory does not exist. No CI pipeline is configured.

## 6. Angular Build/Test Commands

From `pom.xml` `frontend-maven-plugin` configuration:
- Working directory: `orchestrator-ui`
- Node version: v22.22.3 (auto-installed)
- Build command: `npm run build`
- Skip flag: `<skip>true</skip>` is set by default in pom.xml

From `SETUP_GUIDE.md`:
- Dev server: `cd orchestrator-ui && ng serve` (port 4200, proxies to backend 8080)
- Test command: `ng test` (referenced in planv3 Phase 7 task checklist)

**[ASSUMED]** `ng test` works as documented — no explicit Karma/Jasmine config was verified. The CI pipeline should include a smoke-test fallback if the full test suite is flaky.

## 7. Java Version

**Confirmed:** `<java.version>21</java.version>` in `pom.xml`. Spring Boot 4.1.0 parent.

## 8. Existing CLI Scripts

From `SETUP_GUIDE.md`, shell scripts already exist:
- `scripts/auth.sh` / `scripts/auth.bat` — login and cache token
- `scripts/run-job.bat` / implied `run_job` (bash) — trigger job by name
- `scripts/run-step.bat` / implied `run_step` (bash) — trigger step by name
- Environment variables: `ORCHESTRATOR_URL`, `ORCHESTRATOR_USER`, `ORCHESTRATOR_PASS`, `ORCHESTRATOR_TOKEN`

These are simple curl wrappers. The new CLI should replace or supersede these.

## 9. [NOT FOUND IN REPO] Items

- **No `CONTRIBUTING.md`** — does not exist
- **No `.github/ISSUE_TEMPLATE/`** — directory does not exist
- **No Dockerfile** — referenced in README as example only, not committed
- **No `mkdocs.yml`** — MkDocs site does not yet exist
