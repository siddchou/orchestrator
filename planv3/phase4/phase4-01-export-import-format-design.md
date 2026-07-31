<!-- FILE: phase4-01-export-import-format-design.md -->
# Phase 4 — Export/Import Format Design

## JSON Schema for Exported Job Definition

The export format is a superset of `JobDefinitionResponse` with internal DB IDs removed, stable names added, and DAG dependencies included.

```jsonc
{
  // --- Format metadata ---
  "format_version": "1.0",
  "exported_at": "2026-07-30T14:30:00Z",          // ISO 8601 timestamp
  "exported_from": "orchestrator-v0.0.1",         // application version for provenance

  // --- Job metadata (matches JOB_DEFINITION columns) ---
  "jobName": "nightly-etl-pipeline",              // stable identifier — UNIQUE in target env
  "description": "Nightly ETL: extract, transform, load",
  "workingDir": "/opt/orchestrator/work/etl",
  "javaHome": "/usr/lib/jvm/java-21",             // nullable
  "classpathEntries": ["lib/*.jar", "lib/ext/*.jar"], // nullable → [] if null
  "enabled": true,

  // --- Team context (informational on export, remappable on import) ---
  "teamName": "Data Engineering",                 // team name, not ID

  // --- Steps (matches JOB_STEP + inline config) ---
  "steps": [
    {
      "stepName": "extract-data",                 // stable identifier within job — UNIQUE per job
      "stepType": "DB_QUERY",                     // matches registered step type key
      "stepConfig": "{\"query\":\"SELECT * FROM staging\",\"outputFile\":\"extract.csv\"}", // JSON string, as stored in DB
      "continueOnFailure": false,
      "enabled": true
    },
    {
      "stepName": "upload-to-sftp",
      "stepType": "SFTP",
      "stepConfig": "{\"host\":\"sftp.example.com\",\"port\":22,\"username\":\"deployer\",\"credentialRef\":\"prod-sftp-key\",\"remoteDir\":\"/incoming\",\"filePattern\":\"*.csv\",\"direction\":\"UPLOAD\"}",
      "continueOnFailure": false,
      "enabled": true
    },
    {
      "stepName": "archive-output",
      "stepType": "ARCHIVE",
      "stepConfig": "{\"sourceDir\":\"/opt/orchestrator/work/etl\",\"pattern\":\"*.csv\",\"outputFile\":\"archive.zip\"}",
      "continueOnFailure": true,
      "enabled": true
    }
  ],

  // --- DAG Dependencies (references steps by stepName, not DB ID) ---
  "dependencies": [
    {
      "stepName": "upload-to-sftp",               // the dependent step
      "dependsOnStepName": "extract-data",        // the prerequisite step
      "edgeCondition": "ON_SUCCESS"               // ON_SUCCESS | ON_FAILURE | ALWAYS
    },
    {
      "stepName": "archive-output",
      "dependsOnStepName": "upload-to-sftp",
      "edgeCondition": "ALWAYS"                   // archive regardless of upload success/failure
    }
  ],

  // --- Environment Variables (matches JOB_ENV_VAR) ---
  "envVars": [
    { "key": "DB_URL", "value": "jdbc:oracle:thin:@//dbhost:1521/orcl", "isGlobal": false },
    { "key": "LOG_LEVEL", "value": "INFO", "isGlobal": true }
  ],

  // --- Schedule (matches JOB_SCHEDULE — nullable) ---
  "schedule": {
    "cronExpression": "0 2 * * *",               // every day at 2 AM
    "enabled": false                              // disabled by default on import to prevent accidental runs
  },

  // --- Future: Notification subscriptions (Phase 5) ---
  "notifications": null                           // reserved — populated in Phase 5
}
```

## Field-by-Field Specification

### Top-Level Fields

