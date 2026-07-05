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
});
