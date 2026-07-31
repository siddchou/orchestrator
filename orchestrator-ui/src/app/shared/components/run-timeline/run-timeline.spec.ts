import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RunTimelineComponent } from './run-timeline';
import { JobRunDetail, RunStepDetail } from '@app/core/models/run.model';
import { RunStatus } from '@app/core/models/job.model';

function makeSteps(): RunStepDetail[] {
  return [
    {
      runStepId: 1,
      stepName: 'Download',
      stepType: 'SHELL_CMD',
      stepOrder: 1,
      status: 'SUCCESS' as RunStatus,
      exitCode: 0,
      startedAt: '2025-01-01T00:00:00Z',
      endedAt: '2025-01-01T00:00:10Z',
      durationSeconds: 10,
    },
    {
      runStepId: 2,
      stepName: 'Compile',
      stepType: 'SHELL_CMD',
      stepOrder: 2,
      status: 'SUCCESS' as RunStatus,
      exitCode: 0,
      startedAt: '2025-01-01T00:00:10Z',
      endedAt: '2025-01-01T00:00:30Z',
      durationSeconds: 20,
    },
    {
      runStepId: 3,
      stepName: 'Deploy',
      stepType: 'HTTP_CALL',
      stepOrder: 3,
      status: 'FAILED' as RunStatus,
      exitCode: 1,
      startedAt: '2025-01-01T00:00:30Z',
      endedAt: '2025-01-01T00:00:45Z',
      durationSeconds: 15,
    },
  ];
}

function makeRun(steps?: RunStepDetail[]): JobRunDetail {
  return {
    runId: 1,
    jobId: 1,
    jobName: 'Test Job',
    status: 'FAILED' as RunStatus,
    triggerType: 'MANUAL',
    triggeredBy: 'test-user',
    startedAt: '2025-01-01T00:00:00Z',
    endedAt: '2025-01-01T00:00:45Z',
    durationSeconds: 45,
    steps: steps ?? makeSteps(),
  };
}

