import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RunDagCanvasComponent } from './run-dag-canvas.component';
import { JobRunDetail } from '@app/core/models/run.model';
import { StepDependency, EdgeCondition } from '@app/core/models/job.model';

describe('RunDagCanvasComponent', () => {
  let component: RunDagCanvasComponent;
  let fixture: ComponentFixture<RunDagCanvasComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RunDagCanvasComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RunDagCanvasComponent);
    component = fixture.componentInstance;
  });

  function createRun(steps: { name: string; type: string; status: string }[]): JobRunDetail {
    return {
      runId: 1,
      jobId: 1,
      jobName: 'test-job',
      status: 'RUNNING',
      triggerType: 'MANUAL',
      triggeredBy: 'test',
      startedAt: '2026-01-01T00:00:00Z',
      endedAt: '',
      durationSeconds: 0,
      steps: steps.map((s, i) => ({
        runStepId: i + 1,
        stepName: s.name,
        stepType: s.type,
        stepOrder: i + 1,
        status: s.status as any,
        exitCode: null,
        startedAt: '2026-01-01T00:00:00Z',
        endedAt: '',
        durationSeconds: 0,
      })),
    };
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('builds nodes from run steps', () => {
    component.run = createRun([
      { name: 'Step A', type: 'SHELL_EXEC', status: 'SUCCESS' },
      { name: 'Step B', type: 'JAVA_EXEC', status: 'RUNNING' },
    ]);
    component.stepDependencies = {};
    component.ngOnChanges({ run: { currentValue: component.run, previousValue: null, firstChange: true, isFirstChange: () => true } });

    expect(component.nodes.length).toBe(2);
    expect(component.nodes[0].stepName).toBe('Step A');
    expect(component.nodes[1].status).toBe('RUNNING');
  });

  it('builds edges from step dependencies', () => {
    component.run = createRun([
      { name: 'A', type: 'SHELL_EXEC', status: 'SUCCESS' },
      { name: 'B', type: 'JAVA_EXEC', status: 'PENDING' },
    ]);
    component.stepDependencies = {
      2: [{ dependsOnStepId: 1, dependsOnStepName: 'A', edgeCondition: 'ON_SUCCESS' }],
    };
    component.ngOnChanges({ run: { currentValue: component.run, previousValue: null, firstChange: true, isFirstChange: () => true } });

    expect(component.edges.length).toBe(1);
    expect(component.edges[0].from).toBe(1);
    expect(component.edges[0].to).toBe(2);
    expect(component.edges[0].condition).toBe('ON_SUCCESS');
  });

  it('returns correct status colors', () => {
    expect(component.statusColor('SUCCESS')).toBe('#4caf50');
    expect(component.statusColor('FAILED')).toBe('#f44336');
    expect(component.statusColor('RUNNING')).toBe('#ff9800');
    expect(component.statusColor('PENDING')).toBe('#9e9e9e');
  });

  it('edge color matches step status', () => {
    expect(component.edgeColor('SUCCESS')).toBe('#4caf50');
    expect(component.edgeColor('FAILED')).toBe('#f44336');
  });

  it('isRunning returns true only for RUNNING status', () => {
    expect(component.isRunning('RUNNING')).toBe(true);
    expect(component.isRunning('SUCCESS')).toBe(false);
    expect(component.isRunning('PENDING')).toBe(false);
  });

  it('conditionSymbol maps conditions to symbols', () => {
    expect(component.conditionSymbol('ON_SUCCESS')).toBe('✓');
    expect(component.conditionSymbol('ON_FAILURE')).toBe('✗');
    expect(component.conditionSymbol('ALWAYS')).toBe('∞');
  });

  it('handles empty run gracefully', () => {
    component.run = createRun([]);
    component.stepDependencies = {};
    component.ngOnChanges({ run: { currentValue: component.run, previousValue: null, firstChange: true, isFirstChange: () => true } });

    expect(component.nodes.length).toBe(0);
    expect(component.edges.length).toBe(0);
  });

  it('nodes are positioned by layout service', () => {
    component.run = createRun([
      { name: 'A', type: 'SHELL_EXEC', status: 'SUCCESS' },
      { name: 'B', type: 'JAVA_EXEC', status: 'PENDING' },
      { name: 'C', type: 'ENV_SETUP', status: 'PENDING' },
    ]);
    component.stepDependencies = {};
    component.ngOnChanges({ run: { currentValue: component.run, previousValue: null, firstChange: true, isFirstChange: () => true } });

    // Layout should have assigned non-zero positions
    expect(component.nodes[0].position.x).toBeGreaterThan(0);
    expect(component.nodes[0].position.y).toBeGreaterThanOrEqual(0);
  });

  it('getTransform returns identity transform for read-only canvas', () => {
    expect(component.getTransform()).toBe('translate(0px, 0px) scale(1)');
  });
});
