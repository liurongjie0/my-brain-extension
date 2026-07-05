import { describe, expect, it } from 'vitest';
import {
  defaultSupportAgentModel,
  resolveSupportAgentModel,
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
