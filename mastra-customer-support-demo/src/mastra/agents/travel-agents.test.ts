import { describe, expect, it } from 'vitest';
import {
  defaultTravelAgentModel,
  lodgingAgent,
  resolveTravelAgentModel,
  transportAgent,
  travelMemory,
  travelPlannerSubAgents,
  travelPlannerWorkflows,
} from './travel-agents.ts';

describe('resolveTravelAgentModel', () => {
  it('falls back to the DeepSeek chat model when no override is set', () => {
    expect(resolveTravelAgentModel({})).toBe(defaultTravelAgentModel);
  });

  it('honors the TRAVEL_AGENT_MODEL override', () => {
    expect(
      resolveTravelAgentModel({ TRAVEL_AGENT_MODEL: 'openai/gpt-test' }),
    ).toBe('openai/gpt-test');
  });
});

describe('travel planner supervisor wiring', () => {
  it('delegates transport and lodging to described sub-agents', () => {
    expect(Object.keys(travelPlannerSubAgents)).toEqual([
      'transportAgent',
      'lodgingAgent',
    ]);
    // The routing agent picks sub-agents by their descriptions, so an empty
    // description would silently break delegation.
    expect(transportAgent.getDescription()).toBeTruthy();
    expect(lodgingAgent.getDescription()).toBeTruthy();
  });

  it('exposes the itinerary workflow to the routing agent', () => {
    expect(Object.keys(travelPlannerWorkflows)).toEqual(['itineraryWorkflow']);
  });

  it('tracks multi-turn planning state in travel memory', () => {
    expect(travelMemory.id).toBe('travel-memory');
  });
});
