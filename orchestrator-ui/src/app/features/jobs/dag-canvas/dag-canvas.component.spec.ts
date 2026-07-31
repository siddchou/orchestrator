import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DagCanvasComponent } from './dag-canvas.component';
import { JobStepWithDependencies } from '@app/core/models/job.model';

function makeSteps(): JobStepWithDependencies[] {
  return [
    {
      stepId: 1,
      stepName: 'Step A',
      stepOrder: 1,
      stepType: 'SHELL_EXEC',
      stepConfig: '{}',
      continueOnFailure: false,
      enabled: true,
      dependencies: [],
    },
    {
      stepId: 2,
      stepName: 'Step B',
      stepOrder: 2,
      stepType: 'JAVA_EXEC',
      stepConfig: '{}',
      continueOnFailure: false,
      enabled: true,
      dependencies: [{ dependsOnStepId: 1, dependsOnStepName: 'Step A', edgeCondition: 'ON_SUCCESS' }],
    },
    {
      stepId: 3,
      stepName: 'Step C',
      stepOrder: 3,
      stepType: 'HTTP_CALL',
      stepConfig: '{}',
      continueOnFailure: false,
      enabled: true,
      dependencies: [{ dependsOnStepId: 1, dependsOnStepName: 'Step A', edgeCondition: 'ALWAYS' }],
    },
  ];
}

