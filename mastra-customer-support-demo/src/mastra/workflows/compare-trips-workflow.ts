import { createStep, createWorkflow } from '@mastra/core/workflows';
import { z } from 'zod';
import {
  formatYuan,
  searchLodging,
  searchTrails,
  searchTransport,
} from '../../domain/travel.ts';

// Advanced control-flow demo: foreach (fan out per candidate trail with
// bounded concurrency) → rank → branch (within budget vs. over budget) →
// merge into one report shape.

const workflowInputSchema = z.object({
  from: z.string().describe('Departure city, e.g. 上海.'),
  maxDays: z.number().int().positive().describe('Longest acceptable trip in days.'),
  partySize: z.number().int().positive().default(1),
  totalBudgetCents: z
    .number()
    .int()
    .positive()
    .describe('Total trip budget for the whole party, in CNY cents.'),
});

const candidateSchema = workflowInputSchema.extend({
  trailId: z.string(),
  trailName: z.string(),
  days: z.number().int().positive(),
});

const costedTripSchema = z.object({
  trailId: z.string(),
  trailName: z.string(),
  days: z.number().int().positive(),
  available: z.boolean(),
  reason: z.string().optional(),
  transportId: z.string().optional(),
  lodgingId: z.string().optional(),
  totalCents: z.number().int().nonnegative(),
  totalBudgetCents: z.number().int().positive(),
  partySize: z.number().int().positive(),
});

const rankingSchema = z.object({
  ranked: z.array(costedTripSchema),
  withinBudget: z.boolean(),
  totalBudgetCents: z.number().int().positive(),
});

const reportSchema = z.object({
  status: z.enum(['recommended', 'over-budget']),
  summary: z.string(),
  ranked: z.array(costedTripSchema),
});

export const listCandidateTripsStep = createStep({
  id: 'list-candidate-trips',
  inputSchema: workflowInputSchema,
  outputSchema: z.array(candidateSchema),
  execute: async ({ inputData }) =>
    searchTrails({ maxDays: inputData.maxDays }).map((trail) => ({
      ...inputData,
      trailId: trail.id,
      trailName: trail.name,
      days: trail.typicalDays,
    })),
});

export const costTripStep = createStep({
  id: 'cost-trip',
  inputSchema: candidateSchema,
  outputSchema: costedTripSchema,
  execute: async ({ inputData }) => {
    const base = {
      trailId: inputData.trailId,
      trailName: inputData.trailName,
      days: inputData.days,
      totalBudgetCents: inputData.totalBudgetCents,
      partySize: inputData.partySize,
    };

    const transportOptions = searchTransport(inputData.from, inputData.trailId);
    if (transportOptions.length === 0) {
      return {
        ...base,
        available: false,
        reason: `No transport from ${inputData.from}.`,
        totalCents: 0,
      };
    }
    const transport = transportOptions[0];

    const nights = inputData.days - 1;
    let lodgingCents = 0;
    let lodgingId: string | undefined;
    if (nights > 0) {
      const lodging = searchLodging(inputData.trailId);
      if (lodging.options.length === 0) {
        return {
          ...base,
          available: false,
          reason: 'No lodging available.',
          totalCents: 0,
        };
      }
      lodgingId = lodging.options[0].id;
      lodgingCents = lodging.options[0].pricePerNightCents * nights;
    }

    return {
      ...base,
      available: true,
      transportId: transport.id,
      lodgingId,
      totalCents: transport.priceCents * 2 * inputData.partySize + lodgingCents,
    };
  },
});

export const rankTripsStep = createStep({
  id: 'rank-trips',
  inputSchema: z.array(costedTripSchema),
  outputSchema: rankingSchema,
  execute: async ({ inputData }) => {
    const ranked = inputData
      .filter((trip) => trip.available)
      .sort((a, b) => a.totalCents - b.totalCents);
    if (ranked.length === 0) {
      throw new Error('No reachable trips to rank.');
    }

    const totalBudgetCents = ranked[0].totalBudgetCents;
    return {
      ranked,
      withinBudget: ranked[0].totalCents <= totalBudgetCents,
      totalBudgetCents,
    };
  },
});

export const recommendTripStep = createStep({
  id: 'recommend-trip',
  inputSchema: rankingSchema,
  outputSchema: reportSchema,
  execute: async ({ inputData }) => {
    const best = inputData.ranked[0];
    return {
      status: 'recommended' as const,
      summary: `推荐${best.trailName}（${best.days} 天）：${best.partySize} 人合计约 ${formatYuan(best.totalCents)}，在总预算 ${formatYuan(inputData.totalBudgetCents)} 之内。`,
      ranked: inputData.ranked,
    };
  },
});

export const suggestAdjustmentsStep = createStep({
  id: 'suggest-adjustments',
  inputSchema: rankingSchema,
  outputSchema: reportSchema,
  execute: async ({ inputData }) => {
    const cheapest = inputData.ranked[0];
    const gap = cheapest.totalCents - inputData.totalBudgetCents;
    return {
      status: 'over-budget' as const,
      summary: `预算不足：最便宜的${cheapest.trailName}也需约 ${formatYuan(cheapest.totalCents)}，超出总预算 ${formatYuan(gap)}。可考虑提高预算、减少人数或改走一日线路。`,
      ranked: inputData.ranked,
    };
  },
});

const branchOutputSchema = z.object({
  'recommend-trip': reportSchema.optional(),
  'suggest-adjustments': reportSchema.optional(),
});

export const mergeComparisonReportStep = createStep({
  id: 'merge-comparison-report',
  inputSchema: branchOutputSchema,
  outputSchema: reportSchema,
  execute: async ({ inputData }) => {
    const report = inputData['recommend-trip'] ?? inputData['suggest-adjustments'];
    if (!report) {
      throw new Error('Branch produced no comparison report.');
    }
    return report;
  },
});

export const compareTripsWorkflow = createWorkflow({
  id: 'compare-trips-workflow',
  description:
    'Compare all candidate trails from a departure city by total cost (fan-out per trail), then either recommend the best trip within budget or explain the gap.',
  inputSchema: workflowInputSchema,
  outputSchema: reportSchema,
})
  .then(listCandidateTripsStep)
  .foreach(costTripStep, { concurrency: 3 })
  .then(rankTripsStep)
  .branch([
    [async ({ inputData }) => inputData.withinBudget, recommendTripStep],
    [async ({ inputData }) => !inputData.withinBudget, suggestAdjustmentsStep],
  ])
  .then(mergeComparisonReportStep)
  .commit();
