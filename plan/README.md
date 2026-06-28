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

| # | File | Deliverable | Effort |
|---|------|-------------|--------|
| 1 | [PHASE-1-Foundation.md](./PHASE-1-Foundation.md) | DB schema, JPA entities, Flyway migrations | Small |
| 2 | [PHASE-2-Engine.md](./PHASE-2-Engine.md) | Job execution engine — all 5 step types | Large |
| 3 | [PHASE-3-API.md](./PHASE-3-API.md) | REST API + SSE live log streaming | Medium |
| 4 | [PHASE-4-Scheduling.md](./PHASE-4-Scheduling.md) | Dynamic cron scheduling engine | Small |
| 5 | [PHASE-5-UI.md](./PHASE-5-UI.md) | Angular UI — all modules | Large |
| 6 | [PHASE-6-Security.md](./PHASE-6-Security.md) | JWT auth, RBAC, credential store | Medium |
| 7 | [PHASE-7-Deploy.md](./PHASE-7-Deploy.md) | Observability, systemd packaging, hardening | Small |

---

## Recommended Build Order

```
Phase 1 → Phase 2 → Phase 3
                  (test with Postman / curl before continuing)
                         ↓
                    Phase 4 → Phase 5 → Phase 6 → Phase 7
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
