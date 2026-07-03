# User Guide — Novakai Orchestrator

A job orchestration platform for defining, scheduling, and monitoring multi-step jobs through a web dashboard.

---

## Table of Contents

- [Overview](#overview)
- [Logging In](#logging-in)
- [Roles and Permissions](#roles-and-permissions)
- [Dashboard](#dashboard)
- [Jobs](#jobs)
- [Job Runs](#job-runs)
- [Scheduling](#scheduling)
- [Global Configuration](#global-configuration)
- [CLI Usage](#cli-usage)
- [API Reference](#api-reference)

---

## Overview

The orchestrator manages **jobs**, each composed of ordered **steps**. Steps execute sequentially. If a step fails and `Continue on Failure` is disabled (default), the entire run stops. If enabled, remaining steps continue and the final status becomes `PARTIAL`.

### Step Types

| Type | What it does |
|---|---|
| `ENV_SETUP` | Sets environment variables for subsequent steps |
| `JAVA_EXEC` | Runs a Java class or JAR with configurable classpath, JVM args, timeout |
| `SFTP` | Transfers files to/from a remote SFTP server |
| `LOG_CLEANUP` | Cleans up old log files |
| `ARCHIVE` | Archives job output files (tar/gzip) |

### Run Statuses

| Status | Meaning |
|---|---|
| `SUCCESS` | All steps completed successfully |
| `FAILED` | A step failed and `continueOnFailure` was disabled |
| `PARTIAL` | One or more steps failed but `continueOnFailure` was enabled |
| `CANCELLED` | The run was manually cancelled |

---

## Logging In

Open `http://your-server:8080` in your browser. Enter your username and password.

On first login, you'll be prompted to change the default password. Choose a new password and confirm.

---

## Roles and Permissions

| Role | Can view jobs | Can view runs | Can execute jobs | Can create/edit jobs | Can manage config | Can manage credentials | Can view audit log |
|---|---|---|---|---|---|---|---|
| **ADMIN** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **OPERATOR** | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ |
| **VIEWER** | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |

---

## Jobs

### Job List

Navigate to **Jobs** from the sidebar. The list shows all job definitions with their name, enabled status, step count, and schedule.

- **Search** — filter jobs by name
- **Enable / Disable** — toggle the switch next to any job. Disabled jobs cannot be triggered

### Create a New Job

Click **New Job** and fill in:

| Field | Description |
|---|---|
| **Name** | Unique job identifier |
| **Description** | Free-text description |
| **Java Home** | (Optional) Custom Java home for this job |
| **Classpath** | (Optional) Default classpath for Java steps |

### Add Steps

After creating a job, open it from the job list and click **Add Step**. Each step requires:

| Field | Description |
|---|---|
| **Name** | Unique step name within the job |
| **Type** | Step type (`ENV_SETUP`, `JAVA_EXEC`, `SFTP`, `LOG_CLEANUP`, `ARCHIVE`) |
| **Config** | JSON configuration specific to the step type |
| **Continue on Failure** | Whether to keep running subsequent steps if this one fails |
| **Timeout** | (Optional) Maximum execution time |

**Reorder steps** by dragging them in the step list. Steps execute in the displayed order.

### Job-Specific Environment Variables

From the job detail page, add environment variables that are scoped to that job only. These are available to all steps within the job.

### Execute a Job

- Click the **Run** button on the job detail page
- Or use the **Run Job** dialog from the job list to trigger any job by name
- Or trigger from CLI: `curl -X POST http://localhost:8080/api/jobs/name/MyJob/run`

### Cancel a Running Job

From the run detail page, click **Cancel** to stop a running job. The status changes to `CANCELLED`.

---

## Job Runs

### Run History

Navigate to **Runs** from the sidebar. The list shows all past and current job runs.

- **Filter by job** — select a specific job from the dropdown
- **Filter by status** — show only `SUCCESS`, `FAILED`, `PARTIAL`, or `CANCELLED` runs
- **Filter by date** — narrow to a date range

### Run Detail

Click any run to see:

- Job name and overall status
- Start time, end time, duration
- Each step's status, exit code, and log output
- **Live log stream** — while a run is active, logs stream in real time via Server-Sent Events (SSE)

### Step Logs

From the run detail page, expand any step to view its log output. For completed runs, the full log is shown. For running jobs, the log updates live.

---

## Scheduling

### Add a Schedule

From the job detail page, click **Add Schedule** and enter:

| Field | Description |
|---|---|
| **Cron expression** | Standard 5-field cron (`minute hour dayOfMonth month dayOfWeek`) |
| **Enabled** | Whether the schedule is active |

**Examples:**

| Cron | Fires |
|---|---|
| `0 9 * * *` | Every day at 9:00 AM |
| `0 9 * * 1-5` | Weekdays at 9:00 AM |
| `30 8 * * 1` | Every Monday at 8:30 AM |
| `0 0 1 * *` | First of every month at midnight |

### Validate a Cron Expression

Before saving, use the **Validate** button to check the expression and see the next 3 scheduled fire times.

### Enable / Disable a Schedule

Toggle the switch next to any schedule. Disabling a schedule prevents future fires but does not cancel a run already in progress.

---

## Global Configuration

Available to **ADMIN** users only.

### Global Environment Variables

Set environment variables that apply to **all** jobs. Job-specific env vars override global ones with the same name.

### System Health

The **System** page shows:

- Application uptime
- Java home validation
- Working directory status
- Thread pool usage

---

## CLI Usage

### Run a Job

```bash
# Linux
source scripts/auth.sh
run_job "MyJob"

# Windows
call scripts\auth.bat
call scripts\run-job.bat "MyJob"
```

The script authenticates, triggers the job, and polls until completion.

### Run a Single Step

```bash
# Linux
run_step "MyStep"

# Windows
call scripts\run-step.bat "MyStep"
```

### Skip Login with Token

If you already have a JWT token, set `ORCHESTRATOR_TOKEN` to skip authentication:

```bash
export ORCHESTRATOR_TOKEN="eyJhbGciOiJIUzI1NiJ9..."
run_job "MyJob"
```

---

## API Reference

All API responses are wrapped in `{ "success": boolean, "data": ..., "error": "..." }`.

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Login, returns JWT token |
| `POST` | `/api/auth/change-password` | Change current user's password |
| `GET` | `/api/auth/me` | Get current user info, refresh token |

### Jobs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/jobs` | List jobs (paginated, searchable) |
| `GET` | `/api/jobs/{id}` | Get job detail with steps |
| `POST` | `/api/jobs` | Create job (ADMIN) |
| `PUT` | `/api/jobs/{id}` | Update job (ADMIN) |
| `DELETE` | `/api/jobs/{id}` | Delete job (ADMIN) |
| `POST` | `/api/jobs/{id}/enable` | Toggle enabled (ADMIN) |
| `POST` | `/api/jobs/{id}/run` | Trigger job (OPERATOR+) |
| `POST` | `/api/jobs/name/{name}/run` | Trigger by name (OPERATOR+) |

### Runs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/runs` | List runs (paginated, filterable) |
| `GET` | `/api/runs/{runId}` | Get run detail with steps |
| `GET` | `/api/runs/{runId}/log-stream` | Live SSE log stream |
| `POST` | `/api/runs/{runId}/cancel` | Cancel a running job |

### System

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/system/health` | Health check (public) |
| `GET` | `/api/system/cron-validate` | Validate cron expression |

### Authentication

Include the JWT token in every request:

```
Authorization: Bearer <your-jwt-token>
```
