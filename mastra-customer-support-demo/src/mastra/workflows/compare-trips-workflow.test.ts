import { describe, expect, it } from 'vitest';
import { mastra } from '../index.ts';

async function runComparison(totalBudgetCents: number) {
  const workflow = mastra.getWorkflow('compareTripsWorkflow');
  const run = await workflow.createRun();
  return run.start({
    inputData: {
      from: '上海',
      maxDays: 2,
      partySize: 2,
      totalBudgetCents,
    },
  });
}

describe('compareTripsWorkflow', () => {
  it('fans out per trail and recommends the cheapest trip within budget', async () => {
    const result = await runComparison(100_000);

    expect(result.status).toBe('success');
    if (result.status !== 'success') {
      throw new Error('Workflow did not succeed');
    }

    expect(result.result.status).toBe('recommended');
    // Cheapest reachable option from 上海: 莫干山 by bus (¥65 × round trip × 2
    // people) plus one hostel night, beating the 四明山 rail day trip (¥456).
    expect(result.result.ranked[0].trailId).toBe('trail_moganshan');
    expect(result.result.ranked[0].totalCents).toBe(6500 * 2 * 2 + 12000);
    expect(result.result.ranked.length).toBeGreaterThanOrEqual(2);
    expect(result.result.summary).toContain('推荐');
  });

  it('takes the over-budget branch when nothing fits', async () => {
    const result = await runComparison(10_000);

    expect(result.status).toBe('success');
    if (result.status !== 'success') {
      throw new Error('Workflow did not succeed');
    }

    expect(result.result.status).toBe('over-budget');
    expect(result.result.summary).toContain('预算不足');
  });
});
