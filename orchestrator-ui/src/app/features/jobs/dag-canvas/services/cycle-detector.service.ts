import { Injectable } from '@angular/core';

/**
 * Detects cycles in the DAG step dependency graph using DFS with node coloring.
 * Pure logic — no Angular dependencies except the Injectable decorator for consistency.
 */
@Injectable({ providedIn: 'root' })
export class CycleDetectorService {
  /**
   * Returns true if adding an edge `stepId → dependsOnStepId` would create a cycle.
   * The edge means "stepId depends on dependsOnStepId" (dependsOnStepId must run first).
   *
   * @param stepId - the step that would gain a new dependency
   * @param dependsOnStepId - the step it would depend on
   * @param existingEdges - map of stepId → array of stepIds it currently depends on
   */
  wouldCreateCycle(
    stepId: number,
    dependsOnStepId: number,
    existingEdges: Map<number, number[]>,
  ): boolean {
    if (stepId === dependsOnStepId) return true;

    // Temporarily add the edge.
    const currentDeps = existingEdges.get(stepId) ?? [];
    const withNewEdge = new Map(existingEdges);
    withNewEdge.set(stepId, [...currentDeps, dependsOnStepId]);

    return this.hasCycle(withNewEdge);
  }

  /**
   * Returns true if the current graph contains any cycle.
   * Uses DFS with white/gray/black coloring.
   */
  hasCycle(existingEdges: Map<number, number[]>): boolean {
    const allNodes = new Set<number>();
    for (const [from, toList] of existingEdges) {
      allNodes.add(from);
      for (const to of toList) allNodes.add(to);
    }

    const color = new Map<number, 'white' | 'gray' | 'black'>();
    for (const node of allNodes) color.set(node, 'white');

    for (const node of allNodes) {
      if (color.get(node) === 'white') {
        if (this.dfs(node, existingEdges, color)) return true;
      }
    }

    return false;
  }

  private dfs(
    node: number,
    edges: Map<number, number[]>,
    color: Map<number, 'white' | 'gray' | 'black'>,
  ): boolean {
    color.set(node, 'gray');

    for (const neighbor of edges.get(node) ?? []) {
      const nColor = color.get(neighbor);
      if (nColor === 'gray') return true; // back edge → cycle
      if (nColor === 'white' && this.dfs(neighbor, edges, color)) return true;
    }

    color.set(node, 'black');
    return false;
  }
}
