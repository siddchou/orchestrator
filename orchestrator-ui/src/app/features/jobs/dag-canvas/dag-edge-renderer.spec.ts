import { computeEdgePath, DagNodeBounds } from './dag-edge-renderer';

describe('computeEdgePath', () => {
  const sourceBelow: DagNodeBounds = { x: 100, y: 50, width: 160, height: 80 };
  const targetAbove: DagNodeBounds = { x: 200, y: 300, width: 160, height: 80 };

  it('returns a valid SVG path string', () => {
    const result = computeEdgePath(sourceBelow, targetAbove);
    expect(result.d.startsWith('M ')).toBe(true);
    expect(result.d.includes(' C ')).toBe(true);
  });

  it('starts at source bottom-center', () => {
    const result = computeEdgePath(sourceBelow, targetAbove);
    // Source bottom-center: x=100+80=180, y=50+80=130
    expect(result.d.startsWith('M 180 130 C ')).toBe(true);
  });

  it('ends at target top-center', () => {
    const result = computeEdgePath(sourceBelow, targetAbove);
    // Target top-center: x=200+80=280, y=300
    expect(result.d.endsWith(', 280 300')).toBe(true);
  });

  it('places midpoint between control points', () => {
    const result = computeEdgePath(sourceBelow, targetAbove);
    // With dy=170, controlDistance=min(85, 150)=85, max(50,85)=85
    // cp1: (180, 130+85=215), cp2: (280, 300-85=215)
    // mid: ((180+280)/2=230, (215+215)/2=215)
    expect(result.midX).toBeCloseTo(230);
    expect(result.midY).toBeCloseTo(215);
  });

  it('uses minimum control distance of 50 for close nodes', () => {
    const closeTarget: DagNodeBounds = { x: 200, y: 140, width: 160, height: 80 };
    // dy = |140 - 130| = 10, controlDistance = max(50, min(5, 150)) = 50
    const result = computeEdgePath(sourceBelow, closeTarget);
    expect(result.d).toContain(' C ');
    // cp1: (180, 130+50=180), cp2: (280, 140-50=90) → midY = (180+90)/2 = 135
    expect(result.midY).toBeCloseTo(135);
  });

  describe('target above source (reverse flow)', () => {
    const reversedSource: DagNodeBounds = { x: 100, y: 300, width: 160, height: 80 };
    const reversedTarget: DagNodeBounds = { x: 200, y: 50, width: 160, height: 80 };

    it('curves upward when target is above source', () => {
      const result = computeEdgePath(reversedSource, reversedTarget);
      // Source bottom-center: (180, 380), Target top-center: (280, 50)
      expect(result.d.startsWith('M 180 380 C ')).toBe(true);
      expect(result.d.endsWith(', 280 50')).toBe(true);
    });

    it('control points point upward', () => {
      const result = computeEdgePath(reversedSource, reversedTarget);
      // dy = |50 - 380| = 330, controlDistance = min(165, 150) = 150
      // cp1: (180, 380-150=230), cp2: (280, 50+150=200)
      // mid: ((180+280)/2=230, (230+200)/2=215)
      expect(result.midY).toBeCloseTo(215);
    });
  });

  describe('control distance clamping', () => {
    it('caps control distance at 150 for far apart nodes', () => {
      const farTarget: DagNodeBounds = { x: 200, y: 1000, width: 160, height: 80 };
      // dy = |1000 - 130| = 870, controlDistance = min(435, 150) = 150
      const result = computeEdgePath(sourceBelow, farTarget);
      // cp1: (180, 130+150=280), cp2: (280, 1000-150=850)
      // midY = (280 + 850) / 2 = 565
      expect(result.midY).toBeCloseTo(565);
    });
  });

  describe('horizontal alignment', () => {
    it('produces straight vertical curve for aligned nodes', () => {
      const alignedTarget: DagNodeBounds = { x: 100, y: 300, width: 160, height: 80 };
      const result = computeEdgePath(sourceBelow, alignedTarget);
      // Both centers at x=180, so cp1x=cp2x=endX=startX=180
      expect(result.midX).toBeCloseTo(180);
    });

    it('handles offset nodes', () => {
      const leftSource: DagNodeBounds = { x: 0, y: 50, width: 160, height: 80 };
      const rightTarget: DagNodeBounds = { x: 400, y: 300, width: 160, height: 80 };
      const result = computeEdgePath(leftSource, rightTarget);
      // Source center: 80, Target center: 480
      // midX should be between the two centers
      expect(result.midX).toBeGreaterThan(80);
      expect(result.midX).toBeLessThan(480);
    });
  });
});
