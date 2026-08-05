<!-- FILE: phase4-01-export-import-format-design.md -->
# Phase 4 — Export/Import Format Design

## JSON Schema for Exported Job Definition

The export format is a superset of `JobDefinitionResponse` with DAG dependencies included and team resolved by name. Uses camelCase field names (Java record + Jackson default).

```jsonc
{
  // --- Format metadata ---
  "formatVersion": "1.0",
  "exportedAt": "2026-07-30T14:30:00Z",          // ISO 8601 timestamp
  "exportedFrom": "orchestrator-v0.0.1",         // application version for provenance

  // --- Job metadata (matches JOB_DEFINITION columns) ---
  "jobId": 42,                                    // included for provenance; ignored on import
  "jobName": "nightly-etl-pipeline",              // stable identifier — UNIQUE in target env
  "description": "Nightly ETL: extract, transform, load",
  "workingDir": "/opt/orchestrator/work/etl",
  "javaHome": "/usr/lib/jvm/java-21",             // nullable → omitted if null (@JsonInclude(NON_NULL))
  "classpathEntries": ["lib/*.jar", "lib/ext/*.jar"], // nullable → omitted if null
  "enabled": true,

  // --- Team context (informational on export, remappable on import) ---
  "teamName": "Data Engineering",                 // team name, not ID

  // --- Steps (matches JOB_STEP + parsed config) ---
  "steps": [
    {
      "stepName": "extract-data",                 // stable identifier within job — UNIQUE per job
      "stepOrder": 1,                             // retained for backward compat
      "stepType": "DB_QUERY",                     // matches registered step type key
      "stepConfig": {                             // parsed JSON object (not string)
        "query": "SELECT * FROM staging",
        "outputFile": "extract.csv"
      },
      "continueOnFailure": false,
      "enabled": true
    },
    {
      "stepName": "upload-to-sftp",
      "stepOrder": 2,
      "stepType": "SFTP",
      "stepConfig": {
        "host": "sftp.example.com",
        "port": 22,
        "username": "deployer",
        "credentialRef": "prod-sftp-key",
        "remoteDir": "/incoming",
        "filePattern": "*.csv",
        "direction": "UPLOAD"
      },
      "continueOnFailure": false,
      "enabled": true
    }
  ],

  // --- DAG Dependencies (references steps by stepName, not DB ID) ---
  "dependencies": [
    {
      "stepName": "upload-to-sftp",               // the dependent step
      "dependsOnStepName": "extract-data",        // the prerequisite step
      "edgeCondition": "ON_SUCCESS"               // ON_SUCCESS | ON_FAILURE | ALWAYS
    }
  ],

  // --- Environment Variables (matches JOB_ENV_VAR) ---
  "envVars": [
    { "key": "DB_URL", "value": "jdbc:oracle:thin:@//dbhost:1521/orcl", "isGlobal": false },
    { "key": "LOG_LEVEL", "value": "INFO", "isGlobal": true }
  ],

  // --- Schedule (matches JOB_SCHEDULE — omitted if null via @JsonInclude(NON_NULL)) ---
  "schedule": {
    "cronExpression": "0 2 * * *",               // every day at 2 AM
    "enabled": false                              // disabled by default on import to prevent accidental runs
  },

  // --- Custom metadata (user-defined key-value pairs) ---
  "metadata": {
    "owner": "data-team",
    "slack-channel": "#alerts-etl"
  }
}
```

## Field-by-Field Specification

### Top-Level Fields

