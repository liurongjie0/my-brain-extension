import { describe, expect, it } from 'vitest';
import {
  buildPackingListTool,
  searchLodgingTool,
  searchTrailsTool,
  searchTransportTool,
  travelPlannerDirectTools,
} from './travel-tools.ts';

type AnyTool = {
  execute?: (input: never, context: never) => Promise<unknown>;
};

async function runTool<Result>(tool: AnyTool, input: unknown): Promise<Result> {
  const execute = tool.execute;
  expect(execute).toBeDefined();

  if (!execute) {
    throw new Error('Tool execute is not configured');
  }

  return (await execute(input as never, {} as never)) as Result;
}

describe('travelPlannerDirectTools', () => {
  it('keeps transport and lodging searches off the routing agent', () => {
    expect(Object.keys(travelPlannerDirectTools)).toEqual([
      'searchTrailsTool',
      'buildPackingListTool',
    ]);
  });
});

describe('searchTrailsTool', () => {
  it('finds trails by keyword', async () => {
    const result = await runTool<{ trails: Array<{ id: string }> }>(
      searchTrailsTool,
      { keyword: '徽杭' },
    );
    expect(result.trails.map((trail) => trail.id)).toEqual(['trail_huihang']);
  });
});

describe('searchTransportTool', () => {
  it('lists transport options for a city and trail', async () => {
    const result = await runTool<{ options: Array<{ mode: string }> }>(
      searchTransportTool,
      { from: '上海', trailId: 'trail_siming' },
    );
    expect(result.options).toHaveLength(1);
    expect(result.options[0].mode).toBe('high-speed-rail');
  });

  it('propagates unknown-trail errors', async () => {
    await expect(
      runTool(searchTransportTool, { from: '上海', trailId: 'trail_unknown' }),
    ).rejects.toThrow('Trail not found');
  });
});

describe('searchLodgingTool', () => {
  it('filters by nightly budget and explains empty results', async () => {
    const within = await runTool<{ options: Array<{ id: string }> }>(
      searchLodgingTool,
      { trailId: 'trail_huihang', maxPricePerNightCents: 30000 },
    );
    expect(within.options.map((option) => option.id)).toEqual([
      'lg_hh_farmstay',
      'lg_hh_inn',
    ]);

    const empty = await runTool<{ options: unknown[]; reason?: string }>(
      searchLodgingTool,
      { trailId: 'trail_huihang', maxPricePerNightCents: 1000 },
    );
    expect(empty.options).toEqual([]);
    expect(empty.reason).toBeDefined();
  });
});

describe('buildPackingListTool', () => {
  it('builds a seasonal packing list', async () => {
    const result = await runTool<{ items: string[] }>(buildPackingListTool, {
      trailId: 'trail_huihang',
      season: 'summer',
    });
    expect(result.items).toContain('防晒霜');
    expect(result.items).toContain('登山杖');
  });
});
