import {
  RegexFilterProcessor,
  TokenLimiterProcessor,
} from '@mastra/core/processors';

export const supportSecretRedactionProcessor = new RegexFilterProcessor({
  presets: ['secrets'],
  strategy: 'redact',
  phase: 'all',
});

export const supportTokenLimiterProcessor = new TokenLimiterProcessor({
  limit: 4000,
  strategy: 'truncate',
  countMode: 'cumulative',
});

export const supportInputProcessors = [
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
];

// The token limiter stays input-only: in streaming mode its cumulative count
// includes every stream event (reasoning deltas, tool-call deltas, metadata),
// so a 4k budget is exhausted mid-stream and the rest of the reply is
// silently dropped.
export const supportOutputProcessors = [supportSecretRedactionProcessor];

export const supportProcessors = {
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
};