describe('RunTimelineComponent', () => {
  let component: RunTimelineComponent;
  let fixture: ComponentFixture<RunTimelineComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RunTimelineComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RunTimelineComponent);
    component = fixture.componentInstance;
  });

  function setRun(run: JobRunDetail) {
    fixture.componentRef.setInput('run', run);
    fixture.detectChanges();
  }

  // --- Bar position/width computation ---

  it('renders step bars with correct position and width', () => {
    setRun(makeRun());

    expect(component.bars.length).toBe(3);

    // First bar starts at 0%, takes ~22% of total (10s / 45s)
    expect(component.bars[0].leftPercent).toBeCloseTo(0, 1);
    expect(component.bars[0].widthPercent).toBeGreaterThan(0);

    // Second bar starts where first ended
    expect(component.bars[1].leftPercent).toBeGreaterThan(0);

    // Third bar starts after second
    expect(component.bars[2].leftPercent).toBeGreaterThan(component.bars[1].leftPercent);
  });

  // --- Zero-duration step gets minimum width ---

  it('zero-duration step gets minimum width', () => {
    const instantStep: RunStepDetail = {
      runStepId: 1,
      stepName: 'Instant',
      stepType: 'SHELL_CMD',
      stepOrder: 1,
      status: 'SUCCESS' as RunStatus,
      exitCode: 0,
      startedAt: '2025-01-01T00:00:00Z',
      endedAt: '2025-01-01T00:00:00Z', // same as start → zero duration
      durationSeconds: 0,
    };
    const secondStep: RunStepDetail = {
      runStepId: 2,
      stepName: 'Second',
      stepType: 'SHELL_CMD',
      stepOrder: 2,
      status: 'SUCCESS' as RunStatus,
      exitCode: 0,
      startedAt: '2025-01-01T00:00:10Z',
      endedAt: '2025-01-01T00:00:20Z',
      durationSeconds: 10,
    };

    setRun(makeRun([instantStep, secondStep]));

    expect(component.bars.length).toBe(2);
    // Zero-duration step should have minimum width of 1.5%
    expect(component.bars[0].widthPercent).toBeGreaterThanOrEqual(1.5);
  });

  // --- Null timestamps handled gracefully ---

  it('null timestamps handled without error', () => {
    const nullStep: RunStepDetail = {
      runStepId: 1,
      stepName: 'Unknown Start',
      stepType: 'SHELL_CMD',
      stepOrder: 1,
      status: 'PENDING' as RunStatus,
      exitCode: null,
      startedAt: '', // empty string = no start time
      endedAt: '2025-01-01T00:00:10Z',
      durationSeconds: 0,
    };
    const normalStep: RunStepDetail = {
      runStepId: 2,
      stepName: 'Normal',
      stepType: 'SHELL_CMD',
      stepOrder: 2,
      status: 'SUCCESS' as RunStatus,
      exitCode: 0,
      startedAt: '2025-01-01T00:00:05Z',
      endedAt: '2025-01-01T00:00:20Z',
      durationSeconds: 15,
    };

    setRun(makeRun([nullStep, normalStep]));

    expect(component.bars.length).toBe(2);
    // The step with null startedAt should be flagged
    expect(component.bars[0].hasNullTime).toBe(true);
  });

  // --- Color maps to status correctly ---

  it('color maps to status correctly', () => {
    setRun(makeRun());

    const successBar = component.bars.find(b => b.step.status === 'SUCCESS');
    const failedBar = component.bars.find(b => b.step.status === 'FAILED');

    expect(successBar?.colorVar).toMatch(/success|#4caf50/);
    expect(failedBar?.colorVar).toMatch(/failed|#f44336/);
  });

  // --- Tooltip shows step name ---

  it('tooltip title contains step name', () => {
    setRun(makeRun());

    const bars = fixture.nativeElement.querySelectorAll('.step-bar');
    expect(bars.length).toBe(3);

    // Check that the first bar's title attribute contains the step name
    expect(bars[0].getAttribute('title')).toContain('Download');
  });

  it('tooltip shows warning indicator for null timestamps', () => {
    const nullStep: RunStepDetail = {
      runStepId: 1,
      stepName: 'Partial',
      stepType: 'SHELL_CMD',
      stepOrder: 1,
      status: 'RUNNING' as RunStatus,
      exitCode: null,
      startedAt: '',
      endedAt: '',
      durationSeconds: 0,
    };
    const secondStep: RunStepDetail = {
      runStepId: 2,
      stepName: 'Second',
      stepType: 'SHELL_CMD',
      stepOrder: 2,
      status: 'SUCCESS' as RunStatus,
      exitCode: 0,
      startedAt: '2025-01-01T00:00:00Z',
      endedAt: '2025-01-01T00:00:10Z',
      durationSeconds: 10,
    };

    setRun(makeRun([nullStep, secondStep]));

    const bars = fixture.nativeElement.querySelectorAll('.step-bar');
    // The bar with null time should have the unknown class
    const unknownBar = fixture.nativeElement.querySelector('.step-bar-unknown');
    expect(unknownBar).toBeTruthy();
  });

  // --- Time axis ticks ---

  it('generates time axis ticks', () => {
    setRun(makeRun());

    expect(component.ticks.length).toBeGreaterThanOrEqual(2);
    expect(component.ticks[0].label).toBeTruthy();
    expect(component.ticks[0].leftPercent).toBe(0);
  });

  // --- Empty/missing run data ---

  it('handles missing run gracefully', () => {
    // Don't set component.run — leave it undefined
    fixture.detectChanges();

    expect(component.bars).toEqual([]);
    expect(component.ticks).toEqual([]);
  });

  it('handles run with no steps', () => {
    setRun({ ...makeRun(), steps: [] });

    expect(component.bars).toEqual([]);
  });

  // --- formatDuration helper (indirect test via ticks) ---

  it('formats millisecond durations correctly', () => {
    const shortSteps: RunStepDetail[] = [
      {
        runStepId: 1, stepName: 'A', stepType: 'SHELL_CMD', stepOrder: 1,
        status: 'SUCCESS' as RunStatus, exitCode: 0,
        startedAt: '2025-01-01T00:00:00Z', endedAt: '2025-01-01T00:00:00.500Z',
        durationSeconds: 0,
      },
      {
        runStepId: 2, stepName: 'B', stepType: 'SHELL_CMD', stepOrder: 2,
        status: 'SUCCESS' as RunStatus, exitCode: 0,
        startedAt: '2025-01-01T00:00:00.500Z', endedAt: '2025-01-01T00:00:01Z',
        durationSeconds: 0,
      },
    ];
    setRun(makeRun(shortSteps));

    // Ticks should show ms format for sub-second durations
    const hasMsTick = component.ticks.some(t => t.label.includes('ms'));
    expect(hasMsTick).toBe(true);
  });
});
