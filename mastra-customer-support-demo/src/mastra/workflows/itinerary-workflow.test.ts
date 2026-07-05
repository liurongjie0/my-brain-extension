import { describe, expect, it } from 'vitest';
import { mastra } from '../index.ts';

describe('itineraryWorkflow', () => {
  it('assembles a two-day plan from chosen option IDs', async () => {
    const workflow = mastra.getWorkflow('itineraryWorkflow');
    const run = await workflow.createRun();

    const result = await run.start({
      inputData: {
        trailId: 'trail_moganshan',
        days: 2,
        transportId: 'tr_mgs_rail_am',
        lodgingId: 'lg_mgs_hostel',
        partySize: 2,
      },
    });

    expect(result.status).toBe('success');
    if (result.status !== 'success') {
      throw new Error('Workflow did not succeed');
    }

    expect(result.result.plan.days).toHaveLength(2);
    expect(result.result.plan.totalBudgetCents).toBe(9200 * 2 * 2 + 12000);
    expect(result.result.plan.summary).toContain('莫干山环线');
  });

  it('fails on an unknown transport option', async () => {
    const workflow = mastra.getWorkflow('itineraryWorkflow');
    const run = await workflow.createRun();

    const result = await run.start({
      inputData: {
        trailId: 'trail_moganshan',
        days: 1,
        transportId: 'tr_nope',
        partySize: 1,
      },
    });

    expect(result.status).toBe('failed');
  });
});
