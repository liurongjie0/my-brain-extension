import { createTool } from '@mastra/core/tools';
import { z } from 'zod';
import {
  buildPackingList,
  searchLodging,
  searchTrails,
  searchTransport,
} from '../../domain/travel.ts';
import {
  lodgingSearchResultSchema,
  seasonSchema,
  trailDifficultySchema,
  trailSchema,
  transportOptionSchema,
} from '../travel-schemas.ts';

export const searchTrailsTool = createTool({
  id: 'search-trails',
  description:
    'Search mock hiking trails near Shanghai by keyword, trip length, or difficulty.',
  inputSchema: z.object({
    keyword: z.string().optional().describe('Match against trail name, region, or highlights, e.g. 莫干山.'),
    maxDays: z.number().int().positive().optional(),
    difficulty: trailDifficultySchema.optional(),
  }),
  outputSchema: z.object({
    trails: z.array(trailSchema),
  }),
  execute: async ({ keyword, maxDays, difficulty }) => ({
    trails: searchTrails({ keyword, maxDays, difficulty }),
  }),
});

export const searchTransportTool = createTool({
  id: 'search-transport',
  description:
    'List mock transport options (rail/bus) from a departure city to a trail, sorted by price.',
  inputSchema: z.object({
    from: z.string().describe('Departure city, e.g. 上海 or 杭州.'),
    trailId: z.string().describe('Trail ID, such as trail_moganshan.'),
  }),
  outputSchema: z.object({
    options: z.array(transportOptionSchema),
  }),
  execute: async ({ from, trailId }) => ({
    options: searchTransport(from, trailId),
  }),
});

export const searchLodgingTool = createTool({
  id: 'search-lodging',
  description:
    'List mock lodging near a trail, optionally capped by a per-night budget in CNY cents. Returns a reason when nothing fits the budget.',
  inputSchema: z.object({
    trailId: z.string().describe('Trail ID, such as trail_moganshan.'),
    maxPricePerNightCents: z
      .number()
      .int()
      .positive()
      .optional()
      .describe('Budget per room per night in CNY cents, e.g. 50000 for ¥500.'),
  }),
  outputSchema: lodgingSearchResultSchema,
  execute: async ({ trailId, maxPricePerNightCents }) =>
    searchLodging(trailId, maxPricePerNightCents),
});

export const buildPackingListTool = createTool({
  id: 'build-packing-list',
  description:
    'Build a hiking packing list from trail difficulty and season.',
  inputSchema: z.object({
    trailId: z.string().describe('Trail ID, such as trail_moganshan.'),
    season: seasonSchema.default('autumn'),
  }),
  outputSchema: z.object({
    items: z.array(z.string()),
  }),
  execute: async ({ trailId, season }) => ({
    items: buildPackingList(trailId, season),
  }),
});

export const travelTools = {
  searchTrailsTool,
  searchTransportTool,
  searchLodgingTool,
  buildPackingListTool,
};

// The routing agent keeps only trail search and packing list for itself;
// transport and lodging searches belong to the specialist sub-agents so the
// delegation is visible in the demo.
export const travelPlannerDirectTools = {
  searchTrailsTool,
  buildPackingListTool,
};
