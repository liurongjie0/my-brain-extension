import { createStep, createWorkflow } from '@mastra/core/workflows';
import { z } from 'zod';
import {
  buildItinerary,
  getLodgingOption,
  getTrail,
  getTransportOption,
} from '../../domain/travel.ts';
import {
  itineraryPlanSchema,
  lodgingOptionSchema,
  trailSchema,
  transportOptionSchema,
} from '../travel-schemas.ts';

const workflowInputSchema = z.object({
  trailId: z.string().describe('Trail ID, such as trail_moganshan.'),
  days: z.number().int().positive().describe('Trip length in days.'),
  transportId: z.string().describe('Chosen transport option ID, such as tr_mgs_rail_am.'),
  lodgingId: z
    .string()
    .optional()
    .describe('Chosen lodging option ID; required for overnight trips.'),
  partySize: z.number().int().positive().default(1),
});

const trailOutputSchema = workflowInputSchema.extend({
  trail: trailSchema,
});

const transportBranchSchema = trailOutputSchema.extend({
  transport: transportOptionSchema,
});

const lodgingBranchSchema = z.object({
  lodging: lodgingOptionSchema.optional(),
});

const workflowOutputSchema = z.object({
  plan: itineraryPlanSchema,
});

export const lookupTrailStep = createStep({
  id: 'lookup-trail',
  inputSchema: workflowInputSchema,
  outputSchema: trailOutputSchema,
  execute: async ({ inputData }) => ({
    ...inputData,
    trail: getTrail(inputData.trailId),
  }),
});

// The two option lookups are independent, so they run under .parallel();
// their outputs arrive keyed by step ID in the next step's input.
export const lookupTransportOptionStep = createStep({
  id: 'lookup-transport-option',
  inputSchema: trailOutputSchema,
  outputSchema: transportBranchSchema,
  execute: async ({ inputData }) => ({
    ...inputData,
    transport: getTransportOption(inputData.transportId),
  }),
});

export const lookupLodgingOptionStep = createStep({
  id: 'lookup-lodging-option',
  inputSchema: trailOutputSchema,
  outputSchema: lodgingBranchSchema,
  execute: async ({ inputData }) => ({
    lodging: inputData.lodgingId ? getLodgingOption(inputData.lodgingId) : undefined,
  }),
});

export const assembleItineraryStep = createStep({
  id: 'assemble-itinerary',
  inputSchema: z.object({
    'lookup-transport-option': transportBranchSchema,
    'lookup-lodging-option': lodgingBranchSchema,
  }),
  outputSchema: workflowOutputSchema,
  execute: async ({ inputData }) => {
    const context = inputData['lookup-transport-option'];
    return {
      plan: buildItinerary({
        trailId: context.trailId,
        days: context.days,
        transport: context.transport,
        lodging: inputData['lookup-lodging-option'].lodging,
        partySize: context.partySize,
      }),
    };
  },
});

export const itineraryWorkflow = createWorkflow({
  id: 'itinerary-workflow',
  description:
    'Assemble a deterministic day-by-day hiking itinerary with a budget summary from a chosen trail, transport option, and lodging option.',
  inputSchema: workflowInputSchema,
  outputSchema: workflowOutputSchema,
})
  .then(lookupTrailStep)
  .parallel([lookupTransportOptionStep, lookupLodgingOptionStep])
  .then(assembleItineraryStep)
  .commit();
