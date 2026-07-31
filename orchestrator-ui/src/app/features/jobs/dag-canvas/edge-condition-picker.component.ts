import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EdgeCondition } from '@app/core/models/job.model';

const EDGE_CONDITION_OPTIONS: { value: EdgeCondition; label: string; symbol: string }[] = [
  { value: 'ON_SUCCESS', label: 'On Success', symbol: '✓' },
  { value: 'ON_FAILURE', label: 'On Failure', symbol: '✗' },
  { value: 'ALWAYS', label: 'Always', symbol: '∞' },
];

@Component({
  selector: 'app-edge-condition-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './edge-condition-picker.component.html',
  styleUrl: './edge-condition-picker.component.scss',
})
export class EdgeConditionPickerComponent {
  @Input() position = { x: 0, y: 0 };
  @Output() selected = new EventEmitter<EdgeCondition>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('picker') pickerEl!: ElementRef;

  options = EDGE_CONDITION_OPTIONS;

  onSelect(value: EdgeCondition): void {
    this.selected.emit(value);
    this.closed.emit();
  }

  onOutsideClick(event: MouseEvent): void {
    if (this.pickerEl && !(event.target instanceof Node && this.pickerEl.nativeElement.contains(event.target))) {
      this.closed.emit();
    }
  }
}