describe('DagCanvasComponent', () => {
  let component: DagCanvasComponent;
  let fixture: ComponentFixture<DagCanvasComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DagCanvasComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DagCanvasComponent);
    component = fixture.componentInstance;
  });

  function setSteps(steps: JobStepWithDependencies[]) {
    fixture.componentRef.setInput('jobId', 1);
    fixture.componentRef.setInput('steps', steps);
    fixture.detectChanges();
  }

  // --- Node rendering ---

  it('renders nodes from steps input', () => {
    setSteps(makeSteps());

    expect(component.nodes.length).toBe(3);
  });

  it('builds edges from step dependencies', () => {
    setSteps(makeSteps());

    expect(component.edges.length).toBe(2);
    expect(component.edges[0].from).toBe(1);
    expect(component.edges[0].to).toBe(2);
    expect(component.edges[1].from).toBe(1);
    expect(component.edges[1].to).toBe(3);
  });

  it('positions nodes via layout', () => {
    setSteps(makeSteps());

    // Root node (no deps) should be at layer 0
    const root = component.nodes.find(n => n.stepId === 1);
    expect(root?.position.y).toBe(40); // PADDING

    // Dependent nodes should be below the root
    const depA = component.nodes.find(n => n.stepId === 2);
    const depB = component.nodes.find(n => n.stepId === 3);
    expect(depA?.position.y).toBeGreaterThan(root!.position.y);
    expect(depB?.position.y).toBeGreaterThan(root!.position.y);
  });

  // --- Zoom/pan state management ---

  it('has default zoom level of 1', () => {
    fixture.detectChanges();

    expect(component.panZoom.scale).toBe(1);
  });

  it('zooms in up to max scale', () => {
    setSteps(makeSteps());

    component.zoomIn();
    expect(component.panZoom.scale).toBeGreaterThan(1);
    expect(component.panZoom.scale).toBeLessThanOrEqual(component.MAX_SCALE);
  });

  it('zooms out down to min scale', () => {
    setSteps(makeSteps());

    component.panZoom.scale = 1;
    component.zoomOut();
    expect(component.panZoom.scale).toBeLessThan(1);
    expect(component.panZoom.scale).toBeGreaterThanOrEqual(component.MIN_SCALE);
  });

  it('clamps zoom to max scale', () => {
    setSteps(makeSteps());

    component.panZoom.scale = component.MAX_SCALE;
    component.zoomIn();
    expect(component.panZoom.scale).toBe(component.MAX_SCALE);
  });

  it('clamps zoom to min scale', () => {
    setSteps(makeSteps());

    component.panZoom.scale = component.MIN_SCALE;
    component.zoomOut();
    expect(component.panZoom.scale).toBe(component.MIN_SCALE);
  });

  // --- Transform string ---

  it('produces correct CSS transform', () => {
    component.panZoom = { offsetX: 10, offsetY: 20, scale: 1.5 };

    const transform = component.getTransform();
    expect(transform).toBe('translate(10px, 20px) scale(1.5)');
  });

  // --- Edge condition symbols ---

  it('returns correct symbol for ON_SUCCESS', () => {
    expect(component.conditionSymbol('ON_SUCCESS')).toBe('✓');
  });

  it('returns correct symbol for ON_FAILURE', () => {
    expect(component.conditionSymbol('ON_FAILURE')).toBe('✗');
  });

  it('returns correct symbol for ALWAYS', () => {
    expect(component.conditionSymbol('ALWAYS')).toBe('∞');
  });

  // --- Event emissions ---

  it('emits stepSelected on node click', () => {
    setSteps(makeSteps());

    component.stepSelected.subscribe(stepId => {
      expect(stepId).toBe(2);
    });

    component.onNodeClick(2);
  });

  it('emits stepDeleted on delete requested', () => {
    setSteps(makeSteps());

    component.stepDeleted.subscribe(stepId => {
      expect(stepId).toBe(1);
    });

    component.onDeleteRequested(1);
  });

  // --- Empty input ---

  it('handles empty steps array', () => {
    setSteps([]);

    expect(component.nodes.length).toBe(0);
    expect(component.edges.length).toBe(0);
  });

  // --- Cycle detection on edge creation ---

  it('prevents creating circular dependency via picker', () => {
    const circularSteps: JobStepWithDependencies[] = [
      {
        stepId: 1, stepName: 'A', stepOrder: 1, stepType: 'SHELL_EXEC',
        stepConfig: '{}', continueOnFailure: false, enabled: true,
        dependencies: [{ dependsOnStepId: 2, dependsOnStepName: 'B', edgeCondition: 'ON_SUCCESS' }],
      },
      {
        stepId: 2, stepName: 'B', stepOrder: 2, stepType: 'SHELL_EXEC',
        stepConfig: '{}', continueOnFailure: false, enabled: true,
        dependencies: [], // B has no deps; trying to add B depends on A would create cycle (A→B→A)
      },
    ];
    setSteps(circularSteps);

    // Try to create edge: step 2 → step 1 (step 1 already depends on step 2, so making step 2 depend on step 1 creates a cycle)
    component.rubberBand = { active: true, fromStepId: 1, x: 0, y: 0 };
    component.pickerTargetStepId = 2;

    // The pickerSelected method should detect the cycle and not add the edge
    let selectedEmitted = false;
    component.dependenciesChanged.subscribe(() => { selectedEmitted = true; });

    component.onPickerSelected('ON_SUCCESS');

    // Edge count should remain unchanged (no new edge added)
    expect(component.edges.length).toBe(1);
  });

  it('prevents creating duplicate edge', () => {
    const stepsWithEdge: JobStepWithDependencies[] = [
      {
        stepId: 1, stepName: 'A', stepOrder: 1, stepType: 'SHELL_EXEC',
        stepConfig: '{}', continueOnFailure: false, enabled: true,
        dependencies: [],
      },
      {
        stepId: 2, stepName: 'B', stepOrder: 2, stepType: 'SHELL_EXEC',
        stepConfig: '{}', continueOnFailure: false, enabled: true,
        dependencies: [{ dependsOnStepId: 1, dependsOnStepName: 'A', edgeCondition: 'ON_SUCCESS' }],
      },
    ];
    setSteps(stepsWithEdge);

    expect(component.edges.length).toBe(1);

    // Try to create the same edge again: step 2 depends on step 1 (already exists)
    component.rubberBand = { active: true, fromStepId: 1, x: 0, y: 0 };
    component.pickerTargetStepId = 2;

    component.onPickerSelected('ALWAYS');

    // Edge count should still be 1 — duplicate not added
    expect(component.edges.length).toBe(1);
  });

  // --- Dirty tracking (Task 11) ---

  it('is not dirty on initial load', () => {
    setSteps(makeSteps());

    expect(component.isDirty).toBe(false);
  });

  it('becomes dirty when an edge is added via picker', () => {
    const noDeps: JobStepWithDependencies[] = [
      { stepId: 1, stepName: 'A', stepOrder: 1, stepType: 'SHELL_EXEC', stepConfig: '{}', continueOnFailure: false, enabled: true, dependencies: [] },
      { stepId: 2, stepName: 'B', stepOrder: 2, stepType: 'SHELL_EXEC', stepConfig: '{}', continueOnFailure: false, enabled: true, dependencies: [] },
    ];
    setSteps(noDeps);

    expect(component.isDirty).toBe(false);

    // Add edge: B depends on A
    component.rubberBand = { active: true, fromStepId: 1, x: 0, y: 0 };
    component.pickerTargetStepId = 2;
    component.onPickerSelected('ON_SUCCESS');

    expect(component.isDirty).toBe(true);
  });

  it('emits saveRequested with all steps on save', () => {
    setSteps(makeSteps());

    let emitted: any[] | null = null;
    component.saveRequested.subscribe(steps => { emitted = steps; });

    component.onSaveClicked();

    expect(emitted).not.toBeNull();
    expect(emitted!.length).toBe(3);
  });

  it('resets dirty state on saved', () => {
    const noDeps: JobStepWithDependencies[] = [
      { stepId: 1, stepName: 'A', stepOrder: 1, stepType: 'SHELL_EXEC', stepConfig: '{}', continueOnFailure: false, enabled: true, dependencies: [] },
      { stepId: 2, stepName: 'B', stepOrder: 2, stepType: 'SHELL_EXEC', stepConfig: '{}', continueOnFailure: false, enabled: true, dependencies: [] },
    ];
    setSteps(noDeps);

    // Make dirty
    component.rubberBand = { active: true, fromStepId: 1, x: 0, y: 0 };
    component.pickerTargetStepId = 2;
    component.onPickerSelected('ON_SUCCESS');

    expect(component.isDirty).toBe(true);

    // Simulate successful save
    component.onSaved();

    expect(component.isDirty).toBe(false);
  });

  // --- Node drag-to-reposition (Task 8) ---

  it('updates node position on drag end', () => {
    setSteps(makeSteps());

    const root = component.nodes.find(n => n.stepId === 1);
    const originalX = root?.position.x;
    const originalY = root?.position.y;

    component.onNodeDragEnd({ stepId: 1, position: { x: 999, y: 888 } });

    expect(root?.position.x).toBe(999);
    expect(root?.position.y).toBe(888);
  });

  it('ignores drag end for unknown stepId', () => {
    setSteps(makeSteps());

    const nodeCount = component.nodes.length;
    component.onNodeDragEnd({ stepId: 999, position: { x: 0, y: 0 } });

    expect(component.nodes.length).toBe(nodeCount);
  });
});