| Field | Type | Required | Export Source | Import Behavior |
|-------|------|----------|---------------|-----------------|
| `formatVersion` | string (semver) | Yes (generated on export) | Hardcoded `"1.0"` | Validated: must be ≤ current supported version. Unknown future versions rejected with clear error. |
| `exportedAt` | string (ISO 8601) | Yes (generated on export) | Current timestamp | Ignored on import. |
| `exportedFrom` | string | Yes (generated on export) | `SpringApplication.getApplicationVersion()` or fallback `"unknown"` | Logged on import for audit trail. Not stored. |
| `jobId` | number | No (generated on export) | `JOB_DEFINITION.JOB_ID` | **Ignored on import** — new ID assigned by DB identity column. |
| `jobName` | string, 1-200 chars | Yes | `JOB_DEFINITION.JOB_NAME` | Looked up by name for conflict resolution. UNIQUE constraint enforced. |
| `description` | string, 0-1000 chars | No | `JOB_DEFINITION.DESCRIPTION` | Set as-is on create/update. |
| `workingDir` | string, 1-500 chars | Yes | `JOB_DEFINITION.WORKING_DIR` | Set as-is. Caller responsible for path validity in target env. |
| `javaHome` | string or null | No | `JOB_DEFINITION.JAVA_HOME` | Set as-is or null. Omitted if null on export (`@JsonInclude(NON_NULL)`). |
| `classpathEntries` | array of strings or null | No | Parsed from `JOB_DEFINITION.CLASSPATH` JSON | Serialized to JSON and stored in CLASSPATH column. Empty array → null. |
| `enabled` | boolean | No (default: true) | `'Y'.equals(ENABLED)` | Set on create/update. **Default false on import** for safety — user must explicitly enable. |
| `teamName` | string or null | No | Resolved from `TEAM.TEAM_NAME` via FK | If present and non-null, looked up by name; if not found, error. If null, uses importer's active team. |
| `metadata` | map or null | No | User-defined metadata on job | Passed through as-is. |

### Steps Array

Each step object maps to a row in `JOB_STEP`. **No internal DB IDs** (`stepId`, `jobId`) are included. Steps are identified within the job by `stepName`.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `stepName` | string, 1-200 chars | Yes | Must be unique within the job's step list. Used as the stable identifier for dependency edges. |
| `stepOrder` | int | No (retained) | Retained for backward compatibility. On import, reassigned sequentially from array order. |
| `stepType` | string, 1-50 chars | Yes | Validated against registered step types in target environment on import. |
| `stepConfig` | object or null | No | **Parsed JSON object** (not a string). On export, the CLOB is parsed into a Jackson node and serialized as a nested object. On import, accepted as an object and re-serialized to JSON for storage. |
| `continueOnFailure` | boolean | No (default: false) | Maps to CHAR(1) 'Y'/'N' in DB. |
| `enabled` | boolean | No (default: true) | Maps to CHAR(1) 'Y'/'N' in DB. |

**Deviation from plan:** The original design specified `stepConfig` as an opaque JSON string (`String`). The implementation parses it into a Jackson `ObjectNode` on export, producing a nested object in the output. This is cleaner for consumers but means malformed JSON in the CLOB will cause export to fail for that step (vs. passing through verbatim).

### Dependencies Array

Each dependency object maps to a row in `JOB_STEP_DEPENDENCY`. Steps are referenced by **name**, not DB ID.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `stepName` | string | Yes | The dependent step's name (must exist in the steps array). |
| `dependsOnStepName` | string | Yes | The prerequisite step's name (must exist in the steps array). |
| `edgeCondition` | enum: ON_SUCCESS, ON_FAILURE, ALWAYS | No (default: ON_SUCCESS) | Validated against enum values. |

### EnvVars Array

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `key` | string, 1-200 chars | Yes | Environment variable name. |
| `value` | string, 1-2000 chars | Yes | Environment variable value. Stored in plaintext in export — same as current API behavior. Consider marking sensitive vars for future masking. |
| `isGlobal` | boolean | No (default: false) | Whether this env var applies to all jobs. On import, global vars are skipped with a warning if the importer lacks ADMIN role. |

### Schedule Object (nullable)

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `cronExpression` | string, 1-100 chars | Yes (if schedule present) | Standard cron expression. |
| `enabled` | boolean | No (default: false on import) | **Always disabled on import** regardless of exported value — prevents accidental scheduled runs in new environments. |

## Names vs Internal IDs

### Export Direction
- `jobId` → **included** for provenance, but ignored on import (new ID assigned by DB)
- `stepId` → omitted entirely. Steps are identified by `stepName`.
- `dependencyId` → omitted entirely. Dependencies reference steps by name.
- `envVarId` → omitted entirely. Env vars are positional within the array.
- `scheduleId` → omitted entirely. Schedule is nested under the job.
- `teamId` → resolved to `teamName` via FK lookup.

