import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { DagNodeModel } from './services/dag-layout.service';
import { RunStatus } from '@app/core/models/job.model';
import { iconFor } from '../step-builder/step-palette';

const STATUS_COLORS: Record<RunStatus, string> = {
  PENDING: 'var(--status-pending-bg)',
  RUNNING: 'var(--status-running-bg)',
  SUCCESS: 'var(--status-success-bg)',
  FAILED: 'var(--status-failed-bg)',
  PARTIAL: 'var(--status-partial-bg)',
  CANCELLED: 'var(--status-cancelled-bg)',
};

@Component({
  selector: 'app-dag-node',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  templateUrl: './dag-node.component.html',
  styleUrl: './dag-node.component.scss',
})
export class DagNodeComponent {
  @Input() node!: DagNodeModel;
  @Input() readOnly = false;

  @Output() nodeDragEnd = new EventEmitter<{ stepId: number; position: { x: number; y: number } }>();
  @Output() edgeStart = new EventEmitter<{ stepId: number; portX: number; portY: number }>();
  @Output() edgeTargetHover = new EventEmitter<number>();
  @Output() nodeClick = new EventEmitter<number>();
  @Output() deleteRequested = new EventEmitter<number>();

  iconFor = iconFor;

  get statusColor(): string {
    if (!this.node.status) return '';
    return STATUS_COLORS[this.node.status] ?? '';
  }

  get isRunning(): boolean {
    return this.node.status === 'RUNNING';
  }

  onBodyClick(): void {
    if (this.node.stepId != null) {
      this.nodeClick.emit(this.node.stepId);
    }
  }

  onPortMouseDown(event: MouseEvent): void {
    event.stopPropagation();
    if (this.node.stepId == null) return;
    const el = event.target as HTMLElement;
    const rect = el.getBoundingClientRect();
    this.edgeStart.emit({
      stepId: this.node.stepId,
      portX: rect.left + rect.width / 2,
      portY: rect.top + rect.height / 2,
    });
  }

  onInputPortHover(): void {
    if (this.node.stepId != null) {
      this.edgeTargetHover.emit(this.node.stepId);
    }
  }

  onDeleteClick(event: Event): void {
    event.stopPropagation();
    if (this.node.stepId != null) {
      this.deleteRequested.emit(this.node.stepId);
    }
  }
}
