import { describe, expect, it } from 'vitest';
import {
  supportInputProcessors,
  supportOutputProcessors,
  supportTokenLimiterProcessor,
} from './processors.ts';

describe('support processors', () => {
  // In streaming mode the token limiter counts every stream event
  // (reasoning/tool-call deltas, metadata) against its cumulative budget and
  // silently drops the rest of the reply once exceeded, so it must stay
  // input-only.
  it('keeps the token limiter input-only', () => {
    expect(supportInputProcessors).toContain(supportTokenLimiterProcessor);
    expect(supportOutputProcessors).not.toContain(supportTokenLimiterProcessor);
  });
});
