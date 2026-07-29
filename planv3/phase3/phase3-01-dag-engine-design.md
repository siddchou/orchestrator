<!-- FILE: phase3-01-dag-engine-design.md -->
# Phase 3.1 — DAG Engine Design

## Dependency Model Decision

**Decision: Join table (`JOB_STEP_DEPENDENCY`) over CLOB column.**

| Criterion | Join Table | CLOB on JOB_STEP |
|-----------|------------|------------------|
| Per-edge conditions (different condition per upstream) | Native support | Requires JSON array of objects — queryable only via JSON functions |
| Diamond DAGs (step with 2+ upstream deps) | Natural FK relationships | Possible but harder to validate referential integrity |
| Backfill from stepOrder chain | Simple: generate N-1 rows | Simple: generate JSON array |
| Query "what depends on step X?" | `WHERE depends_on_step_id = ?` | Requires JSON table-valued function |
| Schema complexity | +1 table, ~N rows per job | +1 column, no new table |

**Tradeoff accepted:** The join table adds one migration and one JPA entity. In return, every edge has its own condition, referential integrity is enforced by FKs, and the query patterns the DAG engine needs are simple indexed lookups rather than JSON parsing. Given that diamond-shaped workflows (parallel branches converging) are a core Phase 3 requirement, the join table is the right choice.

## Schema

```sql
CREATE TABLE JOB_STEP_DEPENDENCY (
    DEPENDENCY_ID       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    STEP_ID             NUMBER NOT NULL REFERENCES JOB_STEP(STEP_ID) ON DELETE CASCADE,
    DEPENDS_ON_STEP_ID  NUMBER NOT NULL REFERENCES JOB_STEP(STEP_ID),
    EDGE_CONDITION      VARCHAR2(20) DEFAULT 'ON_SUCCESS'
                        CHECK (EDGE_CONDITION IN ('ON_SUCCESS', 'ON_FAILURE', 'ALWAYS')),
    CONSTRAINT UQ_STEP_DEP UNIQUE (STEP_ID, DEPENDS_ON_STEP_ID)
);

CREATE INDEX IDX_DEP_TARGET ON JOB_STEP_DEPENDENCY(STEP_ID);
CREATE INDEX IDX_DEP_SOURCE ON JOB_STEP_DEPENDENCY(DEPENDS_ON_STEP_ID);
```

- `STEP_ID` = the step that depends on another (the "downstream" step)
- `DEPENDS_ON_STEP_ID` = the upstream step it waits for
- `EDGE_CONDITION` = when the dependency is satisfied:
  - **ON_SUCCESS** (default): downstream runs only if upstream succeeded
  - **ON_FAILURE**: downstream runs only if upstream failed
  - **ALWAYS**: downstream runs regardless of upstream result

## DagExecutionEngine — Class Design

```
engine/DagExecutionEngine.java
```

### Construction

```java
@Component
public class DagExecutionEngine {
    public DagExecutionEngine(
        StepExecutorRegistry registry,
        JobRunStepRepository runStepRepo,
        CredentialDecryptionService decryptionService,
        JsonParser jsonParser,
        @Qualifier("jobTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
        ParamResolver paramResolver) {}

    /** Maximum concurrent steps within a single run. Configurable via property. */
    @Value("${orchestrator.engine.max-concurrent-steps:5}")
    private int maxConcurrentSteps;
}
```

### Execution Flow

```java
public void execute(ExecutionContext ctx, JobDefinition job, JobRun run) {
    // 1. Build DAG from steps + dependencies
    DagModel dag = buildDag(job);

    // 2. Validate: cycle detection (throw if circular), orphan warning (log)
    dag.validate();

    // 3. Semaphore-bounded concurrent execution
    Semaphore stepSemaphore = new Semaphore(maxConcurrentSteps);
    CountDownLatch runLatch = new CountDownLatch(dag.stepCount());
    ConcurrentHashMap<String, StepResult> completedResults = new ConcurrentHashMap<>();

    // 4. Submit all steps as tasks; each task:
    //    a. Waits for upstream dependencies (via per-step CountDownLatch)
    //    b. Evaluates edge conditions against completedResults
    //    c. If condition met: resolves params, executes step
    //    d. If condition not met: marks SKIPPED
    //    e. Stores result in completedResults, releases dependents

    // 5. Wait for all tasks (with cancellation support)
    runLatch.await(run.getTimeout(), TimeUnit.SECONDS);
}
```

