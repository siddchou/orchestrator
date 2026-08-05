<!-- FILE: phase7-03-docs-site-structure.md -->

# Phase 7.3 — MkDocs Site Structure

## Site Map

```
docs-site/
├── index.md                        # Landing page: project overview, stack table, quick start link
├── getting-started/
│   ├── installation.md             # Prerequisites, build, first run
│   ├── configuration.md            # Environment variables, Spring profiles, application.yml reference
│   └── troubleshooting.md          # Common issues (build failures, Flyway errors, port conflicts)
├── user-guide/
│   ├── overview.md                 # What is a job? Step types, run statuses, execution model
│   ├── jobs.md                     # Create/edit/delete jobs, add steps, reorder, env vars
│   ├── runs.md                     # Trigger runs, view history, live logs, cancel
│   ├── scheduling.md               # Cron schedules, validate expression, enable/disable
│   └── teams.md                    # Team memberships, multi-tenancy, X-Team-Id header
├── admin/
│   ├── roles-permissions.md        # RBAC matrix (ADMIN/OPERATOR/VIEWER)
│   ├── credentials.md              # Credential management, SSH key generation
│   ├── global-config.md            # Global env vars, system health
│   └── audit-log.md                # Audit log endpoint, what gets logged
├── features/
│   ├── step-types.md               # Reference for each step type (ENV_SETUP, JAVA_EXEC, SFTP, LOG_CLEANUP, ARCHIVE) with config schema
│   ├── import-export.md            # Job export/import formats (JSON/YAML), version management, rollback
│   ├── notifications.md            # Notification subscriptions, channels (Mail), delivery log
│   ├── observability.md            # Prometheus metrics, Grafana dashboard, OTel tracing
│   └── versions.md                 # Job versioning, list versions, rollback workflow
├── developer/
│   ├── api-reference.md            # Links to Swagger UI (`/swagger-ui/index.html`), auto-generated spec link
│   ├── cli-reference.md            # orchestrator-cli command reference (mirrors phase7-01-cli-design.md)
│   ├── plugin-development.md       # How to add a new step type (from existing docs/plugin-development.md)
│   └── architecture.md             # Project structure, key classes, execution flow diagram
├── deployment/
│   ├── packaging.md                # Fat JAR build, Dockerfile example
│   ├── systemd.md                  # OS user setup, systemd service file, environment file
│   └── upgrade-procedure.md        # Stop → backup → deploy → start → validate
└── contribution/
    └── contributing.md             # Build instructions for contributors, PR checklist, coding standards
```

## Page Purposes (One-Line Each)

| Page | Purpose |
|------|---------|
| `index.md` | Project elevator pitch, stack table, links to Getting Started and API Reference |
| `installation.md` | Step-by-step from git clone to running instance |
| `configuration.md` | Every environment variable, Spring profile, and application.yml property |
| `troubleshooting.md` | Searchable FAQ for build, runtime, and auth issues |
| `overview.md` | Mental model: jobs → steps → runs, with status lifecycle diagram |
| `jobs.md` | Full job CRUD walkthrough with screenshots |
| `runs.md` | Run history, filtering, live log viewer, cancel workflow |
| `scheduling.md` | Cron expression reference, schedule management UI |
| `teams.md` | Multi-tenancy model, team assignment, active team selection |
| `roles-permissions.md` | Permission matrix per role per endpoint |
| `credentials.md` | Encrypted credential store, SSH key pair generation |
| `global-config.md` | System-wide env vars and health check page |
| `audit-log.md` | What actions are audited, how to query the log |
| `step-types.md` | Config schema for each step type with examples |
| `import-export.md` | JSON/YAML export format, import modes (CREATE/UPDATE), version snapshot on import |
| `notifications.md` | Subscription model, event types, channel configuration (Mail) |
| `observability.md` | Metrics endpoints, Grafana dashboard JSON, OTel span structure |
| `versions.md` | Version numbering, label convention, rollback procedure |
| `api-reference.md` | Points to live Swagger UI; summarizes auth flow and response envelope |
| `cli-reference.md` | Complete CLI command reference with examples |
| `plugin-development.md` | StepExecutor interface, registration, config schema, testing pattern |
| `architecture.md` | Package layout, key interfaces (StepExecutorRegistry, JobLaunchService), thread model |
| `packaging.md` | Maven build profiles, fat JAR contents, Dockerfile template |
| `systemd.md` | Production deployment with systemd, file permissions, journal log access |
| `upgrade-procedure.md` | Rolling upgrade steps, rollback on failure |
| `contributing.md` | How to contribute: fork → branch → PR, test requirements, code style |

