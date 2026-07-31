import { CycleDetectorService } from './cycle-detector.service';

describe('CycleDetectorService', () => {
  let service: CycleDetectorService;

  beforeEach(() => {
    service = new CycleDetectorService();
  });

  function edgeMap(entries: [number, number[]][]): Map<number, number[]> {
    return new Map(entries);
  }

  describe('wouldCreateCycle', () => {
    it('returns true for self-reference', () => {
      expect(service.wouldCreateCycle(1, 1, edgeMap([]))).toBe(true);
    });

    it('returns false for a safe edge in empty graph', () => {
      expect(service.wouldCreateCycle(2, 1, edgeMap([]))).toBe(false);
    });

    it('detects cycle when adding back-edge to linear chain', () => {
      // 3 → [2], 2 → [1]  (3 depends on 2, 2 depends on 1)
      const edges = edgeMap([[3, [2]], [2, [1]]]);
      // Adding 1 → [3] would create 1→3→2→1 cycle
      expect(service.wouldCreateCycle(1, 3, edges)).toBe(true);
    });

    it('returns false for non-cycling edge on linear chain', () => {
      // 3 → [2], 2 → [1]
      const edges = edgeMap([[3, [2]], [2, [1]]]);
      // Adding 4 → [3] is safe: 4→3→2→1
      expect(service.wouldCreateCycle(4, 3, edges)).toBe(false);
    });

    it('detects cycle in diamond shape', () => {
      // Diamond: A→[B,C], B→[D], C→[D]
      // Edges (depends-on direction): B → [A], C → [A], D → [B, C]
      const edges = edgeMap([[2, [1]], [3, [1]], [4, [2, 3]]]);
      // Adding 1 → [4] would cycle: 1→4→2→1 or 1→4→3→1
      expect(service.wouldCreateCycle(1, 4, edges)).toBe(true);
    });

    it('allows parallel dependency', () => {
      // 2 → [1], 3 → [1] — both depend on 1 independently
      const edges = edgeMap([[2, [1]]]);
      expect(service.wouldCreateCycle(3, 1, edges)).toBe(false);
    });
  });

  describe('hasCycle', () => {
    it('returns false for empty graph', () => {
      expect(service.hasCycle(edgeMap([]))).toBe(false);
    });

    it('returns false for single node with no edges', () => {
      expect(service.hasCycle(edgeMap([[1, []]]))).toBe(false);
    });

    it('returns false for linear chain', () => {
      expect(service.hasCycle(edgeMap([[3, [2]], [2, [1]]]))).toBe(false);
    });

    it('returns false for diamond (DAG)', () => {
      expect(service.hasCycle(edgeMap([[2, [1]], [3, [1]], [4, [2, 3]]]))).toBe(false);
    });

    it('returns true for direct cycle A→B→A', () => {
      expect(service.hasCycle(edgeMap([[1, [2]], [2, [1]]]))).toBe(true);
    });

    it('returns true for three-node cycle', () => {
      expect(service.hasCycle(edgeMap([[1, [3]], [3, [2]], [2, [1]]]))).toBe(true);
    });

    it('returns true for self-loop', () => {
      expect(service.hasCycle(edgeMap([[1, [1]]]))).toBe(true);
    });
  });
});
