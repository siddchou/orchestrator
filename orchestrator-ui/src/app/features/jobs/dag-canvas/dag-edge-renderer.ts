/** Position and size of a node on the DAG canvas */
export interface DagNodeBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Result of computing an edge path between two nodes */
export interface DagEdgePath {
  /** SVG `d` attribute for the `<path>` element */
  d: string;
  /** Midpoint x coordinate for label placement */
  midX: number;
  /** Midpoint y coordinate for label placement */
  midY: number;
}

/**
 * Computes a cubic bezier SVG path from source node's bottom-center to target node's top-center.
 * Works regardless of relative node positions — control points adapt so the curve always flows
 * naturally from source output to target input.
 */
export function computeEdgePath(source: DagNodeBounds, target: DagNodeBounds): DagEdgePath {
  const startX = source.x + source.width / 2;
  const startY = source.y + source.height;
  const endX = target.x + target.width / 2;
  const endY = target.y;

  const dy = Math.abs(endY - startY);
  // Control point distance scales with vertical gap, clamped to a reasonable range.
  const controlDistance = Math.max(50, Math.min(dy * 0.5, 150));

  let cp1x: number, cp1y: number, cp2x: number, cp2y: number;

  if (endY >= startY) {
    // Target below source — top-to-bottom flow
    cp1x = startX;
    cp1y = startY + controlDistance;
    cp2x = endX;
    cp2y = endY - controlDistance;
  } else {
    // Target above source — bottom-to-top flow
    cp1x = startX;
    cp1y = startY - controlDistance;
    cp2x = endX;
    cp2y = endY + controlDistance;
  }

  const d = `M ${startX} ${startY} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${endX} ${endY}`;

  // Midpoint is the average of the two inner control points (smoothest part of curve).
  const midX = (cp1x + cp2x) / 2;
  const midY = (cp1y + cp2y) / 2;

  return { d, midX, midY };
}
