import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';
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

  private isDragging = false;
  private dragStartMouse = { x: 0, y: 0 };
  private dragStartPosition = { x: 0, y: 0 };

  get statusColor(): string {
    if (!this.node.status) return '';
    return STATUS_COLORS[this.node.status] ?? '';
  }

  get isRunning(): boolean {
    return this.node.status === 'RUNNING';
  }

  onBodyMouseDown(event: MouseEvent): void {
    // Left click only, not on ports or buttons
    if (event.button !== 0) return;
    this.isDragging = true;
    this.dragStartMouse = { x: event.clientX, y: event.clientY };
    this.dragStartPosition = { ...this.node.position };
    document.addEventListener('mousemove', this.onDocumentMouseMove);
    document.addEventListener('mouseup', this.onDocumentMouseUp);
  }

  private onDocumentMouseMove = (event: MouseEvent): void => {
    if (!this.isDragging) return;
    const dx = event.clientX - this.dragStartMouse.x;
    const dy = event.clientY - this.dragStartMouse.y;
    this.node.position.x = this.dragStartPosition.x + dx;
    this.node.position.y = this.dragStartPosition.y + dy;
  };

  private onDocumentMouseUp = (): void => {
    document.removeEventListener('mousemove', this.onDocumentMouseMove);
    document.removeEventListener('mouseup', this.onDocumentMouseUp);
    if (this.isDragging && this.node.stepId != null) {
      this.nodeDragEnd.emit({
        stepId: this.node.stepId,
        position: { ...this.node.position },
      });
    }
    this.isDragging = false;
  };

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
