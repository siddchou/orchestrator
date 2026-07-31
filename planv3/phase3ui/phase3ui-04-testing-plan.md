# Phase 3 UI — Testing Plan

## Unit Tests

### CycleDetectorService (`cycle-detector.service.spec.ts`)

| Test Case | Input | Expected Output |
|-----------|-------|-----------------|
| Empty graph | `existingEdges = new Map()` | `false` |
| Single node, no edges | `Map(1 → [])` | `false` |
| Linear chain A→B→C | `Map(A:[], B:[A], C:[B])`, check adding D→C | `false` |
| Cycle: A→B→C→A | `Map(A:[], B:[A], C:[B])`, check adding edge A depends on C | `true` |
| Diamond no cycle | `Map(A:[], B:[A], C:[A], D:[B,C])` | `false` |
| Self-loop | Check step A depends on A | `true` |
| Two independent chains | `Map(A:[], B:[A], X:[], Y:[X])`, check adding Z→Y | `false` |

### DagLayoutService (`dag-layout.service.spec.ts`)

| Test Case | Input | Assertion |
|-----------|-------|-----------|
| Empty array | `[]` | No error, no mutation |
| Single node | `[nodeA]` | Position at (40, 40) — padding offset |
| Two independent nodes | `[nodeA, nodeB]` | Same y-layer, separated by HORIZONTAL_GAP + node width |
| Linear chain A→B→C | 3 nodes, B depends on A, C depends on B | Three layers, increasing y by VERTICAL_GAP + node height |
| Diamond shape | A→B, A→C, B→D, C→D | Layer 0: A; Layer 1: B,C side by side; Layer 2: D centered below |
| Fan-out (5 children) | Root → [A,B,C,D,E] | Root at layer 0, all 5 children at layer 1, evenly spaced |

### DagEdgeRenderer (`dag-edge-renderer.spec.ts`)

| Test Case | Source Position | Target Position | Assertion |
|-----------|-----------------|-----------------|-----------|
| Top-to-bottom | (100, 50) output port | (120, 200) input port | Path goes downward, midpoint ~y=125 |
| Bottom-to-top | (100, 200) output port | (120, 50) input port | Path curves upward without self-intersection |
| Same level left→right | (100, 100) → (300, 100) | Horizontal bezier curve |
| Diagonal | (100, 50) → (400, 250) | Smooth diagonal curve, midpoint at ~{(250,150)} |

### DagNodeComponent (`dag-node.component.spec.ts`)

| Test Case | Setup | Assertion |
|-----------|-------|-----------|
| Renders icon for step type | `node.stepType = 'JAVA_EXEC'` | `<mat-icon>` contains 'language_java' |
| Renders step name | `node.stepName = 'Build App'` | Text "Build App" present in DOM |
| Ports visible in edit mode | `readOnly = false` | `.port-output` and `.port-input` elements exist |
| Ports hidden in read-only mode | `readOnly = true` | No `.port` elements in DOM |
| Status ring colored by status | `node.status = 'SUCCESS'`, `readOnly = true` | `.status-ring` border-color matches green variable |
| nodeClick emits stepId | Click on `.node-body` | EventEmitter emits correct stepId |
| deleteRequested emits stepId | Click on `.delete-btn` | EventEmitter emits correct stepId |

### EdgeConditionPickerComponent (`edge-condition-picker.component.spec.ts`)

| Test Case | Action | Assertion |
|-----------|--------|-----------|
| Select ON_SUCCESS | Click first option | `selected.emit('ON_SUCCESS')` fired |
| Select ON_FAILURE | Click second option | `selected.emit('ON_FAILURE')` fired |
| Select ALWAYS | Click third option | `selected.emit('ALWAYS')` fired |
| Component closes on select | Click any option | Component hidden/destroyed after emit |

### DagCanvasComponent (`dag-canvas.component.spec.ts`)

| Test Case | Setup | Assertion |
|-----------|-------|-----------|
| Creates nodes from steps input | `steps = [stepA, stepB]` | 2 `<app-dag-node>` instances rendered |
| Layout called on init | Any steps array | `dagLayoutService.layout()` called once in ngAfterViewInit |
| Zoom in on wheel event | Dispatch wheel event | Scale increases, capped at 2x |
| Zoom out on wheel event | Dispatch wheel event with negative delta | Scale decreases, floor at 0.3x |
| dependenciesChanged emits on save | Add edge locally, click save | EventEmitter fires with correct stepId + deps array |
| Cycle prevented on edge creation | Mock cycleDetector to return true | No edge added, snackbar shown |
| Self-loop prevented | Drag from node A output to node A input | Edge not created, snackbar "Cannot depend on itself" |

