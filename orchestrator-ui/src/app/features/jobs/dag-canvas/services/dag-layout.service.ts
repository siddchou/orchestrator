import { Injectable } from '@angular/core';
import { RunStatus, StepDependency } from '@app/core/models/job.model';

/** Node position on the canvas */
export interface Position {
  x: number;
  y: number;
}

/** A node to be laid out on the DAG canvas */
export interface DagNodeModel {
  stepId: number | null;
  stepName: string;
  stepType: string;
  position: Position;
  size: { width: number; height: number };
  dependencies?: StepDependency[];
  status?: RunStatus;           // only in run-view mode
}

/** Default node dimensions for layout calculation */
const NODE_WIDTH = 160;
const NODE_HEIGHT = 72;
const HORIZONTAL_GAP = 40;
const VERTICAL_GAP = 120;
const PADDING = 40;

@Injectable({ providedIn: 'root' })
export class DagLayoutService {
  /**
   * Computes layered top-to-bottom positions for all nodes and mutates their `position` in place.
   * Nodes with null stepId are treated as independent roots (layer 0).
   */
  layout(nodes: DagNodeModel[]): void {
    if (nodes.length === 0) return;

    const idSet = new Set<number>();
    for (const n of nodes) {
      if (n.stepId !== null && n.stepId !== undefined) idSet.add(n.stepId);
    }

    // Build adjacency: stepId → set of stepIds it depends on
    const depsOf = new Map<number, Set<number>>();
    for (const n of nodes) {
      if (n.stepId === null || n.stepId === undefined) continue;
      const depIds = new Set<number>();
      for (const d of n.dependencies ?? []) {
        if (idSet.has(d.dependsOnStepId)) depIds.add(d.dependsOnStepId);
      }
      depsOf.set(n.stepId, depIds);
    }

    // Assign layers: roots → 0, each node → max(deps layers) + 1
    const layerOf = new Map<number | null, number>();
    const resolveLayer = (id: number | null): number => {
      if (layerOf.has(id)) return layerOf.get(id)!;
      if (id === null) { layerOf.set(null, 0); return 0; }
      const deps = depsOf.get(id) ?? new Set();
      let maxDepLayer = -1;
      for (const d of deps) {
        if (idSet.has(d)) {
          maxDepLayer = Math.max(maxDepLayer, resolveLayer(d));
        }
      }
      const layer = maxDepLayer + 1;
      layerOf.set(id, layer);
      return layer;
    };

    for (const n of nodes) resolveLayer(n.stepId);

    // Group by layer
    const layers = new Map<number, DagNodeModel[]>();
    for (const n of nodes) {
      const l = layerOf.get(n.stepId) ?? 0;
      if (!layers.has(l)) layers.set(l, []);
      layers.get(l)!.push(n);
    }

    const sortedLayerKeys = [...layers.keys()].sort((a, b) => a - b);
    const maxLayerWidth = Math.max(...[...layers.values()].map(layer => layer.length));

    // Center each layer horizontally within the widest layer's span.
    const canvasWidth = maxLayerWidth * (NODE_WIDTH + HORIZONTAL_GAP) - HORIZONTAL_GAP + PADDING * 2;

    sortedLayerKeys.forEach((key, layerIdx) => {
      const layerNodes = layers.get(key)!;
      const layerWidth = layerNodes.length * (NODE_WIDTH + HORIZONTAL_GAP) - HORIZONTAL_GAP;
      const offsetX = PADDING + (canvasWidth - layerWidth) / 2;
      const y = PADDING + layerIdx * (NODE_HEIGHT + VERTICAL_GAP);

      for (let i = 0; i < layerNodes.length; i++) {
        layerNodes[i].position.x = Math.round(offsetX + i * (NODE_WIDTH + HORIZONTAL_GAP));
        layerNodes[i].position.y = y;
        layerNodes[i].size.width = NODE_WIDTH;
        layerNodes[i].size.height = NODE_HEIGHT;
      }
    });
  }
}
