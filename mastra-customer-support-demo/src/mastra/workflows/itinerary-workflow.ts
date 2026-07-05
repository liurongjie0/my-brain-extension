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

const optionsOutputSchema = workflowInputSchema.extend({
  trail: trailSchema,
  transport: transportOptionSchema,
  lodging: lodgingOptionSchema.optional(),
});

const workflowOutputSchema = z.object({
  plan: itineraryPlanSchema,
});

export const lookupTravelOptionsStep = createStep({
  id: 'lookup-travel-options',
  inputSchema: workflowInputSchema,
  outputSchema: optionsOutputSchema,
  execute: async ({ inputData }) => ({
    ...inputData,
    trail: getTrail(inputData.trailId),
    transport: getTransportOption(inputData.transportId),
    lodging: inputData.lodgingId ? getLodgingOption(inputData.lodgingId) : undefined,
  }),
});

export const assembleItineraryStep = createStep({
  id: 'assemble-itinerary',
  inputSchema: optionsOutputSchema,
  outputSchema: workflowOutputSchema,
  execute: async ({ inputData }) => ({
    plan: buildItinerary({
      trailId: inputData.trailId,
      days: inputData.days,
      transport: inputData.transport,
      lodging: inputData.lodging,
      partySize: inputData.partySize,
    }),
  }),
});

export const itineraryWorkflow = createWorkflow({
  id: 'itinerary-workflow',
  description:
    'Assemble a deterministic day-by-day hiking itinerary with a budget summary from a chosen trail, transport option, and lodging option.',
  inputSchema: workflowInputSchema,
  outputSchema: workflowOutputSchema,
})
  .then(lookupTravelOptionsStep)
  .then(assembleItineraryStep)
  .commit();