## Doc Migration Matrix

| Source File | Content Moves To | Action on Source |
|-------------|-----------------|------------------|
| `README.md` | `index.md` (overview), `getting-started/installation.md` (quick start), `user-guide/overview.md` (step types) | Replace with 3-line blurb + link to docs-site |
| `SETUP_GUIDE.md` | `getting-started/installation.md`, `getting-started/configuration.md`, `getting-started/troubleshooting.md` | Delete (full content migrated) |
| `USER_GUIDE.md` | `user-guide/overview.md`, `jobs.md`, `runs.md`, `scheduling.md`, `admin/global-config.md`, `developer/api-reference.md` | Delete (full content migrated) |
| `GUIDE.md` | `getting-started/installation.md` (dup of SETUP_GUIDE), `deployment/systemd.md`, `deployment/upgrade-procedure.md`, `developer/api-reference.md` | Delete (full content migrated; deployment section is unique and most detailed) |
| `HELP.md` | Nowhere — Spring Boot default stub with no project-specific content | **Delete** |
| `docs/plugin-development.md` | `developer/plugin-development.md` | Move (rename path) |
| `docs/observability/README.md` | `features/observability.md` | Move (rename path) |
| `orchestrator-ui/README.md` | `developer/architecture.md` (frontend section) | Keep in orchestrator-ui/ as-is (frontenders may not navigate to docs-site) |
| `plan/*.md`, `planv2/*.md`, `planv3/*.md` | Not migrated — these are internal planning artifacts, not user-facing docs | **Keep unchanged** |

## MkDocs Configuration

```yaml
site_name: Novakai Orchestrator
site_description: Job orchestration platform for multi-step workflows
theme:
  name: material
  palette:
    primary: blue grey
    accent: indigo
  font:
    text: Roboto
    code: Roboto Mono
  features:
    - navigation.tabs
    - navigation.sections
    - search.highlight
    - content.code.copy
nav:
  - Home: index.md
  - Getting Started:
      - Installation: getting-started/installation.md
      - Configuration: getting-started/configuration.md
      - Troubleshooting: getting-started/troubleshooting.md
  - User Guide:
      - Overview: user-guide/overview.md
      - Jobs: user-guide/jobs.md
      - Runs: user-guide/runs.md
      - Scheduling: user-guide/scheduling.md
      - Teams: user-guide/teams.md
  - Administration:
      - Roles & Permissions: admin/roles-permissions.md
      - Credentials: admin/credentials.md
      - Global Config: admin/global-config.md
      - Audit Log: admin/audit-log.md
  - Features:
      - Step Types: features/step-types.md
      - Import / Export: features/import-export.md
      - Notifications: features/notifications.md
      - Observability: features/observability.md
      - Versions: features/versions.md
  - Developer:
      - API Reference: developer/api-reference.md
      - CLI Reference: developer/cli-reference.md
      - Plugin Development: developer/plugin-development.md
      - Architecture: developer/architecture.md
  - Deployment:
      - Packaging: deployment/packaging.md
      - Systemd: deployment/systemd.md
      - Upgrade Procedure: deployment/upgrade-procedure.md
  - Contributing: contribution/contributing.md
plugins:
  - search
  - mkdocstrings:
      handlers:
        python:
          options:
            show_source: false
markdown_extensions:
  - admonition
  - codehilite
  - pymdownx.highlight
  - pymdownx.superfences
  - tables
  - toc:
      permalink: true
```
