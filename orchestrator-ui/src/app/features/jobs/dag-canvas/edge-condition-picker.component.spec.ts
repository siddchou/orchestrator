import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EdgeConditionPickerComponent } from './edge-condition-picker.component';
import { EdgeCondition } from '@app/core/models/job.model';

describe('EdgeConditionPickerComponent', () => {
  let component: EdgeConditionPickerComponent;
  let fixture: ComponentFixture<EdgeConditionPickerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EdgeConditionPickerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EdgeConditionPickerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('position', { x: 100, y: 200 });
    fixture.detectChanges();
  });

  it('renders three options', () => {
    const options = fixture.nativeElement.querySelectorAll('.option');
    expect(options.length).toBe(3);
  });

  it('positions at correct coordinates', () => {
    const picker = fixture.nativeElement.querySelector('.edge-condition-picker');
    expect(picker.style.left).toBe('100px');
    expect(picker.style.top).toBe('200px');
  });

  it('emits ON_SUCCESS on first option click', () => {
    component.selected.subscribe((value: EdgeCondition) => {
      expect(value).toBe('ON_SUCCESS');
    });

    const options = fixture.nativeElement.querySelectorAll('.option');
    options[0].click();
    fixture.detectChanges();
  });

  it('emits ON_FAILURE on second option click', () => {
    component.selected.subscribe((value: EdgeCondition) => {
      expect(value).toBe('ON_FAILURE');
    });

    const options = fixture.nativeElement.querySelectorAll('.option');
    options[1].click();
    fixture.detectChanges();
  });

  it('emits ALWAYS on third option click', () => {
    component.selected.subscribe((value: EdgeCondition) => {
      expect(value).toBe('ALWAYS');
    });

    const options = fixture.nativeElement.querySelectorAll('.option');
    options[2].click();
    fixture.detectChanges();
  });

  it('emits closed after selection', () => {
    let closedEmitted = false;
    component.closed.subscribe(() => {
      closedEmitted = true;
    });

    const options = fixture.nativeElement.querySelectorAll('.option');
    options[0].click();
    fixture.detectChanges();

    expect(closedEmitted).toBe(true);
  });

  it('shows correct symbols for each option', () => {
    const symbols = fixture.nativeElement.querySelectorAll('.symbol');
    expect(symbols[0].textContent).toContain('✓');
    expect(symbols[1].textContent).toContain('✗');
    expect(symbols[2].textContent).toContain('∞');
  });

  it('emits closed on outside click', () => {
    let closedEmitted = false;
    component.closed.subscribe(() => {
      closedEmitted = true;
    });

    // Simulate a click outside the picker element
    const outsideEvent = new MouseEvent('click', { bubbles: true });
    Object.defineProperty(outsideEvent, 'target', { value: document.body, writable: false });
    component.onOutsideClick(outsideEvent);

    expect(closedEmitted).toBe(true);
  });

  it('does not emit closed on inside click', () => {
    let closedEmitted = false;
    component.closed.subscribe(() => {
      closedEmitted = true;
    });

    fixture.detectChanges();
    const pickerEl = fixture.nativeElement.querySelector('.edge-condition-picker');
    const insideEvent = new MouseEvent('click', { bubbles: true });
    Object.defineProperty(insideEvent, 'target', { value: pickerEl, writable: false });
    component.onOutsideClick(insideEvent);

    expect(closedEmitted).toBe(false);
  });
});
