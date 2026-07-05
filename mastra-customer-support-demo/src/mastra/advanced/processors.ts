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

export const supportOutputProcessors = [
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
];

export const supportProcessors = {
  supportSecretRedactionProcessor,
  supportTokenLimiterProcessor,
};