### JobService Extensions (`job.service.spec.ts`)

| Test Case | Method | Assertion |
|-----------|--------|-----------|
| GET dependencies URL | `getStepDependencies(5, 12)` | HTTP GET to `/api/jobs/5/steps/12/dependencies` |
| PUT dependencies URL | `setStepDependencies(5, 12, [...])` | HTTP PUT to `/api/jobs/5/steps/12/dependencies` with body |

### RunDagCanvasComponent (`run-dag-canvas.component.spec.ts`)

| Test Case | Setup | Assertion |
|-----------|-------|-----------|
| Renders nodes from run steps | `run.steps = [stepA, stepB]` | 2 node elements rendered |
| Status colors applied | Steps with different statuses | Each node has correct status color class |
| Running step pulse animation | One step status = 'RUNNING' | Node element has `.running` CSS class (pulse keyframe) |
| No edit controls in DOM | Any setup | No port elements, no delete buttons found |

## E2E Tests

### E2E Test 1: Diamond DAG Build & Save

**File:** `e2e/tests/dag-canvas.spec.ts`
**Framework:** Playwright (or whatever the project uses — check existing e2e setup)

```
Scenario: User builds a diamond-shaped DAG and saves it

Given I am on the job detail page for a new job with a name and working directory saved
When I switch the Steps tab to canvas view
Then I see an empty canvas with an "Add Step" button

When I click "Add Step" and select "Java Exec" from the palette
And I name it "A - Start" and save
And I repeat to add steps "B - Process 1", "C - Process 2", "D - End"
Then I see 4 nodes on the canvas in a layout

When I drag an edge from A's output port to B's input port
And select "ON_SUCCESS" in the condition picker
And drag an edge from A to C with "ALWAYS"
And drag an edge from B to D with "ON_SUCCESS"
And drag an edge from C to D with "ON_FAILURE"
Then I see 4 edges rendered on the canvas with correct labels

When I click "Save Dependencies"
Then the PUT API calls are made for steps A, B, C, D (intercept and verify)
And a success snackbar appears

When I reload the page
And switch to canvas view
Then the diamond DAG renders with all 4 edges intact
```

**API Interception:** Intercept `PUT /api/jobs/:id/steps/:stepId/dependencies` — verify body contains correct `dependsOnStepId` and `edgeCondition` for each step.

### E2E Test 2: Run-View Canvas Live Status Updates

**File:** `e2e/tests/run-dag-canvas.spec.ts`

```
Scenario: Run-view canvas updates node colors as steps complete

Given I have a job with 3 sequential steps (A→B→C)
When I trigger a manual run of the job
And navigate to the run detail page
Then I see the run-view DAG canvas below the timeline
And all 3 nodes show PENDING status color (grey)

When step A starts (poll returns RUNNING for step A)
Then node A shows RUNNING status color (orange with pulse animation)
And nodes B and C remain PENDING

When step A completes SUCCESS and step B starts RUNNING
Then node A shows SUCCESS color (green)
And node B shows RUNNING color (orange with pulse)
And the edge from A to B is colored green (condition met)

When all steps complete
Then all nodes show their final status colors
And edges reflect whether conditions were satisfied
```

**Mocking Strategy:** Since real job execution takes time, mock the `GET /api/runs/:runId` endpoint to return progressively updated step statuses across poll intervals. Use Playwright's `route()` API to intercept and serve staged responses.

### E2E Test 3: Cycle Prevention

**File:** `e2e/tests/dag-canvas.spec.ts` (same file, separate test)

```
Scenario: User cannot create a circular dependency

Given I have a job with steps A→B→C on the canvas
When I attempt to drag an edge from C's output back to A's input
Then a snackbar appears: "Cannot create circular dependency"
And no new edge is rendered on the canvas
```

## Test Coverage Targets

| Component/Service | Minimum Branch Coverage | Rationale |
|-------------------|------------------------|-----------|
| CycleDetectorService | 95% | Core safety logic — must be thoroughly tested |
| DagLayoutService | 90% | Layout correctness affects usability |
| DagEdgeRenderer | 100% | Pure function — easy to achieve, critical for visual correctness |
| DagCanvasComponent | 70% | Complex interaction component — focus on state logic, not DOM events |
| DagNodeComponent | 80% | Template rendering + event emission |
| RunDagCanvasComponent | 75% | Status color mapping + no-edit-assertions |
| EdgeConditionPicker | 85% | Simple selection logic |
