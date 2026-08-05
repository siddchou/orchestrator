import { Component, Input, OnChanges, SimpleChanges, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DagLayoutService, DagNodeModel } from './services/dag-layout.service';
import { computeEdgePath, DagNodeBounds, DagEdgePath } from './dag-edge-renderer';
import { JobRunDetail, RunStepDetail } from '@app/core/models/run.model';
import { RunStatus, StepDependency, EdgeCondition } from '@app/core/models/job.model';
import { iconFor } from '../step-builder/step-palette';

interface RunNode {
  stepId: number | null;
  stepName: string;
  stepType: string;
  position: { x: number; y: number };
  size: { width: number; height: number };
  status: RunStatus;
  dependencies?: StepDependency[];
}

interface RunEdge {
  from: number;
  to: number;
  condition: EdgeCondition;
  toStatus: RunStatus;
}

const STATUS_COLORS: Record<RunStatus, string> = {
  PENDING: '#9e9e9e',
  RUNNING: '#ff9800',
  SUCCESS: '#4caf50',
  FAILED: '#f44336',
  PARTIAL: '#ff9800',
  CANCELLED: '#9c27b0',
};

@Component({
  selector: 'app-run-dag-canvas',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  templateUrl: './run-dag-canvas.component.html',
  styleUrl: './run-dag-canvas.component.scss',
})
export class RunDagCanvasComponent implements OnChanges {
  @Input() run!: JobRunDetail;
  @Input() stepDependencies!: Record<number, StepDependency[]>;

  private layoutService = new DagLayoutService();
  private cdr = inject(ChangeDetectorRef);

  nodes: RunNode[] = [];
  edges: RunEdge[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['run'] && this.run) {
      this.buildNodes();
    }
    // Build edges whenever dependencies change OR after nodes are built from run
    if (changes['stepDependencies'] || changes['run']) {
      this.buildEdges();
    }
  }

  private buildNodes(): void {
    const runSteps = this.run?.steps ?? [];
    const depsMap = this.stepDependencies ?? {};

    this.nodes = runSteps.map(step => ({
      stepId: step.runStepId,
      stepName: step.stepName,
      stepType: step.stepType,
      position: { x: 0, y: 0 },
      size: { width: 160, height: 72 },
      status: step.status,
      dependencies: depsMap[step.runStepId] ?? [],
    }));

    this.layoutService.layout(this.nodes as DagNodeModel[]);
    this.cdr.detectChanges();
  }

  private buildEdges(): void {
    this.edges = [];
    const statusMap = new Map<number, RunStatus>();
    for (const s of this.run?.steps ?? []) {
      statusMap.set(s.runStepId, s.status);
    }

    for (const node of this.nodes) {
      if (!node.stepId || !node.dependencies?.length) continue;
      for (const dep of node.dependencies) {
        this.edges.push({
          from: dep.dependsOnStepId,
          to: node.stepId,
          condition: dep.edgeCondition,
          toStatus: statusMap.get(node.stepId) ?? 'PENDING',
        });
      }
    }
  }

  getNodeBounds(stepId: number): DagNodeBounds | null {
    const node = this.nodes.find(n => n.stepId === stepId);
    if (!node) return null;
    return { x: node.position.x, y: node.position.y, width: node.size.width, height: node.size.height };
  }

  getEdgePath(edge: RunEdge): DagEdgePath | null {
    const source = this.getNodeBounds(edge.from);
    const target = this.getNodeBounds(edge.to);
    if (!source || !target) return null;
    return computeEdgePath(source, target);
  }

  conditionSymbol(condition: EdgeCondition): string {
    switch (condition) {
      case 'ON_SUCCESS': return '✓';
      case 'ON_FAILURE': return '✗';
      case 'ALWAYS': return '∞';
    }
  }

  edgeColor(status: RunStatus): string {
    return STATUS_COLORS[status] ?? '#9e9e9e';
  }

  statusColor(status: RunStatus): string {
    return STATUS_COLORS[status] ?? '';
  }

  isRunning(status: RunStatus): boolean {
    return status === 'RUNNING';
  }

  iconFor = iconFor;

  getTransform(): string {
    return 'translate(0px, 0px) scale(1)';
  }
}