### Import Direction
1. **Job lookup:** `jobName` is used to find an existing job (for conflict resolution). New jobs get a DB-generated `JOB_ID`.
2. **Step creation:** Steps are created in array order, each getting a new DB-generated `STEP_ID`. A name-to-ID map is built during import for dependency resolution.
3. **Dependency resolution:** `stepName` and `dependsOnStepName` are resolved to the newly created `STEP_ID`s via the name-to-ID map.
4. **Env var creation:** Each env var gets a new DB-generated `ENV_ID`.
5. **Schedule creation:** Gets a new DB-generated `SCHEDULE_ID`.

## Credential References in Export

Credentials appear inside `stepConfig` (a parsed JSON object) as field values with type `SECRET_REF` per the step type's schema. For example, the SFTP executor has:

```json
{"credentialRef": "prod-sftp-key", ...}
```

- **Export:** The stepConfig is parsed from CLOB and serialized as a nested object. It contains only the credential reference name (e.g., `"prod-sftp-key"`), never the decrypted value.
- **Import validation:** After parsing stepConfig, if any field marked as `SECRET_REF` in the step type's schema has a value, that value is looked up against `JOB_CREDENTIAL.CREDENTIAL_REF` in the target environment. If not found, import fails with a specific error listing the missing credential names.
- **Import storage:** The stepConfig object is re-serialized to JSON and stored as-is in the CLOB. No transformation of credential references occurs during import.

## YAML Representation

The YAML format is a direct serialization of the same JSON structure using Jackson's `YAMLFactory`. No structural differences — just syntax:

```yaml
formatVersion: "1.0"
exportedAt: "2026-07-30T14:30:00Z"
jobId: 42
jobName: nightly-etl-pipeline
description: "Nightly ETL: extract, transform, load"
workingDir: /opt/orchestrator/work/etl
javaHome: /usr/lib/jvm/java-21
classpathEntries:
  - lib/*.jar
  - lib/ext/*.jar
enabled: true
teamName: "Data Engineering"
steps:
  - stepName: extract-data
    stepOrder: 1
    stepType: DB_QUERY
    stepConfig:
      query: "SELECT * FROM staging"
      outputFile: extract.csv
    continueOnFailure: false
    enabled: true
  - stepName: upload-to-sftp
    stepOrder: 2
    stepType: SFTP
    stepConfig:
      host: sftp.example.com
      port: 22
      username: deployer
      credentialRef: prod-sftp-key
      remoteDir: /incoming
      filePattern: "*.csv"
      direction: UPLOAD
    continueOnFailure: false
    enabled: true
dependencies:
  - stepName: upload-to-sftp
    dependsOnStepName: extract-data
    edgeCondition: ON_SUCCESS
envVars:
  - key: DB_URL
    value: "jdbc:oracle:thin:@//dbhost:1521/orcl"
    isGlobal: false
schedule:
  cronExpression: "0 2 * * *"
  enabled: false
```

**Implementation:** Jackson `ObjectMapper` with `YAMLFactory` for YAML, standard `ObjectMapper` for JSON. Same DTO classes, different serializers. Note that in YAML format, `stepConfig` is a nested object (not a quoted string) — this is more readable than the planned string-based approach.

## Import Request Structure

The import endpoint accepts a **flat** request body (not nested):

```jsonc
{
  "mode": "error",                    // "error" | "update" | "skip" — conflict resolution mode
  "formatVersion": "1.0",            // same fields as the export format...
  "jobId": null,                     // ignored on import
  "jobName": "nightly-etl-pipeline",
  "description": "...",
  "workingDir": "/opt/orchestrator/work/etl",
  "javaHome": "/usr/lib/jvm/java-21",
  "classpathEntries": ["lib/*.jar"],
  "enabled": false,
  "teamName": "Data Engineering",
  "steps": [...],
  "dependencies": [...],
  "envVars": [...],
  "schedule": {...},
  "metadata": {...}
}
```

| Field | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `mode` | enum string | No | `"error"` | `"error"` — reject if job name exists. `"update"` — update existing job's steps, envVars, schedule. Create version snapshot of current state before overwriting. `"skip"` — return 200 with a message indicating the job was skipped. |
| All other fields | same as export | Per field spec | — | Same schema as `JobExport`, but with nullable types for optional fields (Import DTOs use wrapper types: `Integer`, `Boolean` vs primitives). |

**Deviation from plan:** The original design had a nested `{mode, definition: {...}}` envelope. The implementation uses a flat structure where `mode` sits alongside the job fields. This is simpler to construct from curl/CLI but means the import DTO has more fields than strictly necessary.