### Per-Step Task Logic (pseudocode)

```
for each step S in DAG:
    submit to taskExecutor:
        1. Acquire stepSemaphore (bounds concurrency)
        2. Wait on S.dependencyLatch (blocks until all upstream steps complete)
        3. Evaluate edge conditions:
           for each dependency D of S:
               upstreamResult = completedResults.get(D.stepId)
               if condition == ON_SUCCESS and upstreamResult != SUCCESS → skip
               if condition == ON_FAILURE and upstreamResult != FAILED   → skip
               if condition == ALWAYS                                    → proceed
           if any ON_SUCCESS/ON_FAILURE dep not satisfied → mark SKIPPED
        4. Build StepContext with completedResults as upstreamOutputs
        5. Resolve parameters in step config via ParamResolver
        6. Execute step (same retry logic from current executeStep)
        7. Store result in completedResults
        8. Release all downstream steps' dependency latches
        9. Release stepSemaphore
        10. CountDown runLatch
```

### SKIPPED Propagation

When a step is SKIPPED because its edge condition was not met:
- Downstream steps with `ON_SUCCESS` edges from the skipped step are also SKIPPED (the upstream didn't succeed)
- Downstream steps with `ALWAYS` edges still run
- This propagation is evaluated at dependency resolution time, not recursively — each step independently checks its own edge conditions against actual upstream results

### Cycle Detection

Before execution begins, perform a DFS-based cycle detection on the DAG:
```java
void validate() throws CircularDependencyException {
    // Standard 3-color DFS (WHITE=unvisited, GRAY=in-progress, BLACK=done)
    // If a GRAY node is revisited → cycle detected
}
```

The exception includes the cycle path for debugging: `"Circular dependency: step_A → step_C → step_B → step_A"`

## ParamResolver — Class Design

```
engine/template/ParamResolver.java
```

### Resolution Pattern

```java
public class ParamResolver {
    private static final Pattern TEMPLATE_PATTERN =
        Pattern.compile("\\$\\{([^}]+)}");

    public String resolve(String template, ResolutionContext context) {
        if (template == null || !template.contains("${")) return template;

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String ref = matcher.group(1);
            Object value = resolveReference(ref, context);
            matcher.appendReplacement(result,
                escapeReplacement(value != null ? value.toString() : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
```

### Resolution Order (reference types)

| Pattern | Source | Example |
|---------|--------|---------|
| `${job.param.X}` | Runtime parameters from POST body | `${job.param.env}` → `"staging"` |
| `${step.<stepId>.output.X}` | `StepResult.outputs` map from completed step | `${step.3.output.statusCode}` → `200` |
| `${env.X}` | System env vars + global/job-specific env vars from JOB_ENV_VAR | `${env.HOME}` → `/home/user` |

**Resolution context:**
```java
public record ResolutionContext(
    Map<String, Object> jobParams,           // from POST body
    Map<String, StepResult> stepResults,     // completed upstream steps
    Map<String, String> envVars              // system + global + job-specific
) {}
```

### Where It Runs in the Pipeline

The ParamResolver runs **inside `DagExecutionEngine.executeStep()`**, between dependency satisfaction and executor dispatch:

1. Step dependencies satisfied → step is eligible to run
2. **ParamResolver resolves all string values in step's config JSON**
3. Resolved config passed to `StepContext.stepConfig`
4. Executor's `execute(StepContext)` called with resolved config

This means executors receive fully-resolved config — no executor-specific templating code needed (the SftpStepExecutor's inline template replacement becomes redundant and can be removed).

### Unresolved References

- **`${job.param.X}` where X not in POST body:** Left unresolved (kept as literal `${job.param.X}`). The step executor may fail if it expects a concrete value. Logged as a warning.
- **`${step.<id>.output.X}` where step hasn't run yet:** Cannot happen — the step is blocked on its dependency latch until that upstream step completes. If the reference is to a step that's NOT an upstream dependency, log a warning and leave unresolved.
- **`${env.X}` where X not in any env map:** Left unresolved. Logged as debug.

### Recursive Resolution

After one pass of replacement, if the result still contains `${...}`, perform a second pass. Cap at 3 passes to prevent infinite loops from self-referential templates. Log a warning if more than 2 passes are needed.
