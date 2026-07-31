import { Component, ElementRef, EventEmitter, inject, Input, OnDestroy, OnChanges, SimpleChanges, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { DagNodeComponent } from './dag-node.component';
import { EdgeConditionPickerComponent } from './edge-condition-picker.component';
import { DagLayoutService } from './services/dag-layout.service';
import { CycleDetectorService } from './services/cycle-detector.service';
import { computeEdgePath, DagNodeBounds, DagEdgePath } from './dag-edge-renderer';
import { JobStepWithDependencies, StepDependency, EdgeCondition } from '@app/core/models/job.model';

interface PanZoomState {
  offsetX: number;
  offsetY: number;
  scale: number;
}

@Component({
  selector: 'app-dag-canvas',
  standalone: true,
  imports: [CommonModule, MatButtonModule, DagNodeComponent, EdgeConditionPickerComponent],
  templateUrl: './dag-canvas.component.html',
  styleUrl: './dag-canvas.component.scss',
})
export class DagCanvasComponent implements OnChanges, OnDestroy {
  @Input() jobId!: number;
  @Input() steps!: JobStepWithDependencies[];
  @Output() dependenciesChanged = new EventEmitter<{ stepId: number; deps: StepDependency[] }>();
  @Output() stepSelected = new EventEmitter<number>();
  @Output() stepDeleted = new EventEmitter<number>();
  @Output() addStepRequested = new EventEmitter<void>();

  @ViewChild('canvasContainer') canvasContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('svgLayer') svgLayer!: ElementRef<SVGSVGElement>;

  nodes: Array<{
    stepId: number | null;
    stepName: string;
    stepType: string;
    position: { x: number; y: number };
    size: { width: number; height: number };
    dependencies?: StepDependency[];
  }> = [];

  edges: Array<{ from: number; to: number; condition: EdgeCondition; id?: number }> = [];
  rubberBand: { active: boolean; fromStepId: number | null; x: number; y: number } | null = null;
  pickerVisible = false;
  pickerPosition = { x: 0, y: 0 };
  pickerTargetStepId: number | null = null;

  panZoom: PanZoomState = { offsetX: 0, offsetY: 0, scale: 1 };
  isPanning = false;
  lastMousePos = { x: 0, y: 0 };

  private layoutService = new DagLayoutService();
  private cycleDetector = new CycleDetectorService();

  readonly MIN_SCALE = 0.3;
  readonly MAX_SCALE = 2;

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['steps'] && this.steps) {
      this.onStepsChange(this.steps);
    }
  }

  ngOnDestroy(): void {
    // cleanup if needed
  }

  onStepsChange(newSteps: JobStepWithDependencies[]): void {
    this.nodes = newSteps.map(s => ({
      stepId: s.stepId,
      stepName: s.stepName,
      stepType: s.stepType,
      position: { x: 0, y: 0 },
      size: { width: 160, height: 72 },
      dependencies: s.dependencies ?? [],
    }));
    this.buildEdgesFromDependencies();
    this.layoutService.layout(this.nodes as any);
  }

  private buildEdgesFromDependencies(): void {
    this.edges = [];
    for (const node of this.nodes) {
      if (!node.stepId || !node.dependencies?.length) continue;
      for (const dep of node.dependencies) {
        this.edges.push({
          from: dep.dependsOnStepId,
          to: node.stepId,
          condition: dep.edgeCondition,
          id: dep.stepDependencyId,
        });
      }
    }
  }

  onWheel(event: WheelEvent): void {
    if (event.ctrlKey || event.metaKey) {
      event.preventDefault();
      const delta = -event.deltaY;
      const factor = delta > 0 ? 1.1 : 0.9;
      const newScale = Math.min(this.MAX_SCALE, Math.max(this.MIN_SCALE, this.panZoom.scale * factor));

      // Zoom toward cursor
      const rect = this.canvasContainer.nativeElement.getBoundingClientRect();
      const mx = event.clientX - rect.left;
      const my = event.clientY - rect.top;

      this.panZoom.offsetX = mx - (mx - this.panZoom.offsetX) * (newScale / this.panZoom.scale);
      this.panZoom.offsetY = my - (my - this.panZoom.offsetY) * (newScale / this.panZoom.scale);
      this.panZoom.scale = newScale;
    }
  }

  onMouseDown(event: MouseEvent): void {
    if (event.button === 1 || (event.button === 0 && event.shiftKey)) {
      this.isPanning = true;
      this.lastMousePos = { x: event.clientX, y: event.clientY };
      event.preventDefault();
    }
  }

  onMouseMove(event: MouseEvent): void {
    if (this.isPanning) {
      const dx = event.clientX - this.lastMousePos.x;
      const dy = event.clientY - this.lastMousePos.y;
      this.panZoom.offsetX += dx;
      this.panZoom.offsetY += dy;
      this.lastMousePos = { x: event.clientX, y: event.clientY };
    }

    if (this.rubberBand?.active) {
      const rect = this.canvasContainer.nativeElement.getBoundingClientRect();
      this.rubberBand.x = event.clientX - rect.left;
      this.rubberBand.y = event.clientY - rect.top;
    }
  }

  onMouseUp(): void {
    this.isPanning = false;
  }

  onEdgeStart(event: { stepId: number; portX: number; portY: number }): void {
    const rect = this.canvasContainer.nativeElement.getBoundingClientRect();
    this.rubberBand = {
      active: true,
      fromStepId: event.stepId,
      x: event.portX - rect.left,
      y: event.portY - rect.top,
    };
  }

  onEdgeTargetHover(stepId: number): void {
    // Highlight handled by CSS hover state
  }

  onNodeClick(stepId: number): void {
    this.stepSelected.emit(stepId);
  }

  onDeleteRequested(stepId: number): void {
    this.stepDeleted.emit(stepId);
  }

  zoomIn(): void {
    const factor = 1.2;
    const newScale = Math.min(this.MAX_SCALE, this.panZoom.scale * factor);
    const center = this.getCanvasCenter();
    this.panZoom.offsetX = center.x - (center.x - this.panZoom.offsetX) * (newScale / this.panZoom.scale);
    this.panZoom.offsetY = center.y - (center.y - this.panZoom.offsetY) * (newScale / this.panZoom.scale);
    this.panZoom.scale = newScale;
  }

  zoomOut(): void {
    const factor = 0.8;
    const newScale = Math.max(this.MIN_SCALE, this.panZoom.scale * factor);
    const center = this.getCanvasCenter();
    this.panZoom.offsetX = center.x - (center.x - this.panZoom.offsetX) * (newScale / this.panZoom.scale);
    this.panZoom.offsetY = center.y - (center.y - this.panZoom.offsetY) * (newScale / this.panZoom.scale);
    this.panZoom.scale = newScale;
  }

  fitToView(): void {
    if (!this.nodes.length || !this.canvasContainer.nativeElement.children.length) return;
    const container = this.canvasContainer.nativeElement;
    const cw = container.clientWidth;
    const ch = container.clientHeight;

    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const n of this.nodes) {
      minX = Math.min(minX, n.position.x);
      minY = Math.min(minY, n.position.y);
      maxX = Math.max(maxX, n.position.x + n.size.width);
      maxY = Math.max(maxY, n.position.y + n.size.height);
    }

    const contentW = maxX - minX + 120;
    const contentH = maxY - minY + 120;
    const scale = Math.min(cw / contentW, ch / contentH, 1.5);
    this.panZoom.scale = Math.max(this.MIN_SCALE, Math.min(this.MAX_SCALE, scale));
    this.panZoom.offsetX = (cw - contentW * this.panZoom.scale) / 2 - minX * this.panZoom.scale;
    this.panZoom.offsetY = (ch - contentH * this.panZoom.scale) / 2 - minY * this.panZoom.scale;
  }

  private getCanvasCenter(): { x: number; y: number } {
    const container = this.canvasContainer.nativeElement;
    return { x: container.clientWidth / 2, y: container.clientHeight / 2 };
  }

  getTransform(): string {
    const { offsetX, offsetY, scale } = this.panZoom;
    return `translate(${offsetX}px, ${offsetY}px) scale(${scale})`;
  }

  getNodeBounds(stepId: number): DagNodeBounds | null {
    const node = this.nodes.find(n => n.stepId === stepId);
    if (!node) return null;
    return { x: node.position.x, y: node.position.y, width: node.size.width, height: node.size.height };
  }

  getEdgePath(edge: { from: number; to: number }): DagEdgePath | null {
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

  onPickerSelected(condition: EdgeCondition): void {
    if (this.rubberBand?.fromStepId != null && this.pickerTargetStepId != null) {
      // Check for cycle
      const existingEdges = new Map<number, number[]>();
      for (const n of this.nodes) {
        if (n.stepId && n.dependencies?.length) {
          existingEdges.set(n.stepId, n.dependencies.map(d => d.dependsOnStepId));
        }
      }

      // pickerTargetStepId is the target node; rubberBand.fromStepId is the source
      // The target depends on the source → edge from source to target
      if (this.cycleDetector.wouldCreateCycle(this.pickerTargetStepId, this.rubberBand.fromStepId, existingEdges)) {
        this.pickerVisible = false;
        return;
      }

      const sourceNode = this.nodes.find(n => n.stepId === this.rubberBand.fromStepId);
      if (sourceNode) {
        // Add dependency: pickerTargetStepId depends on rubberBand.fromStepId
        const targetNode = this.nodes.find(n => n.stepId === this.pickerTargetStepId);
        if (targetNode) {
          if (!targetNode.dependencies) targetNode.dependencies = [];
          // Check for duplicate
          const exists = targetNode.dependencies.some(d => d.dependsOnStepId === this.rubberBand.fromStepId);
          if (!exists) {
            targetNode.dependencies.push({
              dependsOnStepId: this.rubberBand.fromStepId!,
              dependsOnStepName: sourceNode.stepName,
              edgeCondition: condition,
            });
            this.buildEdgesFromDependencies();
          }
        }
      }
    }
    this.pickerVisible = false;
    this.rubberBand = null;
  }

  onPickerClosed(): void {
    this.pickerVisible = false;
    this.rubberBand = null;
  }
}
