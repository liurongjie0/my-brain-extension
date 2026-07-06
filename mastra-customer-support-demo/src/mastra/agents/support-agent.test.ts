import { RequestContext } from '@mastra/core/request-context';
import { describe, expect, it } from 'vitest';
import {
  buildSupportInstructions,
  defaultSupportAgentModel,
  resolveSupportAgentModel,
  supportAgent,
  supportAgentBaseInstructions,
} from './support-agent.ts';

describe('resolveSupportAgentModel', () => {
  it('falls back to the DeepSeek chat model when no override is set', () => {
    expect(resolveSupportAgentModel({})).toBe(defaultSupportAgentModel);
  });

  it('honors the SUPPORT_AGENT_MODEL override', () => {
    expect(
      resolveSupportAgentModel({ SUPPORT_AGENT_MODEL: 'openai/gpt-test' }),
    ).toBe('openai/gpt-test');
  });
});

describe('buildSupportInstructions (dynamic agent)', () => {
  it('returns only the base instructions without request context', () => {
    expect(buildSupportInstructions()).toBe(supportAgentBaseInstructions);
  });

  it('adds the VIP addendum for vip customers', () => {
    const instructions = buildSupportInstructions({ customerTier: 'vip' });
    expect(instructions).toContain(supportAgentBaseInstructions);
    expect(instructions).toContain('VIP customer addendum');
    expect(instructions).toContain('within 2 hours');
  });

  it('does not add the VIP addendum for standard customers', () => {
    expect(buildSupportInstructions({ customerTier: 'standard' })).not.toContain(
      'VIP customer addendum',
    );
  });

  it('appends tone guidance from preferredTone', () => {
    expect(buildSupportInstructions({ preferredTone: 'concise' })).toContain(
      'under 5 sentences',
    );
    expect(buildSupportInstructions({ preferredTone: 'precise' })).toContain(
      'exact policy windows',
    );
  });

  it('resolves per-request instructions through the live agent', async () => {
    const vipContext = new RequestContext();
    vipContext.set('customerTier', 'vip');
    vipContext.set('preferredTone', 'concise');
    const vipInstructions = String(
      await supportAgent.getInstructions({ requestContext: vipContext }),
    );
    expect(vipInstructions).toContain('VIP customer addendum');
    expect(vipInstructions).toContain('under 5 sentences');

    const standardContext = new RequestContext();
    standardContext.set('customerTier', 'standard');
    const standardInstructions = String(
      await supportAgent.getInstructions({ requestContext: standardContext }),
    );
    expect(standardInstructions).not.toContain('VIP customer addendum');
  });
});
