import { describe, expect, it } from 'vitest';
import { mastra } from '../index.ts';

describe('Mastra advanced registry', () => {
  it('registers the advanced playground modules', () => {
    expect(mastra.listMemory()?.supportMemory.id).toBe('support-memory');
    expect(mastra.listProcessors()).toEqual(
      expect.objectContaining({
        supportSecretRedactionProcessor: expect.any(Object),
        supportTokenLimiterProcessor: expect.any(Object),
      }),
    );
    expect(mastra.listScorers()?.supportReplyQualityScorer.id).toBe(
      'support-reply-quality',
    );
    expect(mastra.listMCPServers()?.supportPolicyMcpServer.id).toBe(
      'support-policy-mcp',
    );
  });

  it('registers the multi-agent travel demo modules', () => {
    expect(mastra.getAgent('travelPlannerAgent').id).toBe('travel-planner-agent');
    expect(mastra.getAgent('transportAgent').id).toBe('transport-agent');
    expect(mastra.getAgent('lodgingAgent').id).toBe('lodging-agent');
    expect(mastra.getWorkflow('itineraryWorkflow').id).toBe('itinerary-workflow');
    expect(mastra.listMemory()?.travelMemory.id).toBe('travel-memory');
  });
});
