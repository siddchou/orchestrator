# Job Orchestration Platform — Migration Plan Index

> **Project:** Windows Batch → Linux Spring Boot Orchestrator  
> **Stack:** Java 21 · Spring Boot 3.x · Angular 17+ · Oracle 19c  
> **Target OS:** Linux

---

## Overview

This document set covers the full plan to migrate Windows CMD-driven batch jobs to a
configurable, UI-driven Spring Boot orchestration platform on Linux.

Each phase is self-contained and can be handed to an implementation team independently.
Complete phases in order — each builds on the previous.

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────┐
│                   Angular UI                        │
│  Dashboard · Job Manager · Run Monitor · Config     │
└────────────────────┬────────────────────────────────┘
                     │ REST / SSE
┌────────────────────▼────────────────────────────────┐
│              Spring Boot API Layer                  │
│  Job CRUD · Execution Trigger · Schedule · Auth     │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│              Job Execution Engine                   │
│  EnvSetup · LogCleanup · JavaExec · SFTP · Archive  │
└──────────┬─────────────────────────┬────────────────┘
           │                         │
┌──────────▼──────┐        ┌─────────▼──────────────┐
│   Oracle 19c    │        │  Linux Filesystem /     │
│  Job Config &   │        │  ProcessBuilder /       │
│  Run History    │        │  SFTP / Archives        │
└─────────────────┘        └────────────────────────┘
```

---

## Phase Summary

Phases 1 and 2 are single files. Phases 3–7 are split into focused sub-parts sized
for a ~50k token context window — each sub-part is independently implementable.

| # | File | Deliverable | Effort |
|---|------|-------------|--------|
| 1 | [PHASE-1-Foundation.md](./PHASE-1-Foundation.md) | DB schema, JPA entities, Flyway migrations | Small |
| 2 | [PHASE-2-Engine.md](./PHASE-2-Engine.md) | Job execution engine — all 5 step types | Large |
| 3a | [PHASE-3a-API-Envelope-DTOs.md](./PHASE-3a-API-Envelope-DTOs.md) | Response envelope, global exception handler, all DTOs | Small |
| 3b | [PHASE-3b-API-JobDefinition-Controller.md](./PHASE-3b-API-JobDefinition-Controller.md) | Job definition controller + service (CRUD, steps, env vars, schedule) | Medium |
| 3c | [PHASE-3c-API-Execution-SSE-System.md](./PHASE-3c-API-Execution-SSE-System.md) | Run trigger/query controller, SSE live log, system/global-config endpoints | Medium |
| 4a | [PHASE-4a-Scheduler-Core.md](./PHASE-4a-Scheduler-Core.md) | `JobSchedulerService`, `TaskScheduler` bean, startup registration | Small |
| 4b | [PHASE-4b-Scheduler-Integration.md](./PHASE-4b-Scheduler-Integration.md) | Service-layer integration, restart recovery, cron validation endpoint | Small |
| 5a | [PHASE-5a-UI-Setup-Models-Services.md](./PHASE-5a-UI-Setup-Models-Services.md) | Angular project setup, all TypeScript models, core services, interceptors | Small |
| 5b | [PHASE-5b-UI-Dashboard-JobList.md](./PHASE-5b-UI-Dashboard-JobList.md) | Dashboard component + Job list component | Small |
| 5c | [PHASE-5c-UI-JobDetail-StepForms.md](./PHASE-5c-UI-JobDetail-StepForms.md) | Job detail tabs (General, Steps, Env Vars, Schedule) + dynamic step forms | Medium |
| 5d | [PHASE-5d-UI-RunMonitor-LogViewer.md](./PHASE-5d-UI-RunMonitor-LogViewer.md) | Run list, run detail timeline, live log viewer (SSE) | Small |
| 5e | [PHASE-5e-UI-Routing-Config-Build.md](./PHASE-5e-UI-Routing-Config-Build.md) | Routing, global config, shared components, Maven build integration | Small |
| 6a | [PHASE-6a-Security-JWT-Auth.md](./PHASE-6a-Security-JWT-Auth.md) | JWT service, auth filter, security config, login endpoint, user table | Medium |
| 6b | [PHASE-6b-Security-RBAC-Credentials-Audit.md](./PHASE-6b-Security-RBAC-Credentials-Audit.md) | Role-based access, credential API + encryption, AOP audit logging | Medium |
| 7a | [PHASE-7a-Deploy-Packaging-Systemd.md](./PHASE-7a-Deploy-Packaging-Systemd.md) | Fat JAR build, directory layout, env file, systemd unit, upgrade procedure | Small |
| 7b | [PHASE-7b-Deploy-Observability-Hardening.md](./PHASE-7b-Deploy-Observability-Hardening.md) | Structured JSON logging, Actuator health indicators, Micrometer metrics, log retention | Small |

---

## Recommended Build Order

```
Phase 1 → Phase 2 → Phase 3a → Phase 3b → Phase 3c
                               (test with Postman before continuing)
                                      ↓
          Phase 4a → Phase 4b → Phase 5a → 5b → 5c → 5d → 5e
                                      ↓
                              Phase 6a → Phase 6b → Phase 7a → Phase 7b
```

---

## Key Design Decisions

- **No third-party scheduler** (no Quartz, no Jenkins) — Spring `TaskScheduler` is sufficient
  and keeps the deployment footprint minimal.
- **ProcessBuilder only** — never `Runtime.exec()`. All Java processes launched via
  `ProcessBuilder` with explicit environment maps.
- **Step config as JSON CLOB** — each step type carries a typed JSON payload in the DB.
  This allows new step types to be added without schema changes.
- **SSE for live logs** — Server-Sent Events stream stdout/stderr in real time to the UI
  without requiring WebSocket infrastructure.
- **Single fat JAR** — Angular static assets bundled inside the Spring Boot JAR.
  One artifact, one deployment.

---

## Prerequisites

Before starting Phase 1, ensure the following are available on the target Linux environment:

- [ ] Java 21 JDK installed (`JAVA_HOME` set)
- [ ] Oracle 19c accessible with a dedicated schema/user for the app
- [ ] Maven 3.9+ or Gradle 8+ available
- [ ] Node.js 20+ and npm (for Angular build)
- [ ] Network access from app server to SFTP target hosts
- [ ] A dedicated OS user account (e.g. `orchestrator`) for running the service
