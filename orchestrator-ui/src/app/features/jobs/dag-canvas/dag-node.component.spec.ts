import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DagNodeComponent } from './dag-node.component';
import { DagNodeModel } from './services/dag-layout.service';

function makeNode(overrides?: Partial<DagNodeModel>): DagNodeModel {
  return {
    stepId: 1,
    stepName: 'Test Step',
    stepType: 'SHELL_EXEC',
    position: { x: 100, y: 200 },
    size: { width: 160, height: 72 },
    dependencies: [],
    status: undefined,
    ...overrides,
  };
}

describe('DagNodeComponent', () => {
  let component: DagNodeComponent;
  let fixture: ComponentFixture<DagNodeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DagNodeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DagNodeComponent);
    component = fixture.componentInstance;
  });

  function setNode(node: DagNodeModel) {
    fixture.componentRef.setInput('node', node);
    fixture.detectChanges();
  }

  // --- Rendering ---

  it('renders step name and type', () => {
    setNode(makeNode());

    expect(fixture.nativeElement.querySelector('.step-name')?.textContent).toContain('Test Step');
    expect(fixture.nativeElement.querySelector('.step-type')?.textContent).toContain('SHELL_EXEC');
  });

  it('renders step icon', () => {
    setNode(makeNode());

    const icon = fixture.nativeElement.querySelector('mat-icon');
    expect(icon).toBeTruthy();
    // SHELL_EXEC maps to 'terminal' icon
    expect(icon.textContent).toContain('terminal');
  });

  it('positions node at correct coordinates', () => {
    setNode(makeNode({ position: { x: 50, y: 120 } }));

    const el = fixture.nativeElement.querySelector('.dag-node');
    expect(el.style.left).toBe('50px');
    expect(el.style.top).toBe('120px');
  });

  // --- Ports visibility ---

  it('shows ports in edit mode', () => {
    setNode(makeNode());

    const outputPort = fixture.nativeElement.querySelector('.port-output');
    const inputPort = fixture.nativeElement.querySelector('.port-input');
    expect(outputPort).toBeTruthy();
    expect(inputPort).toBeTruthy();
  });

  it('hides ports in read-only mode', () => {
    setNode(makeNode());
    fixture.componentRef.setInput('readOnly', true);
    fixture.detectChanges();

    const outputPort = fixture.nativeElement.querySelector('.port-output');
    const inputPort = fixture.nativeElement.querySelector('.port-input');
    expect(outputPort).toBeFalsy();
    expect(inputPort).toBeFalsy();
  });

  it('hides delete button in read-only mode', () => {
    setNode(makeNode());
    fixture.componentRef.setInput('readOnly', true);
    fixture.detectChanges();

    const deleteBtn = fixture.nativeElement.querySelector('.delete-btn');
    expect(deleteBtn).toBeFalsy();
  });

  // --- Status ring ---

  it('shows status ring when node has a status', () => {
    setNode(makeNode({ status: 'SUCCESS' }));

    const ring = fixture.nativeElement.querySelector('.status-ring');
    expect(ring).toBeTruthy();
    expect(ring.style.borderColor).toContain('success');
  });

  it('hides status ring when node has no status', () => {
    setNode(makeNode({ status: undefined }));

    const ring = fixture.nativeElement.querySelector('.status-ring');
    expect(ring).toBeFalsy();
  });

  it('applies running class for RUNNING status', () => {
    setNode(makeNode({ status: 'RUNNING' }));

    const nodeEl = fixture.nativeElement.querySelector('.dag-node');
    expect(nodeEl.classList.contains('running')).toBe(true);
  });

  // --- Event emissions ---

  it('emits nodeClick on body click', () => {
    setNode(makeNode());

    component.nodeClick.subscribe(stepId => {
      expect(stepId).toBe(1);
    });

    const body = fixture.nativeElement.querySelector('.node-body');
    body.click();
  });

  it('emits deleteRequested on delete button click', () => {
    setNode(makeNode());

    component.deleteRequested.subscribe(stepId => {
      expect(stepId).toBe(1);
    });

    const btn = fixture.nativeElement.querySelector('.delete-btn');
    btn.click();
  });

  it('emits edgeTargetHover on input port hover', () => {
    setNode(makeNode());

    component.edgeTargetHover.subscribe(stepId => {
      expect(stepId).toBe(1);
    });

    const port = fixture.nativeElement.querySelector('.port-input');
    port.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
  });

  it('does not emit nodeClick for null stepId', () => {
    setNode(makeNode({ stepId: null }));

    let emitted = false;
    component.nodeClick.subscribe(() => { emitted = true; });

    const body = fixture.nativeElement.querySelector('.node-body');
    body.click();

    expect(emitted).toBe(false);
  });
});
