import { DagLayoutService, DagNodeModel } from './dag-layout.service';

function createNode(stepId: number | null, name: string, deps: number[] = []): DagNodeModel {
  return {
    stepId,
    stepName: name,
    stepType: 'CUSTOM_SCRIPT',
    position: { x: 0, y: 0 },
    size: { width: 0, height: 0 },
    dependencies: deps.map(d => ({ dependsOnStepId: d, dependsOnStepName: '', edgeCondition: 'ON_SUCCESS' })),
  };
}

describe('DagLayoutService', () => {
  let service: DagLayoutService;

  beforeEach(() => {
    service = new DagLayoutService();
  });

  it('handles empty input', () => {
    service.layout([]);
    // No error, nothing to check.
  });

  it('positions single node at padding offset', () => {
    const nodes = [createNode(1, 'A')];
    service.layout(nodes);

    expect(nodes[0].position.y).toBe(40); // PADDING
    expect(nodes[0].size.width).toBe(160);
    expect(nodes[0].size.height).toBe(72);
  });

  it('places dependent node below root', () => {
    const nodes = [createNode(1, 'A'), createNode(2, 'B', [1])];
    service.layout(nodes);

    const a = nodes.find(n => n.stepId === 1)!;
    const b = nodes.find(n => n.stepId === 2)!;

    expect(a.position.y).toBeLessThan(b.position.y);
    // B should be exactly one layer below A.
    expect(b.position.y - a.position.y).toBe(72 + 120); // NODE_HEIGHT + VERTICAL_GAP
  });

  it('lays out linear chain top-to-bottom', () => {
    const nodes = [createNode(1, 'A'), createNode(2, 'B', [1]), createNode(3, 'C', [2])];
    service.layout(nodes);

    const a = nodes.find(n => n.stepId === 1)!;
    const b = nodes.find(n => n.stepId === 2)!;
    const c = nodes.find(n => n.stepId === 3)!;

    expect(a.position.y).toBe(40);
    expect(b.position.y).toBe(40 + 72 + 120);
    expect(c.position.y).toBe(40 + 2 * (72 + 120));
  });

  it('lays out diamond DAG correctly', () => {
    // A(root) → B,C(middle) → D(bottom)
    const nodes = [
      createNode(1, 'A'),
      createNode(2, 'B', [1]),
      createNode(3, 'C', [1]),
      createNode(4, 'D', [2, 3]),
    ];
    service.layout(nodes);

    const a = nodes.find(n => n.stepId === 1)!;
    const b = nodes.find(n => n.stepId === 2)!;
    const c = nodes.find(n => n.stepId === 3)!;
    const d = nodes.find(n => n.stepId === 4)!;

    // A is layer 0, B and C are layer 1, D is layer 2.
    expect(a.position.y).toBe(40);
    expect(b.position.y).toBe(c.position.y); // same layer
    expect(d.position.y > b.position.y).toBe(true);
    // B and C should be horizontally separated.
    expect(Math.abs(b.position.x - c.position.x)).toBeGreaterThan(0);
  });

  it('fits wide fan-out within reasonable width', () => {
    // 15 nodes distributed across layers (not all in one layer)
    const nodes: DagNodeModel[] = [createNode(1, 'Root')];
    for (let i = 2; i <= 6; i++) nodes.push(createNode(i, `L1_${i}`, [1]));
    for (let i = 6; i <= 10; i++) nodes.push(createNode(i, `L2_${i}`, [2]));
    for (let i = 10; i <= 15; i++) nodes.push(createNode(i, `L3_${i}`, [7]));
    service.layout(nodes);

    const maxWidth = Math.max(...nodes.map(n => n.position.x + n.size.width));
    expect(maxWidth).toBeLessThan(2000);
  });

  it('handles nodes with null stepId as roots', () => {
    const nodes = [createNode(null, 'Temp'), createNode(1, 'A')];
    service.layout(nodes);

    // Both should be on layer 0 (same y).
    expect(nodes[0].position.y).toBe(nodes[1].position.y);
  });

  it('ignores dependencies on non-existent stepIds', () => {
    const nodes = [createNode(1, 'A'), createNode(2, 'B', [99])];
    service.layout(nodes);

    // B depends on nonexistent 99, so treated as root (layer 0).
    expect(nodes[0].position.y).toBe(nodes[1].position.y);
  });
});