| Field | Type | Required | Export Source | Import Behavior |
|-------|------|----------|---------------|-----------------|
| `format_version` | string (semver) | Yes (generated on export) | Hardcoded `"1.0"` | Validated: must be ≤ current supported version. Unknown future versions rejected with clear error. |
| `exported_at` | string (ISO 8601) | Yes (generated on export) | Current timestamp | Ignored on import. |
| `exported_from` | string | Yes (generated on export) | `SpringApplication.getApplicationVersion()` or fallback `"unknown"` | Logged on import for audit trail. Not stored. |
| `jobName` | string, 1-200 chars | Yes | `JOB_DEFINITION.JOB_NAME` | Looked up by name for conflict resolution. UNIQUE constraint enforced. |
| `description` | string, 0-1000 chars | No | `JOB_DEFINITION.DESCRIPTION` | Set as-is on create/update. |
| `workingDir` | string, 1-500 chars | Yes | `JOB_DEFINITION.WORKING_DIR` | Set as-is. Caller responsible for path validity in target env. |
| `javaHome` | string or null | No | `JOB_DEFINITION.JAVA_HOME` | Set as-is or null. |
| `classpathEntries` | array of strings or null | No | Parsed from `JOB_DEFINITION.CLASSPATH` JSON | Serialized to JSON and stored in CLASSPATH column. Empty array → null. |
| `enabled` | boolean | No (default: true) | `"Y".equals(ENABLED)` | Set on create/update. **Default false on import** for safety — user must explicitly enable. |
| `teamName` | string or null | No | Resolved from `TEAM.TEAM_NAME` via FK | If present and non-null, looked up by name; if not found, error. If null, uses importer's active team. |

### Steps Array

Each step object maps to a row in `JOB_STEP`. **No internal DB IDs** (`stepId`, `jobId`) are included. Steps are identified within the job by `stepName`.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `stepName` | string, 1-200 chars | Yes | Must be unique within the job's step list. Used as the stable identifier for dependency edges. |
| `stepType` | string, 1-50 chars | Yes | Validated against registered step types in target environment on import. |
| `stepConfig` | string (JSON) or null | No | Stored as-is in CLOB column. On import, parsed and validated against the step type's `StepConfigSchema`. |
| `continueOnFailure` | boolean | No (default: false) | Maps to CHAR(1) 'Y'/'N' in DB. |
| `enabled` | boolean | No (default: true) | Maps to CHAR(1) 'Y'/'N' in DB. |

**Note on `stepOrder`:** The export format omits `stepOrder` entirely since Phase 3 replaced linear ordering with DAG dependencies. On import, stepOrder is assigned sequentially from the steps array order for backward compatibility with any code that still reads it.

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
- `jobId` → omitted entirely. Job is identified by `jobName`.
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

Credentials appear inside `stepConfig` (a JSON string) as field values with type `SECRET_REF` per the step type's schema. For example, the SFTP executor has:

```json
{"credentialRef": "prod-sftp-key", ...}
```

- **Export:** The `stepConfig` string is serialized as-is. It contains only the credential reference name (e.g., `"prod-sftp-key"`), never the decrypted value.
- **Import validation:** After parsing stepConfig, if any field marked as `SECRET_REF` in the step type's schema has a value, that value is looked up against `JOB_CREDENTIAL.CREDENTIAL_REF` in the target environment. If not found, import fails with a specific error listing the missing credential names.
- **Import storage:** The stepConfig string is stored as-is. No transformation of credential references occurs during import.

## YAML Representation

The YAML format is a direct serialization of the same JSON structure using Jackson's `YAMLFactory`. No structural differences — just syntax:

```yaml
format_version: "1.0"
exported_at: "2026-07-30T14:30:00Z"
jobName: nightly-etl-pipeline
description: "Nightly ETL: extract, transform, load"
workingDir: /opt/orchestrator/work/etl
javaHome: /usr/lib/jvm/java-21
classpathEntries:
  - lib/*.jar
  - lib/ext/*.jar
enabled: false
teamName: "Data Engineering"
steps:
  - stepName: extract-data
    stepType: DB_QUERY
    stepConfig: '{"query":"SELECT * FROM staging","outputFile":"extract.csv"}'
    continueOnFailure: false
    enabled: true
  - stepName: upload-to-sftp
    stepType: SFTP
    stepConfig: '{"host":"sftp.example.com","port":22,"username":"deployer","credentialRef":"prod-sftp-key","remoteDir":"/incoming","filePattern":"*.csv","direction":"UPLOAD"}'
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
notifications: null
```

**Implementation:** Jackson `ObjectMapper` with `YAMLFactory` for YAML, standard `ObjectMapper` for JSON. Same DTO classes, different serializers.

## Import Request Envelope

The import endpoint accepts the job document wrapped in an envelope that carries import options:

```jsonc
{
  "mode": "error",           // "error" | "update" | "skip" — conflict resolution mode
  "definition": {            // the exported job document (same schema as above)
    "format_version": "1.0",
    "jobName": "...",
    ...
  }
}
```

| Envelope Field | Type | Required | Default | Notes |
|---------------|------|----------|---------|-------|
| `mode` | enum | No | `"error"` | `"error"` — reject if job name exists. `"update"` — update existing job's steps, envVars, schedule. Create version snapshot of current state before overwriting. `"skip"` — return 200 with a message indicating the job was skipped. |
| `definition` | object | Yes | — | The exported job document. |
