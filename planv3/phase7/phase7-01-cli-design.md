<!-- FILE: phase7-01-cli-design.md -->

# Phase 7.1 — CLI Design

## Technology Choice: Picocli (Java)

**Decision:** Build the CLI as a standalone Java module using [Picocli](https://picocli.info/) v4.7+.

**Justification against confirmed Java 21:**
- Picocli v4.7+ has native Java 21 support (pattern matching, virtual threads compatible).
- The project is already a Java 21 / Maven monorepo; adding `orchestrator-cli/` as a child module shares DTOs and avoids duplicating auth logic in bash.
- Existing shell scripts (`scripts/auth.sh`, `run-job.bat`) are fragile (no retry, no JSON parsing, Windows-only `.bat`). A Java CLI works cross-platform.
- Picocli's `CommandLine.Runners` and `@Mixin` make it trivial to share `--server`, `--token`, and `--json` flags across all commands.
- Picocli auto-generates `--help`, tab completion scripts, and man pages.

**Build output:** Fat JAR via `spring-boot-maven-plugin` (reusing the existing plugin config), executable as `java -jar orchestrator-cli/target/orchestrator-cli-*.jar`.

## Module Structure

```
orchestrator-cli/
├── pom.xml                          # Picocli + Jackson + Spring Web (RestTemplate)
└── src/main/java/com/novakai/orch/cli/
    ├── OrchCli.java                 # Main entry point, Picocli command mixin
    ├── config/
    │   └── CliConfig.java           # Server URL, token, output format
    ├── auth/
    │   └── AuthCommand.java         # login, token-cache management
    ├── jobs/
    │   ├── JobsListCommand.java
    │   ├── JobsRunCommand.java
    │   ├── JobsExportCommand.java
    │   └── JobsImportCommand.java
    ├── runs/
    │   ├── RunsListCommand.java
    │   └── RunsTailCommand.java     # SSE consumer
    └── notifications/
        └── NotificationsListCommand.java
```

## Authentication Handling

The CLI supports two auth modes:

1. **Token via environment variable** (primary for CI/scripts):
   ```bash
   export ORCHESTRATOR_TOKEN="eyJhbGciOiJIUzI1NiJ9..."
   orch jobs list
   ```

2. **Interactive login** (for ad-hoc use):
   ```bash
   orch login --user admin --password changeme
   # Token cached in ~/.orchestrator/token for subsequent commands
   ```

Token cache location: `~/.orchestrator/token` (plaintext, file mode 0600). Cache expires when the server returns 401; CLI prompts for re-login.

Server URL defaults to `http://localhost:8080`, overridable via `--server` flag or `ORCHESTRATOR_URL` env var.

## Output Format

All commands default to **human-readable table** output. Adding `--json` flag emits raw JSON (the `ApiResponse<T>` envelope unwrapped to just the `data` field).

Example:
```bash
$ orch jobs list
ID   NAME          STATUS    STEPS  SCHEDULE
1    DailyETL      enabled   4      0 2 * * *
2    WeeklyReport  disabled  2      -

$ orch jobs list --json
[{"jobId":1,"jobName":"DailyETL","enabled":"Y",...}, ...]
```

## Command Reference

### `orch login`
Authenticate and cache token.

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--user`, `-u` | Yes | — | Username |
| `--password`, `-p` | No | prompts stdin | Password (stdin if omitted) |
| `--server`, `-s` | No | `ORCHESTRATOR_URL` or `http://localhost:8080` | API base URL |

**Exit code:** 0 on success, 1 on auth failure. Prints token to stdout.

---

### `orch jobs list`
List job definitions with pagination.

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--page`, `-p` | No | 0 | Page number |
| `--size`, `-s` | No | 20 | Page size |
| `--search`, `-q` | No | — | Filter by job name (substring) |
| `--json` | No | false | Output raw JSON |

---

### `orch jobs run`
Trigger a job run. Returns immediately with the run ID.

| Flag/Argument | Required | Default | Description |
|---------------|----------|---------|-------------|
| `<job-id-or-name>` | Yes | — | Job ID (numeric) or name (string). CLI detects type and routes to `/api/jobs/{id}/run` or `/api/jobs/name/{name}/run` |
| `--param`, `-P` | No | — | Key=value parameter. Repeatable: `-P key1=val1 -P key2=val2` |
| `--wait`, `-w` | No | false | Poll `/api/runs/{runId}` until terminal status (SUCCESS/FAILED/PARTIAL/CANCELLED). Default poll interval: 2s |
| `--json` | No | false | Output raw JSON |

**Exit code:** 0 if run triggered successfully. When `--wait` is used, exit code reflects final run status (0=SUCCESS, 1=FAILED/PARTIAL, 2=CANCELLED).

---

### `orch jobs export`
Export a job definition to file or stdout.

| Flag/Argument | Required | Default | Description |
|---------------|----------|---------|-------------|
| `<job-id>` | Yes | — | Job ID |
| `--format`, `-f` | No | `json` | Export format: `json` or `yaml` |
| `--output`, `-o` | No | stdout | File path to write export. If omitted, writes to stdout |

---

### `orch jobs import`
Import a job definition from file.

| Flag/Argument | Required | Default | Description |
|---------------|----------|---------|-------------|
| `<file>` | Yes | — | Path to exported JSON/YAML file |
| `--team-id` | No | auto-resolve | Target team ID (X-Team-Id header) |

---

### `orch runs list`
List job run history.

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--job`, `-j` | No | — | Filter by job ID |
| `--status` | No | — | Filter by status: SUCCESS, FAILED, PARTIAL, CANCELLED |
| `--from` | No | — | Start date (YYYY-MM-DD) |
| `--to` | No | — | End date (YYYY-MM-DD) |
| `--page`, `-p` | No | 0 | Page number |
| `--size`, `-s` | No | 20 | Page size |
| `--json` | No | false | Output raw JSON |

---

### `orch runs tail`
Stream live logs for a running job via SSE.

| Flag/Argument | Required | Default | Description |
|---------------|----------|---------|-------------|
| `<run-id>` | Yes | — | Run ID to tail |
| `--follow`, `-f` | No | true | Keep connection open and stream logs. If false, fetches accumulated log via `/api/runs/{runId}/steps/{stepId}/log` |

**Behavior:** Connects to `/api/runs/{runId}/log-stream`. Each SSE event is printed as it arrives with a timestamp prefix. Exits when the server sends `done` event or on connection error.

```bash
$ orch runs tail 42 -f
[10:23:01] [STEP-1/Build] Compiling module A...
[10:23:05] [STEP-1/Build] Build successful (4s)
[10:23:06] [STEP-2/Deploy] Uploading artifacts...
[10:23:12] done: RUN_COMPLETE
```

---

### `orch notifications list`
List notification subscriptions.

| Flag | Required | Default | Description |
|------|----------|---------|-------------|
| `--job`, `-j` | No | — | Filter by job ID (uses `/api/notifications/subscriptions/job/{jobId}`). If omitted, lists all |
| `--json` | No | false | Output raw JSON |

**Note:** Requires ADMIN role. CLI should surface a clear error if the token lacks ADMIN permission.

---

### Global Mixin Flags

Available on every command via Picocli `@Mixin`:

| Flag | Description |
|------|-------------|
| `--server`, `-s` `<url>` | Override API base URL (env: `ORCHESTRATOR_URL`) |
| `--token` `<jwt>` | Override JWT token (env: `ORCHESTRATOR_TOKEN`) |
| `--json` | Force JSON output on all commands |
| `--verbose`, `-v` | Print HTTP request/response details for debugging |
